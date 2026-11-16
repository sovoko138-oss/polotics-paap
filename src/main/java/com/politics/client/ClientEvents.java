package com.politics.client;

import com.politics.ModBlocks;
import com.politics.PoliticsMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PoliticsMod.MODID, value = Dist.CLIENT)
public class ClientEvents {

    /** Vanilla waits 5 ticks between two mined blocks while the button is held; the test pickaxe does not. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.player.getMainHandItem().is(ModBlocks.TEST_PICKAXE.get())) {
            mc.gameMode.destroyDelay = 0;
        }
    }
}
