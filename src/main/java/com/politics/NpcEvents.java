package com.politics;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The creepers of Криперляндия are peaceful: they never target anybody and never explode. */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID)
public class NpcEvents {
    private static boolean isCreeperCitizen(Entity e) {
        return e != null && e.getTags().contains("politics_creeper");
    }

    /** No target, so no chasing and no swelling. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent e) {
        if (isCreeperCitizen(e.getEntity())) e.setCanceled(true);
    }

    /** Safety net: an explosion started by such a creeper does not happen. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Start e) {
        if (isCreeperCitizen(e.getExplosion().getExploder())) e.setCanceled(true);
    }

    /** Nobody can set a citizen on fire with flint and steel, ride it, or use it in any other way. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        for (String t : e.getTarget().getTags()) {
            if (t.startsWith("politics_")) { e.setCanceled(true); return; }
        }
    }
}
