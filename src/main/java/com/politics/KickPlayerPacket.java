package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

public class KickPlayerPacket {
    public final UUID countryId;
    public final String name;

    public KickPlayerPacket(UUID countryId, String name) { this.countryId = countryId; this.name = name; }

    public static void encode(KickPlayerPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.countryId);
        buf.writeUtf(m.name, 64);
    }

    public static KickPlayerPacket decode(FriendlyByteBuf buf) { return new KickPlayerPacket(buf.readUUID(), buf.readUtf(64)); }

    public static void handle(KickPlayerPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender == null) return;
        String error = ResidentService.kick(sender, m);
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                new CreateResultPacket(error == null, error == null ? "Игрок исключён." : error));
        CountryData.Country c = CountryData.get(sender.getServer()).byId(m.countryId);
        if (c != null) PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), ResidentService.build(sender, c, false));
    }
}
