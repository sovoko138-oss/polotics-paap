package com.politics;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** When a player walks into the territory of another country, its name appears above the hotbar in its colour. */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID)
public class TerritoryNotifier {
    private static final UUID NONE = new UUID(0, 0);
    private static final Map<UUID, UUID> LAST = new HashMap<>();
    private static int counter = 0;

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (++counter % 10 != 0) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        CountryData data = CountryData.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String dim = p.level().dimension().location().toString();
            CountryData.Country c = data.byChunk(dim, p.blockPosition().getX() >> 4, p.blockPosition().getZ() >> 4);
            UUID now = c == null ? NONE : c.id;
            UUID before = LAST.put(p.getUUID(), now);
            if (now.equals(before) || c == null) continue;
            if (c.isMember(p.getGameProfile().getName())) continue; // own country: nothing to announce
            p.displayClientMessage(Component.literal(c.name)
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(c.color)).withBold(true)), true);
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        LAST.clear();
        counter = 0;
    }
}
