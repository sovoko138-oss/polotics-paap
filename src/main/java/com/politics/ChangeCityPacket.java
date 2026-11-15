package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

public class ChangeCityPacket {
    public final UUID countryId;
    public final String cityName, mayor, flag;

    public ChangeCityPacket(UUID countryId, String cityName, String mayor, String flag) {
        this.countryId = countryId; this.cityName = cityName; this.mayor = mayor; this.flag = flag;
    }

    public static void encode(ChangeCityPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.countryId);
        buf.writeUtf(m.cityName, 100);
        buf.writeUtf(m.mayor, 100);
        buf.writeUtf(m.flag, 100);
    }

    public static ChangeCityPacket decode(FriendlyByteBuf buf) {
        return new ChangeCityPacket(buf.readUUID(), buf.readUtf(100), buf.readUtf(100), buf.readUtf(100));
    }

    public static void handle(ChangeCityPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender == null) return;
        String error = CountryService.tryChangeCity(sender, m);
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                new CreateResultPacket(error == null, error == null ? "Город обновлён!" : error));
    }
}
