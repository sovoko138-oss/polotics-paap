package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** DEBUG: all countries' territories, drawn while a wooden hoe is held. To be removed later. */
public class AllBordersPacket {
    public static class Entry {
        public final String dimension;
        public final int color;
        public final long[] chunks;
        public Entry(String dimension, int color, long[] chunks) { this.dimension = dimension; this.color = color; this.chunks = chunks; }
    }

    public final List<Entry> entries;

    public AllBordersPacket(List<Entry> entries) { this.entries = entries; }

    public static void encode(AllBordersPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entries.size());
        for (Entry e : m.entries) {
            buf.writeUtf(e.dimension, 100);
            buf.writeInt(e.color);
            buf.writeLongArray(e.chunks);
        }
    }

    public static AllBordersPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(new Entry(buf.readUtf(100), buf.readInt(), buf.readLongArray()));
        return new AllBordersPacket(list);
    }

    public static void handle(AllBordersPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.setAllBorders(m));
    }
}
