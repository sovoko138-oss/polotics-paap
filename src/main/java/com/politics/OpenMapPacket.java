package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The countries for the /map window (the terrain itself is remembered by the client). */
public class OpenMapPacket {
    public static class Entry {
        public final String name, president, dimension;
        public final int color;
        public final long[] chunks;
        public Entry(String name, String president, String dimension, int color, long[] chunks) {
            this.name = name; this.president = president; this.dimension = dimension; this.color = color; this.chunks = chunks;
        }
    }

    public final List<Entry> countries;
    /** Chunks captured in wars that are going on; the map draws them gray. */
    public final long[] captured;

    public OpenMapPacket(List<Entry> countries, long[] captured) { this.countries = countries; this.captured = captured; }

    public static void encode(OpenMapPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.countries.size());
        for (Entry e : m.countries) {
            buf.writeUtf(e.name, 100);
            buf.writeUtf(e.president, 100);
            buf.writeUtf(e.dimension, 100);
            buf.writeInt(e.color);
            buf.writeLongArray(e.chunks);
        }
        buf.writeLongArray(m.captured);
    }

    public static OpenMapPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readUtf(100), buf.readUtf(100), buf.readUtf(100), buf.readInt(), buf.readLongArray()));
        }
        return new OpenMapPacket(list, buf.readLongArray());
    }

    public static void handle(OpenMapPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.openMap(m));
    }
}
