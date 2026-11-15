package com.politics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class CreateCountryPacket {
    public final BlockPos pos;
    public final String name;
    public final String capital;
    public final String deputies;
    public final String flag;

    public CreateCountryPacket(BlockPos pos, String name, String capital, String deputies, String flag) {
        this.pos = pos; this.name = name; this.capital = capital; this.deputies = deputies; this.flag = flag;
    }

    public static void encode(CreateCountryPacket m, FriendlyByteBuf buf) {
        buf.writeBlockPos(m.pos);
        buf.writeUtf(m.name, 100);
        buf.writeUtf(m.capital, 100);
        buf.writeUtf(m.deputies, 500);
        buf.writeUtf(m.flag, 100);
    }

    public static CreateCountryPacket decode(FriendlyByteBuf buf) {
        return new CreateCountryPacket(buf.readBlockPos(), buf.readUtf(100), buf.readUtf(100),
                buf.readUtf(500), buf.readUtf(100));
    }

    public static void handle(CreateCountryPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender == null) return;
        String error = CountryService.tryCreate(sender, m);
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                new CreateResultPacket(error == null, error == null ? "Страна создана!" : error));
    }
}
