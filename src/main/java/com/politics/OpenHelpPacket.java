package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Opens the server guide on the client. */
public class OpenHelpPacket {
    public static void encode(OpenHelpPacket m, FriendlyByteBuf buf) {}
    public static OpenHelpPacket decode(FriendlyByteBuf buf) { return new OpenHelpPacket(); }
    public static void handle(OpenHelpPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.openHelp());
    }
}
