package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

public class SetTitlePacket {
    public final UUID countryId;
    public final String name, title;

    public SetTitlePacket(UUID countryId, String name, String title) {
        this.countryId = countryId; this.name = name; this.title = title;
    }

    public static void encode(SetTitlePacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.countryId);
        buf.writeUtf(m.name, 64);
        buf.writeUtf(m.title, 100);
    }

    public static SetTitlePacket decode(FriendlyByteBuf buf) {
        return new SetTitlePacket(buf.readUUID(), buf.readUtf(64), buf.readUtf(100));
    }

    public static void handle(SetTitlePacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender == null) return;
        String error = ResidentService.setTitle(sender, m);
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                new CreateResultPacket(error == null, error == null ? "Звание обновлено." : error));
        CountryData.Country c = CountryData.get(sender.getServer()).byId(m.countryId);
        if (c != null) PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), ResidentService.build(sender, c, false));
    }
}
