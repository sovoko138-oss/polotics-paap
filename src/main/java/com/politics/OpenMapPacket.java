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
        /** The alliances of the country: names ("A, B"), the id and the colour of the first one (colour -1 if none). */
        public final String alliances;
        public final java.util.UUID allianceId;
        public final int allianceColor;
        public Entry(String name, String president, String dimension, int color, long[] chunks,
                     String alliances, java.util.UUID allianceId, int allianceColor) {
            this.name = name; this.president = president; this.dimension = dimension; this.color = color; this.chunks = chunks;
            this.alliances = alliances; this.allianceId = allianceId; this.allianceColor = allianceColor;
        }
    }

    public final List<Entry> countries;
    /** Chunks captured in wars that are going on; the map draws them gray. */
    public final long[] captured;

    /** Pairs of country names that are at war with each other. */
    public final List<String[]> wars;

    public OpenMapPacket(List<Entry> countries, long[] captured, List<String[]> wars) {
        this.countries = countries; this.captured = captured; this.wars = wars;
    }

    public static void encode(OpenMapPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.countries.size());
        for (Entry e : m.countries) {
            buf.writeUtf(e.name, 100);
            buf.writeUtf(e.president, 100);
            buf.writeUtf(e.dimension, 100);
            buf.writeInt(e.color);
            buf.writeLongArray(e.chunks);
            buf.writeUtf(e.alliances, 300);
            buf.writeUUID(e.allianceId);
            buf.writeInt(e.allianceColor);
        }
        buf.writeLongArray(m.captured);
        buf.writeVarInt(m.wars.size());
        for (String[] pr : m.wars) { buf.writeUtf(pr[0], 100); buf.writeUtf(pr[1], 100); }
    }

    public static OpenMapPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readUtf(100), buf.readUtf(100), buf.readUtf(100), buf.readInt(), buf.readLongArray(),
                    buf.readUtf(300), buf.readUUID(), buf.readInt()));
        }
        long[] captured = buf.readLongArray();
        int wn = buf.readVarInt();
        List<String[]> wars = new ArrayList<>();
        for (int i = 0; i < wn; i++) wars.add(new String[]{buf.readUtf(100), buf.readUtf(100)});
        return new OpenMapPacket(list, captured, wars);
    }

    public static void handle(OpenMapPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.openMap(m));
    }
}
