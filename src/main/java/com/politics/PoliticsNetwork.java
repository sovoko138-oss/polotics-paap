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
    }
}
