package com.politics;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class CountryService {
    private static final Pattern NICK = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    /** Players in a 5x5 area around the block, any height. */
    public static int playersAround(Level level, BlockPos pos) {
        AABB area = new AABB(pos.getX() - 2, level.getMinBuildHeight(), pos.getZ() - 2,
                pos.getX() + 3, level.getMaxBuildHeight(), pos.getZ() + 3);
        return level.getEntitiesOfClass(Player.class, area, p -> !p.isSpectator()).size();
    }

    /** Sends the player their own country's border (or clears it if they are not a president). */
    public static void syncBorder(ServerPlayer p) {
        CountryData.Country c = CountryData.get(p.getServer()).byPresident(p.getUUID());
        BorderSyncPacket pk = c == null
                ? new BorderSyncPacket(false, "", 0, new long[0])
                : new BorderSyncPacket(true, c.dimension, c.color,
                        c.chunks.stream().mapToLong(Long::longValue).toArray());
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pk);
    }

    /** Wooden hoe right click: the president claims the chunk they are standing in. */
    public static void tryClaim(ServerPlayer p) {
        CountryData data = CountryData.get(p.getServer());
        CountryData.Country c = data.byPresident(p.getUUID());
        if (c == null) return;
        ServerLevel level = p.serverLevel();
        String dim = level.dimension().location().toString();
        if (!dim.equals(c.dimension)) {
            p.displayClientMessage(Component.literal("Захватывать территорию можно только в измерении вашей страны."), true);
            return;
        }
        net.minecraft.world.level.ChunkPos cp = p.chunkPosition();
        CountryData.Country owner = data.byChunk(dim, cp.x, cp.z);
        if (owner != null) {
            p.displayClientMessage(Component.literal(owner == c
                    ? "Этот чанк уже принадлежит вашей стране."
                    : "Этот чанк уже принадлежит стране «" + owner.name + "»."), true);
            return;
        }
        if (!data.touches(c, cp.x, cp.z)) {
            p.displayClientMessage(Component.literal("Новый чанк должен граничить с территорией вашей страны."), true);
            return;
        }
        int chunkLimit = maxChunks(c);
        if (c.chunks.size() >= chunkLimit) {
            p.displayClientMessage(Component.literal("Ваша страна достигла лимита территории: "
                    + c.chunks.size() + "/" + chunkLimit + " чанков. Привлекайте новых жителей, чтобы увеличить лимит."), true);
            return;
        }
        data.claim(c, cp.x, cp.z);
        syncBorder(p);
        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1f);
        p.displayClientMessage(Component.literal("Территория захвачена. Чанков в стране: " + c.chunks.size()), true);
    }


    /** Territory allowance based on country population. The initial population is treated as 4. */
    public static int maxChunks(CountryData.Country c) {
        int players = Math.max(4, c.residents.size());
        int limit = 25;

        // Players 5-7: +10 chunks each (maximum 55).
        int tier1 = Math.min(Math.max(players - 4, 0), 3);
        limit += tier1 * 10;

        // Players 8-15: +5 chunks each (maximum 95).
        int tier2 = Math.min(Math.max(players - 7, 0), 8);
        limit += tier2 * 5;

        // Players 16-20: +3 chunks each (maximum 110).
        int tier3 = Math.min(Math.max(players - 15, 0), 5);
        limit += tier3 * 3;

        // Players 21+: +1 chunk each, up to the hard cap of 125.
        int tier4 = Math.max(players - 20, 0);
        limit += tier4;

        return Math.min(125, limit);
    }

    /** Returns an error message, or null if the country was created. */
    public static String tryCreate(ServerPlayer p, CreateCountryPacket m) {
        ServerLevel level = p.serverLevel();
        if (!level.isLoaded(m.pos)) return "Блок столицы не найден.";
        if (p.distanceToSqr(Vec3.atCenterOf(m.pos)) > 64) return "Вы слишком далеко от блока столицы.";
        if (!(level.getBlockEntity(m.pos) instanceof CapitalBlockEntity be)) return "Блок столицы не найден.";
        if (!p.getUUID().equals(be.getOwnerId())) return "Только поставивший блок игрок может создать страну.";
        if (be.isUsed()) return "Из этого блока страна уже создана.";

        CountryData data = CountryData.get(p.getServer());
        if (data.countryLimit() > 0 && data.all().size() >= data.countryLimit()) return "Достигнут лимит стран на сервере (" + data.countryLimit() + ").";
        if (data.byPresident(p.getUUID()) != null) return "Вы уже президент другой страны.";

        int count = playersAround(level, m.pos);
        int required = Config.MIN_PLAYERS.get();
        if (count < required) return "Нужно игроков вокруг блока: " + required + ", сейчас: " + count + ".";

        String name = m.name.trim();
        String capital = m.capital.trim();
        if (name.length() < 3 || name.length() > 32) return "Название страны: от 3 до 32 символов.";
        if (capital.length() < 2 || capital.length() > 32) return "Название столицы: от 2 до 32 символов.";
        if (name.indexOf('§') >= 0 || capital.indexOf('§') >= 0) return "Недопустимый символ в названии.";
        if (name.equalsIgnoreCase(PigProtocol.NAME)) return "Это название принадлежит свиньям.";
        if (data.nameTaken(name)) return "Страна с таким названием уже существует.";

        ResourceLocation flagId = ResourceLocation.tryParse(m.flag);
        if (flagId == null || !ForgeRegistries.ITEMS.containsKey(flagId)) return "Неверный флаг.";
        Item flagItem = ForgeRegistries.ITEMS.getValue(flagId);
        if (flagItem == null || flagItem == Items.AIR) return "Неверный флаг.";

        String presidentName = p.getGameProfile().getName();
        List<String> deputies = new ArrayList<>();
        for (String raw : m.deputies.split("[,;\\s]+")) {
            String nick = raw.trim();
            if (nick.isEmpty()) continue;
            if (!NICK.matcher(nick).matches()) return "Некорректный ник заместителя: " + nick;
            if (nick.equalsIgnoreCase(presidentName)) continue;
            boolean dup = false;
            for (String d : deputies) if (d.equalsIgnoreCase(nick)) dup = true;
            if (!dup) deputies.add(nick);
        }
        if (deputies.size() > 10) return "Заместителей не больше 10.";

        CountryData.Country c = new CountryData.Country();
        c.name = name;
        c.capitalName = capital;
        c.presidentId = p.getUUID();
        c.presidentName = presidentName;
        c.deputies = deputies;
        c.flagItem = flagId.toString();
        c.dimension = level.dimension().location().toString();
        c.capitalPos = m.pos;
        c.mayor = presidentName;
        c.cityFlag = flagId.toString();
        c.ensureCore();
        data.add(c, level.getRandom());
        // the colour chosen in the window (each channel 1..255)
        int cr = Math.max(1, Math.min(255, m.color >> 16 & 0xFF));
        int cg = Math.max(1, Math.min(255, m.color >> 8 & 0xFF));
        int cb = Math.max(1, Math.min(255, m.color & 0xFF));
        c.color = (cr << 16) | (cg << 8) | cb;
        c.hue = java.awt.Color.RGBtoHSB(cr, cg, cb, null)[0];
        data.setDirty();
        be.setUsed(true);
        syncBorder(p);

        announceNewCountry(p.getServer(), c);
        MailService.welcomeFromPigs(p.getServer(), c);
        JoinService.addDemoRequests(p.getServer(), c);
        return null;
    }

    /** Chat message for everybody, in the colour of the new country. */
    public static void announceNewCountry(net.minecraft.server.MinecraftServer server, CountryData.Country c) {
        Component msg = Component.literal("Появилась новая страна - " + c.name + "! с президентом " + c.presidentName)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(c.color)));
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    public static boolean canEditCity(ServerPlayer p, CountryData.Country c) {
        return p.getUUID().equals(c.presidentId) || p.getGameProfile().getName().equalsIgnoreCase(c.mayor);
    }

    /** Returns an error message, or null if the city was changed. */
    public static String tryChangeCity(ServerPlayer p, ChangeCityPacket m) {
        CountryData data = CountryData.get(p.getServer());
        CountryData.Country c = data.byId(m.countryId);
        if (c == null) return "Страна не найдена.";
        if (!canEditCity(p, c)) return "Изменять город может только президент или мэр.";

        String name = m.cityName.trim();
        String mayor = m.mayor.trim();
        if (name.length() < 2 || name.length() > 32) return "Название города: от 2 до 32 символов.";
        if (name.indexOf('§') >= 0) return "Недопустимый символ в названии.";
        if (!NICK.matcher(mayor).matches()) return "Некорректный ник мэра.";
        ResourceLocation flagId = ResourceLocation.tryParse(m.flag);
        if (flagId == null || !ForgeRegistries.ITEMS.containsKey(flagId)) return "Неверный флаг.";
        Item flagItem = ForgeRegistries.ITEMS.getValue(flagId);
        if (flagItem == null || flagItem == Items.AIR) return "Неверный флаг.";

        c.capitalName = name;
        c.mayor = mayor;
        c.ensureCore();
        c.cityFlag = flagId.toString();
        data.setDirty();
        return null;
    }

    public static List<CountriesPacket.Info> buildInfos(CountryData data) {
        List<CountriesPacket.Info> list = new ArrayList<>();
        for (CountryData.Country c : data.all()) {
            CountriesPacket.Info i = new CountriesPacket.Info();
            i.name = c.name;
            i.color = c.color;
            i.flag = c.flagItem;
            i.president = c.presidentName;
            i.deputies = new ArrayList<>(c.deputies);
            i.cities.add(c.capitalName + " (столица, мэр: " + c.mayor + ")");
            i.chunks = c.chunks.size();
            List<CountryData.Alliance> als = data.alliancesOf(c);
            if (!als.isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (CountryData.Alliance al : als) { if (names.length() > 0) names.append(", "); names.append(al.name); }
                i.alliance = names.toString();
                i.allianceColor = als.get(0).color;
            }
            list.add(i);
        }
        return list;
    }

    /** An item name with the item shown on hover. */
    public static Component flagComponent(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) return Component.literal(id);
        ItemStack st = new ItemStack(item);
        return item.getDescription().copy().withStyle(s ->
                s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(st))));
    }
}
