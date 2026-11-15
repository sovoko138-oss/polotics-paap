package com.politics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps strangers from breaking, building, using doors/buttons and summoning inside a country. */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID)
public class ProtectionEvents {

    private static class Use {
        final ServerPlayer player;
        final long tick;
        Use(ServerPlayer player, long tick) { this.player = player; this.tick = tick; }
    }

    /** Players who right-clicked in the last few ticks (to find who summoned an entity). */
    private static final Map<UUID, Use> RECENT = new HashMap<>();

    /** True if the player may do this at this position. Notifies the player when not. */
    public static boolean allowed(ServerPlayer p, BlockPos pos, int right, boolean notify) {
        if (!Config.PROTECT_TERRITORY.get()) return true;
        CountryData data = CountryData.get(p.getServer());
        String dim = p.level().dimension().location().toString();
        CountryData.Country c = data.byChunk(dim, pos.getX() >> 4, pos.getZ() >> 4);
        if (c == null) return true;
        if (Config.OPS_BYPASS.get() && p.hasPermissions(2)) return true;
        if (ResidentService.viewerRank(p, c) >= 2) return true;
        if ((c.rightsOf(p.getGameProfile().getName()) & right) != 0) return true;
        if (notify) {
            p.displayClientMessage(Component.literal("Территория страны «" + c.name + "»: вам нельзя "
                    + Rights.actionText(right) + "."), true);
        }
        return false;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p && !allowed(p, e.getPos(), Rights.BREAK, true)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !allowed(p, e.getPos(), Rights.PLACE, true)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer p)) return;
        record(p);

        Block b = e.getLevel().getBlockState(e.getPos()).getBlock();
        int right = 0;
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) right = Rights.DOORS;
        else if (b instanceof ButtonBlock || b instanceof LeverBlock) right = Rights.BUTTONS;
        if (right != 0 && !allowed(p, e.getPos(), right, true)) {
            e.setUseBlock(Event.Result.DENY);
        }
        if (e.getItemStack().getItem() instanceof SpawnEggItem && !allowed(p, e.getPos(), Rights.SUMMON, true)) {
            e.setUseItem(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer p)) return;
        record(p);
        if (e.getItemStack().getItem() instanceof SpawnEggItem && !allowed(p, p.blockPosition(), Rights.SUMMON, true)) {
            e.setCanceled(true);
        }
    }

    private static void record(ServerPlayer p) {
        long now = p.level().getGameTime();
        RECENT.values().removeIf(u -> now - u.tick > 10);
        RECENT.put(p.getUUID(), new Use(p, now));
    }

    /**
     * An entity appears right after a player right-clicked next to it (vehicle item, boat, armor stand...):
     * if that player has no summon right in this country, the entity is not created.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent e) {
        Level level = e.getLevel();
        if (level.isClientSide() || e.loadedFromDisk() || RECENT.isEmpty()) return;
        Entity en = e.getEntity();
        if (en instanceof ItemEntity || en instanceof ExperienceOrb || en instanceof Projectile || en instanceof Player
                || en instanceof FallingBlockEntity || en instanceof PrimedTnt || en instanceof LightningBolt
                || en instanceof AreaEffectCloud) return;

        long now = level.getGameTime();
        ServerPlayer culprit = null;
        boolean someoneAllowed = false;
        for (Use u : RECENT.values()) {
            if (now - u.tick > 3 || u.player.level() != level || u.player.distanceToSqr(en) > 100) continue;
            if (allowed(u.player, en.blockPosition(), Rights.SUMMON, false)) someoneAllowed = true;
            else culprit = u.player;
        }
        if (culprit != null && !someoneAllowed) {
            allowed(culprit, en.blockPosition(), Rights.SUMMON, true);
            e.setCanceled(true);
        }
    }
}
