package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class OpenMailPacket {
    public final UUID countryId;
    public final String countryName, presidentName;
    public final int color;

    public OpenMailPacket(UUID countryId, String countryName, String presidentName, int color) {
        this.countryId = countryId; this.countryName = countryName; this.presidentName = presidentName; this.color = color;
    }

    public static void encode(OpenMailPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.countryId);
        buf.writeUtf(m.countryName, 100);
        buf.writeUtf(m.presidentName, 100);
        buf.writeInt(m.color);
    }

    public static OpenMailPacket decode(FriendlyByteBuf buf) {
        return new OpenMailPacket(buf.readUUID(), buf.readUtf(100), buf.readUtf(100), buf.readInt());
    }

    public static void handle(OpenMailPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.openMail(m));
    }
}
