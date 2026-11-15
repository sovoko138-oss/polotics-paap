package com.politics;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ResidentService {
    private static final Pattern NICK = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    public static boolean isOnline(MinecraftServer server, String name) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.getGameProfile().getName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** The country whose land the player stands on, otherwise the country they belong to. */
    public static CountryData.Country targetCountryFor(ServerPlayer p, CountryData data) {
        String dim = p.level().dimension().location().toString();
        ChunkPos cp = p.chunkPosition();
        CountryData.Country here = data.byChunk(dim, cp.x, cp.z);
        if (here != null) return here;
        String name = p.getGameProfile().getName();
        for (CountryData.Country c : data.all()) if (c.isMember(name)) return c;
        return null;
    }

    /** Anyone who is in the country (on its land, or a member of it) can look at the list. */
    public static boolean canView(ServerPlayer p, CountryData.Country c, CountryData data) {
        String dim = p.level().dimension().location().toString();
        ChunkPos cp = p.chunkPosition();
        if (data.byChunk(dim, cp.x, cp.z) == c) return true;
        return c.isMember(p.getGameProfile().getName());
    }

    public static int viewerRank(ServerPlayer p, CountryData.Country c) {
        if (p.getUUID().equals(c.presidentId)) return 3;
        return c.rankOf(p.getGameProfile().getName());
    }

    /** The country in which the player is president or a deputy, or null. */
    public static CountryData.Country leaderCountry(ServerPlayer p, CountryData data) {
        for (CountryData.Country c : data.all()) if (viewerRank(p, c) >= 2) return c;
        return null;
    }

    public static boolean validNick(String nick) { return NICK.matcher(nick).matches(); }

    public static String cityName(CountryData.Country c, String cityId) {
        return c.capitalName; // only the capital exists for now
    }

    public static String displayTitle(CountryData.Country c, CountryData.Resident r) {
        if (!r.title.isEmpty()) return r.title;
        if (c.isPresident(r.name)) return "Президент";
        if (c.isDeputy(r.name)) return "Зам. Президента";
        if (c.isMayor(r.name)) return "Мэр " + cityName(c, r.cityId);
        return "";
    }

    public static PlayerListPacket build(ServerPlayer viewer, CountryData.Country c, boolean open) {
        int vr = viewerRank(viewer, c);
        boolean manage = vr >= 2;

        List<CountryData.Resident> sorted = new ArrayList<>(c.residents);
        sorted.sort(Comparator.<CountryData.Resident>comparingInt(r -> -c.rankOf(r.name))
                .thenComparing(r -> r.name.toLowerCase()));

        Map<String, PlayerListPacket.Group> groups = new LinkedHashMap<>();
        for (CountryData.Resident r : sorted) {
            PlayerListPacket.Group g = groups.computeIfAbsent(r.cityId, id -> {
                PlayerListPacket.Group ng = new PlayerListPacket.Group();
                ng.city = cityName(c, id);
                return ng;
            });
            PlayerListPacket.Entry e = new PlayerListPacket.Entry();
            e.name = r.name;
            e.online = isOnline(viewer.getServer(), r.name);
            e.title = displayTitle(c, r);
            e.rawTitle = r.title;
            e.rank = c.rankOf(r.name);
            boolean lower = e.rank < vr;
            e.canKick = manage && lower;
            e.canTitle = manage && lower;
            g.entries.add(e);
        }
        return new PlayerListPacket(c.id, c.name, c.color, open, new ArrayList<>(groups.values()));
    }

    /** Returns an error message, or null if the resident was removed from the country. */
    public static String kick(ServerPlayer p, KickPlayerPacket m) {
        CountryData data = CountryData.get(p.getServer());
        CountryData.Country c = data.byId(m.countryId);
        if (c == null) return "Страна не найдена.";
        int vr = viewerRank(p, c);
        if (vr < 2) return "Исключать игроков могут только президент и заместители.";
        CountryData.Resident r = c.findResident(m.name);
        if (r == null) return "Такого жителя нет.";
        if (c.rankOf(r.name) >= vr) return "Нельзя исключить игрока вашего или более высокого ранга.";

        String name = r.name;
        c.residents.remove(r);
        c.deputies.removeIf(d -> d.equalsIgnoreCase(name));
        if (c.isMayor(name)) c.mayor = c.presidentName;
        data.setDirty();

        for (ServerPlayer sp : p.getServer().getPlayerList().getPlayers()) {
            if (sp.getGameProfile().getName().equalsIgnoreCase(name)) {
                sp.displayClientMessage(Component.literal("Вас исключили из страны «" + c.name + "»."), false);
            }
        }
        return null;
    }

    /** Returns an error message, or null if the title was changed. An empty title removes it. */
    public static String setTitle(ServerPlayer p, SetTitlePacket m) {
        CountryData data = CountryData.get(p.getServer());
        CountryData.Country c = data.byId(m.countryId);
        if (c == null) return "Страна не найдена.";
        int vr = viewerRank(p, c);
        if (vr < 2) return "Выдавать звания могут только президент и заместители.";
        CountryData.Resident r = c.findResident(m.name);
        if (r == null) return "Такого жителя нет.";
        if (c.rankOf(r.name) >= vr) return "Нельзя менять звание игрока вашего или более высокого ранга.";

        String title = m.title.trim();
        if (title.length() > 24) return "Звание: не больше 24 символов.";
        if (title.indexOf('§') >= 0) return "Недопустимый символ в звании.";
        r.title = title;
        data.setDirty();
        return null;
    }

    /** Test helper for operators: adds a resident to the capital. */
    public static String addResident(CountryData data, CountryData.Country c, String nick) {
        if (!NICK.matcher(nick).matches()) return "Некорректный ник.";
        if (c.findResident(nick) != null) return "Этот игрок уже житель страны.";
        c.addResident(nick);
        data.setDirty();
        return null;
    }
}
