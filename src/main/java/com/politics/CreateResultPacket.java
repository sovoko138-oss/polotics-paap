package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CreateResultPacket {
    public final boolean ok;
    public final String message;

    public CreateResultPacket(boolean ok, String message) { this.ok = ok; this.message = message; }

    public static void encode(CreateResultPacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.ok);
        buf.writeUtf(m.message, 300);
    }

    public static CreateResultPacket decode(FriendlyByteBuf buf) {
        return new CreateResultPacket(buf.readBoolean(), buf.readUtf(300));
    }

    public static void handle(CreateResultPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.onCreateResult(m.ok, m.message));
    }
}
