package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/** The open residents window asks for fresh data (online status changes). */
public class RequestPlayerListPacket {
    public final UUID countryId;

    public RequestPlayerListPacket(UUID countryId) { this.countryId = countryId; }

    public static void encode(RequestPlayerListPacket m, FriendlyByteBuf buf) { buf.writeUUID(m.countryId); }

    public static RequestPlayerListPacket decode(FriendlyByteBuf buf) { return new RequestPlayerListPacket(buf.readUUID()); }

    public static void handle(RequestPlayerListPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender == null) return;
        CountryData data = CountryData.get(sender.getServer());
        CountryData.Country c = data.byId(m.countryId);
        if (c == null || !ResidentService.canView(sender, c, data)) return;
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), ResidentService.build(sender, c, false));
    }
}
