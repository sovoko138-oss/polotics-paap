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
        data.claim(c, cp.x, cp.z);
        syncBorder(p);
        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1f);
        p.displayClientMessage(Component.literal("Территория захвачена. Чанков в стране: " + c.chunks.size()), true);
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
        if (data.byPresident(p.getUUID()) != null) return "Вы уже президент другой страны.";

        int count = playersAround(level, m.pos);
        int required = Config.MIN_PLAYERS.get();
        if (count < required) return "Нужно игроков вокруг блока: " + required + ", сейчас: " + count + ".";

        String name = m.name.trim();
        String capital = m.capital.trim();
        if (name.length() < 3 || name.length() > 32) return "Название страны: от 3 до 32 символов.";
        if (capital.length() < 2 || capital.length() > 32) return "Название столицы: от 2 до 32 символов.";
        if (name.indexOf('§') >= 0 || capital.indexOf('§') >= 0) return "Недопустимый символ в названии.";
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
        data.add(c, level.getRandom());
        be.setUsed(true);
        syncBorder(p);

        Component msg = Component.literal("Основана страна ")
                .append(Component.literal(name).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(c.color))))
                .append(Component.literal(". Столица: " + capital + ", президент: " + presidentName + "."));
        p.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        return null;
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
