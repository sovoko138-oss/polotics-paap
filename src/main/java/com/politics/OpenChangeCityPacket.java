package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class OpenChangeCityPacket {
    public final UUID countryId;
    public final String cityName, mayor, flag;

    public OpenChangeCityPacket(UUID countryId, String cityName, String mayor, String flag) {
        this.countryId = countryId; this.cityName = cityName; this.mayor = mayor; this.flag = flag;
    }

    public static void encode(OpenChangeCityPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.countryId);
        buf.writeUtf(m.cityName, 100);
        buf.writeUtf(m.mayor, 100);
        buf.writeUtf(m.flag, 100);
    }

    public static OpenChangeCityPacket decode(FriendlyByteBuf buf) {
        return new OpenChangeCityPacket(buf.readUUID(), buf.readUtf(100), buf.readUtf(100), buf.readUtf(100));
    }

    public static void handle(OpenChangeCityPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.openChangeCity(m));
    }
}
