package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Tells a president's client which chunks their country owns (or that they have no country). */
public class BorderSyncPacket {
    public final boolean active;
    public final String dimension;
    public final int color;
    public final long[] chunks;

    public BorderSyncPacket(boolean active, String dimension, int color, long[] chunks) {
        this.active = active; this.dimension = dimension; this.color = color; this.chunks = chunks;
    }

    public static void encode(BorderSyncPacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.active);
        buf.writeUtf(m.dimension, 100);
        buf.writeInt(m.color);
        buf.writeLongArray(m.chunks);
    }

    public static BorderSyncPacket decode(FriendlyByteBuf buf) {
        return new BorderSyncPacket(buf.readBoolean(), buf.readUtf(100), buf.readInt(), buf.readLongArray());
    }

    public static void handle(BorderSyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.setBorder(m));
    }
}
