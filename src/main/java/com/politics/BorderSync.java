package com.politics;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Keeps clients up to date: own border for presidents, and (debug) all borders for the wooden hoe. */
public class BorderSync {
    static long last = Long.MIN_VALUE;

    /** Called every second: sends the borders again only if some territory changed. */
    public static void tick(MinecraftServer server, CountryData data) {
        long h = 1;
        for (CountryData.Country c : data.all()) {
            h = 31 * h + c.id.hashCode();
            h = 31 * h + c.chunks.hashCode();
            h = 31 * h + c.color;
            h = 31 * h + c.dimension.hashCode();
        }
        if (h == last) return;
        last = h;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            CountryService.syncBorder(sp);
            sendAll(sp, data);
        }
    }

    public static void sendAll(ServerPlayer sp, CountryData data) {
        List<AllBordersPacket.Entry> list = new ArrayList<>();
        for (CountryData.Country c : data.all()) {
            list.add(new AllBordersPacket.Entry(c.dimension, c.color,
                    c.chunks.stream().mapToLong(Long::longValue).toArray()));
        }
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new AllBordersPacket(list));
    }
}
