package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

public class SendMailPacket {
    public final UUID countryId;
    public final String text;

    public SendMailPacket(UUID countryId, String text) { this.countryId = countryId; this.text = text; }

    public static void encode(SendMailPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.countryId);
        buf.writeUtf(m.text, 1000);
    }

    public static SendMailPacket decode(FriendlyByteBuf buf) {
        return new SendMailPacket(buf.readUUID(), buf.readUtf(1000));
    }

    public static void handle(SendMailPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender == null) return;
        String error = MailService.send(sender, m.countryId, m.text);
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                new CreateResultPacket(error == null, error == null ? "Письмо отправлено!" : error));
    }
}
