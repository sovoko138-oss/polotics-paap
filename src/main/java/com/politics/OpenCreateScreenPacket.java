package com.politics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenCreateScreenPacket {
    public final BlockPos pos;

    public OpenCreateScreenPacket(BlockPos pos) { this.pos = pos; }

    public static void encode(OpenCreateScreenPacket m, FriendlyByteBuf buf) { buf.writeBlockPos(m.pos); }

    public static OpenCreateScreenPacket decode(FriendlyByteBuf buf) { return new OpenCreateScreenPacket(buf.readBlockPos()); }

    public static void handle(OpenCreateScreenPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.openCreateScreen(m.pos));
    }
}
