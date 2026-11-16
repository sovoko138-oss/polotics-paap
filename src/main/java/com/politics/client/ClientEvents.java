package com.politics.client;

import com.politics.ModBlocks;
import com.politics.PoliticsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

@Mod.EventBusSubscriber(modid = PoliticsMod.MODID, value = Dist.CLIENT)
public class ClientEvents {
    private static Field delayField;
    private static boolean searched = false;

    /** The "destroyDelay" field: by name in development, otherwise the first int field of the class. */
    private static Field findDelayField() {
        if (searched) return delayField;
        searched = true;
        try {
            Field firstInt = null;
            for (Field f : MultiPlayerGameMode.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
                if (f.getName().equals("destroyDelay")) { delayField = f; break; }
                if (firstInt == null) firstInt = f;
            }
            if (delayField == null) delayField = firstInt;
            if (delayField != null) delayField.setAccessible(true);
        } catch (Throwable t) {
            delayField = null;
        }
        return delayField;
    }

    /** Vanilla waits 5 ticks between two mined blocks while the button is held; the test pickaxe does not. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (!mc.player.getMainHandItem().is(ModBlocks.TEST_PICKAXE.get())) return;
        Field f = findDelayField();
        if (f == null) return;
        try {
            int v = f.getInt(mc.gameMode);
            if (v > 0 && v <= 5) f.setInt(mc.gameMode, 0);
        } catch (Throwable ignored) {
        }
    }
}
