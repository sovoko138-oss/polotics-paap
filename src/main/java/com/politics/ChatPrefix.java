package com.politics;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Chat: residents of a country get "[Country]" in the colour of the country in front of their name. */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID)
public class ChatPrefix {
    @SubscribeEvent
    public static void onChat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        MinecraftServer server = p.getServer();
        if (server == null) return;
        String nick = p.getGameProfile().getName();
        CountryData.Country country = null;
        for (CountryData.Country c : CountryData.get(server).all()) {
            if (c.isMember(nick)) { country = c; break; }
        }
        if (country == null) return;

        e.setCanceled(true);
        MutableComponent msg = Component.literal("[" + country.name + "]")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(country.color)))
                .append(Component.literal(" "))
                .append(p.getDisplayName().copy().setStyle(Style.EMPTY))
                .append(Component.literal(": "))
                .append(e.getMessage().copy().setStyle(Style.EMPTY));
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }
}
