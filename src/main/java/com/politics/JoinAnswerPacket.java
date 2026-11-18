package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/** The tick or the cross next to a player in the /accept window. */
public class JoinAnswerPacket {
    public final UUID countryId;
    public final String nick;
    public final boolean accept;

    public JoinAnswerPacket(UUID countryId, String nick, boolean accept) {
        this.countryId = countryId; this.nick = nick; this.accept = accept;
    }

    public static void encode(JoinAnswerPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.countryId);
        buf.writeUtf(m.nick, 64);
        buf.writeBoolean(m.accept);
    }

    public static JoinAnswerPacket decode(FriendlyByteBuf buf) {
        return new JoinAnswerPacket(buf.readUUID(), buf.readUtf(64), buf.readBoolean());
    }

    public static void handle(JoinAnswerPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender == null) return;
        String error = JoinService.answer(sender, m);
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                new CreateResultPacket(error == null,
                        error == null ? (m.accept ? "Игрок принят в страну." : "Заявка отклонена.") : error));
        CountryData.Country c = CountryData.get(sender.getServer()).byId(m.countryId);
        if (c != null) PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), JoinService.build(c, false));
    }
}
