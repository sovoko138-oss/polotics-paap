package com.politics;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PoliticsNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PoliticsMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(OpenCreateScreenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenCreateScreenPacket::encode)
                .decoder(OpenCreateScreenPacket::decode)
                .consumerMainThread(OpenCreateScreenPacket::handle)
                .add();
        CHANNEL.messageBuilder(CreateCountryPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CreateCountryPacket::encode)
                .decoder(CreateCountryPacket::decode)
                .consumerMainThread(CreateCountryPacket::handle)
                .add();
        CHANNEL.messageBuilder(CreateResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CreateResultPacket::encode)
                .decoder(CreateResultPacket::decode)
                .consumerMainThread(CreateResultPacket::handle)
                .add();
        CHANNEL.messageBuilder(BorderSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BorderSyncPacket::encode)
                .decoder(BorderSyncPacket::decode)
                .consumerMainThread(BorderSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenChangeCityPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenChangeCityPacket::encode)
                .decoder(OpenChangeCityPacket::decode)
                .consumerMainThread(OpenChangeCityPacket::handle)
                .add();
        CHANNEL.messageBuilder(ChangeCityPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChangeCityPacket::encode)
                .decoder(ChangeCityPacket::decode)
                .consumerMainThread(ChangeCityPacket::handle)
                .add();
        CHANNEL.messageBuilder(CountriesPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CountriesPacket::encode)
                .decoder(CountriesPacket::decode)
                .consumerMainThread(CountriesPacket::handle)
                .add();
        CHANNEL.messageBuilder(PlayerListPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerListPacket::encode)
                .decoder(PlayerListPacket::decode)
                .consumerMainThread(PlayerListPacket::handle)
                .add();
        CHANNEL.messageBuilder(RequestPlayerListPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestPlayerListPacket::encode)
                .decoder(RequestPlayerListPacket::decode)
                .consumerMainThread(RequestPlayerListPacket::handle)
                .add();
        CHANNEL.messageBuilder(KickPlayerPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(KickPlayerPacket::encode)
                .decoder(KickPlayerPacket::decode)
                .consumerMainThread(KickPlayerPacket::handle)
                .add();
        CHANNEL.messageBuilder(SetTitlePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetTitlePacket::encode)
                .decoder(SetTitlePacket::decode)
                .consumerMainThread(SetTitlePacket::handle)
                .add();
        CHANNEL.messageBuilder(TrustPackets.OpenName.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TrustPackets.OpenName::encode)
                .decoder(TrustPackets.OpenName::decode)
                .consumerMainThread(TrustPackets.OpenName::handle)
                .add();
        CHANNEL.messageBuilder(TrustPackets.OpenRights.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TrustPackets.OpenRights::encode)
                .decoder(TrustPackets.OpenRights::decode)
                .consumerMainThread(TrustPackets.OpenRights::handle)
                .add();
        CHANNEL.messageBuilder(TrustPackets.Request.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TrustPackets.Request::encode)
                .decoder(TrustPackets.Request::decode)
                .consumerMainThread(TrustPackets.Request::handle)
                .add();
        CHANNEL.messageBuilder(TrustPackets.Set.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TrustPackets.Set::encode)
                .decoder(TrustPackets.Set::decode)
                .consumerMainThread(TrustPackets.Set::handle)
                .add();
        CHANNEL.messageBuilder(OpenMailPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenMailPacket::encode)
                .decoder(OpenMailPacket::decode)
                .consumerMainThread(OpenMailPacket::handle)
                .add();
        CHANNEL.messageBuilder(SendMailPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SendMailPacket::encode)
                .decoder(SendMailPacket::decode)
                .consumerMainThread(SendMailPacket::handle)
                .add();
        CHANNEL.messageBuilder(NoticePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NoticePacket::encode)
                .decoder(NoticePacket::decode)
                .consumerMainThread(NoticePacket::handle)
                .add();
    }
}
