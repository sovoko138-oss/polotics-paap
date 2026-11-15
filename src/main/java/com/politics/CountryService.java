package com.politics;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
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
                ? new BorderSyncPacket(false, "", 0, 0, 0)
                : new BorderSyncPacket(true, c.dimension, c.capitalPos.getX() >> 4, c.capitalPos.getZ() >> 4, c.color);
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pk);
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
        data.add(c, level.getRandom());
        be.setUsed(true);
        syncBorder(p);

        Component msg = Component.literal("Основана страна ")
                .append(Component.literal(name).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(c.color))))
                .append(Component.literal(". Столица: " + capital + ", президент: " + presidentName + "."));
        p.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        return null;
    }
}
