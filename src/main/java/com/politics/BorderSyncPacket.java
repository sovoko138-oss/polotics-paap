package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Tells a president's client where their country's border is (or that they have none). */
public class BorderSyncPacket {
    public final boolean active;
    public final String dimension;
    public final int chunkX, chunkZ, color;

    public BorderSyncPacket(boolean active, String dimension, int chunkX, int chunkZ, int color) {
        this.active = active; this.dimension = dimension; this.chunkX = chunkX; this.chunkZ = chunkZ; this.color = color;
    }

    public static void encode(BorderSyncPacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.active);
        buf.writeUtf(m.dimension, 100);
        buf.writeInt(m.chunkX);
        buf.writeInt(m.chunkZ);
        buf.writeInt(m.color);
    }

    public static BorderSyncPacket decode(FriendlyByteBuf buf) {
        return new BorderSyncPacket(buf.readBoolean(), buf.readUtf(100), buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(BorderSyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.setBorder(m));
    }
}
