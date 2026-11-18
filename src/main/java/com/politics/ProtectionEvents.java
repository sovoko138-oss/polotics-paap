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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.level.ExplosionEvent;
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
        if (ResidentService.viewerRank(p, c) >= 2 || c.isMember(p.getGameProfile().getName())) return true;
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

    /** Items that change the world: placing, filling, lighting, tilling, summoning. */
    private static boolean changesWorld(Item item) {
        return item instanceof BlockItem || item instanceof BucketItem || item instanceof FlintAndSteelItem
                || item instanceof FireChargeItem || item instanceof SpawnEggItem || item instanceof DiggerItem
                || item instanceof BoneMealItem || item instanceof ShearsItem || item instanceof HangingEntityItem
                || item instanceof BoatItem || item instanceof MinecartItem || item instanceof ArmorStandItem;
    }

    /** In a foreign country nothing may be used or opened: doors, chests, furnaces, crafting tables... */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer p)) return;
        record(p);

        Block b = e.getLevel().getBlockState(e.getPos()).getBlock();
        int right;
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) right = Rights.DOORS;
        else if (b instanceof ButtonBlock || b instanceof LeverBlock) right = Rights.BUTTONS;
        else right = Rights.PLACE;
        if (!allowed(p, e.getPos(), right, true)) {
            e.setUseBlock(Event.Result.DENY);
        }

        Item item = e.getItemStack().getItem();
        if (changesWorld(item)) {
            int r = item instanceof SpawnEggItem ? Rights.SUMMON : Rights.PLACE;
            BlockPos target = e.getFace() == null ? e.getPos() : e.getPos().relative(e.getFace());
            if (!allowed(p, e.getPos(), r, true) || !allowed(p, target, r, false)) {
                e.setUseItem(Event.Result.DENY);
            }
        }
    }

    /** Punching blocks (fire, notes, pressing buttons by hitting...) is not allowed either. */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer p)) return;
        boolean start = e.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START;
        if (!allowed(p, e.getPos(), Rights.BREAK, start)) e.setCanceled(true);
    }

    /** Item frames, armor stands, animals, villagers, boats... cannot be used in a foreign country. Monsters and players can. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        guardEntity(e.getLevel().isClientSide(), e.getEntity(), e.getTarget(), Rights.PLACE, e);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) {
        guardEntity(e.getLevel().isClientSide(), e.getEntity(), e.getTarget(), Rights.PLACE, e);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent e) {
        if (e.getEntity().level().isClientSide() || !(e.getEntity() instanceof ServerPlayer p)) return;
        Entity t = e.getTarget();
        if (t instanceof Player || t instanceof Enemy) return;
        if (!allowed(p, t.blockPosition(), Rights.BREAK, true)) e.setCanceled(true);
    }

    private static void guardEntity(boolean client, Player player, Entity target, int right, PlayerInteractEvent e) {
        if (client || !(player instanceof ServerPlayer p)) return;
        if (target instanceof Player || target instanceof Enemy) return;
        if (!allowed(p, target.blockPosition(), right, true)) e.setCanceled(true);
    }

    /** Explosions do not destroy blocks inside any country. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate e) {
        if (!Config.PROTECT_TERRITORY.get() || e.getLevel().isClientSide()) return;
        net.minecraft.server.MinecraftServer server = e.getLevel().getServer();
        if (server == null) return;
        CountryData data = CountryData.get(server);
        String dim = e.getLevel().dimension().location().toString();
        e.getAffectedBlocks().removeIf(pos -> data.byChunk(dim, pos.getX() >> 4, pos.getZ() >> 4) != null);
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
