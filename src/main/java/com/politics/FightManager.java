package com.politics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The war between Свиностан and Криперляндия: 4 fighters from each side with a random character each.
 * BERSERKER rushes everybody, TANK is slow but armoured, COWARD shoots a bow from far away,
 * CHAOTIC does anything at all and moves very strangely.
 */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID)
public class FightManager {
    public enum Role {
        BERSERKER("Боец"), TANK("Танк"), COWARD("Трус"), CHAOTIC("Хаос");
        final String label;
        Role(String label) { this.label = label; }
    }

    private static class Fighter {
        UUID id, country;
        Role role;
        long nextAttack, nextShot;
    }

    public static final int PER_SIDE = 4;
    private static final Logger LOG = LoggerFactory.getLogger("politics");
    private static final Map<UUID, Fighter> FIGHTERS = new HashMap<>();
    private static final UUID MOD_ID = UUID.fromString("5d3a7b52-3f7e-4c61-9a1e-0c1d2e3f4a5b");
    private static final SoundEvent SHOOT = SoundEvent.createVariableRangeEvent(new ResourceLocation("minecraft", "entity.skeleton.shoot"));
    private static int counter = 0;

    // ------------------------------------------------------------------ queries

    public static boolean isFighter(UUID id) { return FIGHTERS.containsKey(id); }

    /** The living fighters of a country that are loaded in this world. */
    public static List<Mob> fightersOf(ServerLevel level, CountryData.Country c) {
        List<Mob> out = new ArrayList<>();
        for (Fighter f : FIGHTERS.values()) {
            if (!f.country.equals(c.id)) continue;
            if (level.getEntity(f.id) instanceof Mob m && m.isAlive()) out.add(m);
        }
        return out;
    }

    private static Mob find(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.getEntity(id) instanceof Mob m && m.isAlive()) return m;
        }
        return null;
    }

    // ------------------------------------------------------------------ starting the war

    /**
     * A letter "startwar" (or "startwar Name") to any kingdom: it declares war on the named kingdom,
     * or on the nearest free one if no name is given.
     */
    public static String startWar(MinecraftServer server, CountryData data, CountryData.Country target, String rest) {
        if (!target.npc) return "Это слово понимают только королевства.";
        if (data.warOf(target) != null) return "Страна «" + target.name + "» уже воюет.";
        CountryData.Country opponent = null;
        String name = rest == null ? "" : rest.trim();
        if (!name.isEmpty()) {
            opponent = data.byName(name);
            if (opponent == null || !opponent.npc) return "Королевство не найдено: " + name;
            if (opponent == target) return "Страна не может воевать сама с собой.";
            if (data.allied(target, opponent)) return "Эти страны в одном альянсе, воевать друг с другом они не будут.";
            if (data.warOf(opponent) != null) return "Страна «" + opponent.name + "» уже воюет.";
        } else {
            double best = Double.MAX_VALUE;
            for (CountryData.Country c : data.all()) {
                if (!c.npc || c == target || c.capitalPos == null || target.capitalPos == null) continue;
                if (data.warOf(c) != null || data.allied(target, c)) continue;
                double d = c.capitalPos.distSqr(target.capitalPos);
                if (d < best) { best = d; opponent = c; }
            }
            if (opponent == null) return "Нет свободного королевства, с которым можно воевать.";
        }
        WarService.declare(server, data, target, opponent);
        return null;
    }

    /** Every second of an NPC war: keeps PER_SIDE fighters on each side, new ones are picked among the residents. */
    public static void maintain(MinecraftServer server, CountryData data, CountryData.Country a, CountryData.Country b) {
        for (CountryData.Country n : new CountryData.Country[]{a, b}) {
            ServerLevel level = WarManager.levelOf(server, n);
            if (level == null || n.capitalPos == null) continue;
            int have = fightersOf(level, n).size();
            if (have >= PER_SIDE) continue;

            final String tag = WarManager.tagOf(n);
            List<Mob> pool = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(n.capitalPos).inflate(80, 128, 80),
                    m -> m.isAlive() && m.getTags().contains(tag) && !FIGHTERS.containsKey(m.getUUID())));
            Collections.shuffle(pool, new java.util.Random(level.getRandom().nextLong()));
            for (int i = 0; i < PER_SIDE - have && i < pool.size(); i++) {
                Fighter f = new Fighter();
                f.id = pool.get(i).getUUID();
                f.country = n.id;
                f.role = Role.values()[level.getRandom().nextInt(Role.values().length)]; // any mix is possible
                FIGHTERS.put(f.id, f);
                apply(pool.get(i), f.role, n);
            }
        }
    }

    private static void mod(Mob mob, Attribute attr, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(MOD_ID);
        inst.addTransientModifier(new AttributeModifier(MOD_ID, "politics_fighter", amount, op));
    }

    private static void apply(Mob mob, Role role, CountryData.Country country) {
        double speed = switch (role) {
            case BERSERKER -> 0.9;  // fastest
            case TANK -> -0.35;     // slower than usual
            case COWARD -> 0.1;
            case CHAOTIC -> 0.3;
        };
        mod(mob, Attributes.MOVEMENT_SPEED, speed, AttributeModifier.Operation.MULTIPLY_TOTAL);
        if (role == Role.TANK) {
            mod(mob, Attributes.ARMOR, 15, AttributeModifier.Operation.ADDITION);
            mod(mob, Attributes.MAX_HEALTH, 20, AttributeModifier.Operation.ADDITION);
            mob.setHealth(mob.getMaxHealth());
        }
        // pigs would run away in panic when hurt; a fighter does not
        for (WrappedGoal wg : new ArrayList<>(mob.goalSelector.getAvailableGoals())) {
            if (wg.getGoal() instanceof PanicGoal) mob.goalSelector.removeGoal(wg.getGoal());
        }
        mob.setCustomName(Component.literal(role.label).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(country.color))));
        mob.setCustomNameVisible(true);
    }

    private static void release(Mob mob) {
        if (mob == null) return;
        for (Attribute a : new Attribute[]{Attributes.MOVEMENT_SPEED, Attributes.ARMOR, Attributes.MAX_HEALTH}) {
            AttributeInstance inst = mob.getAttribute(a);
            if (inst != null) inst.removeModifier(MOD_ID);
        }
        mob.setCustomName(null);
        mob.setCustomNameVisible(false);
        if (mob.getHealth() > mob.getMaxHealth()) mob.setHealth(mob.getMaxHealth());
    }

    public static void releaseCountry(MinecraftServer server, UUID countryId) {
        for (Fighter f : new ArrayList<>(FIGHTERS.values())) {
            if (!f.country.equals(countryId)) continue;
            release(find(server, f.id));
            FIGHTERS.remove(f.id);
        }
    }

    public static void reset() { FIGHTERS.clear(); counter = 0; }

    // ------------------------------------------------------------------ capture and defence

    /** Fighters standing in an enemy chunk capture it (or take their own back); residents standing at home defend. */
    public static void addHits(MinecraftServer server, CountryData.Country a, CountryData.Country b,
                               CountryData.War w, Map<Long, Integer> hit, Set<Long> defended) {
        for (CountryData.Country n : new CountryData.Country[]{a, b}) {
            CountryData.Country enemy = n == a ? b : a;
            ServerLevel level = WarManager.levelOf(server, n);
            if (level == null || n.capitalPos == null) continue;
            for (Mob m : fightersOf(level, n)) {
                ChunkPos pc = m.chunkPosition();
                long k = CountryData.chunkKey(pc.x, pc.z);
                boolean attack = enemy.chunks.contains(k) && !w.captured.contains(k);
                boolean retake = n.chunks.contains(k) && w.captured.contains(k);
                if (attack || retake) hit.merge(k, 1, Integer::sum);
            }
            final String tag = WarManager.tagOf(n);
            for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(n.capitalPos).inflate(96, 128, 96),
                    x -> x.isAlive() && x.getTags().contains(tag) && !FIGHTERS.containsKey(x.getUUID()))) {
                ChunkPos pc = m.chunkPosition();
                long k = CountryData.chunkKey(pc.x, pc.z);
                if (n.chunks.contains(k) && !w.captured.contains(k)) defended.add(k);
            }
        }
    }

    // ------------------------------------------------------------------ the fight itself (4 times a second)

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (++counter % 5 != 0) return;
        if (FIGHTERS.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        try {
            tick(server);
        } catch (Throwable t) {
            LOG.warn("[politics] ошибка в FightManager", t);
        }
    }

    private static void tick(MinecraftServer server) {
        CountryData data = CountryData.get(server);
        long now = server.overworld().getGameTime();
        for (Fighter f : new ArrayList<>(FIGHTERS.values())) {
            CountryData.Country own = data.byId(f.country);
            Mob mob = find(server, f.id);
            CountryData.War w = own == null ? null : data.warOf(own);
            CountryData.Country enemy = w == null ? null : data.byId(w.attacker.equals(own.id) ? w.defender : w.attacker);
            if (own == null || w == null || !w.active || enemy == null || !enemy.npc) {
                release(mob);
                FIGHTERS.remove(f.id);
                continue;
            }
            if (mob == null) { FIGHTERS.remove(f.id); continue; } // dead (a new fighter is picked by maintain())
            Walk.calm(mob);
            act((ServerLevel) mob.level(), own, enemy, w, mob, f, now);
        }
    }

    private static Mob nearest(ServerLevel level, Mob mob, String tag, double range) {
        Mob best = null;
        double bd = Double.MAX_VALUE;
        for (Mob m : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(range),
                x -> x != mob && x.isAlive() && x.getTags().contains(tag))) {
            double d = mob.distanceToSqr(m);
            if (d < bd) { bd = d; best = m; }
        }
        return best;
    }

    private static void chase(Mob mob, Mob target, double speed) {
        Walk.toEntity(mob, target, speed);
        mob.getLookControl().setLookAt(target, 30f, 30f);
    }

    private static void melee(ServerLevel level, Mob mob, Mob target, float damage, long now, Fighter f) {
        if (now < f.nextAttack || mob.distanceTo(target) > 2.3f) return;
        f.nextAttack = now + 20;
        target.hurt(level.damageSources().mobAttack(mob), damage);
        double dx = target.getX() - mob.getX(), dz = target.getZ() - mob.getZ();
        target.knockback(0.5, -dx, -dz);
    }

    private static void shoot(ServerLevel level, Mob mob, Mob target, long now, Fighter f) {
        if (now < f.nextShot) return;
        f.nextShot = now + 30;
        Arrow arrow = new Arrow(level, mob);
        double dx = target.getX() - mob.getX();
        double dy = target.getY(0.33) - arrow.getY();
        double dz = target.getZ() - mob.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + h * 0.2, dz, 1.6f, 6f);
        arrow.setBaseDamage(1.5);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.addFreshEntity(arrow);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SHOOT, SoundSource.HOSTILE, 1.0f, 1.0f);
        mob.getLookControl().setLookAt(target, 30f, 30f);
    }

    private static void moveToPoint(ServerLevel level, Mob mob, double x, double z, double speed) {
        if (!level.hasChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) return;
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        Walk.to(mob, x, y, z, speed);
    }

    /** With nobody to fight: go to the nearest enemy chunk that is not captured yet (or take back a lost one) and stand in it. */
    private static void march(ServerLevel level, Mob mob, CountryData.Country own, CountryData.Country enemy,
                              CountryData.War w, double speed) {
        ChunkPos pc = mob.chunkPosition();
        long best = 0;
        double bd = Double.MAX_VALUE;
        boolean found = false;
        List<Long> cands = new ArrayList<>();
        for (long k : own.chunks) if (w.captured.contains(k)) cands.add(k);
        for (long k : enemy.chunks) if (!w.captured.contains(k)) cands.add(k);
        for (long k : cands) {
            if (!level.hasChunk(CountryData.keyX(k), CountryData.keyZ(k))) continue;
            double dx = CountryData.keyX(k) - pc.x, dz = CountryData.keyZ(k) - pc.z;
            double d = dx * dx + dz * dz;
            if (d < bd) { bd = d; best = k; found = true; }
        }
        if (!found) {
            if (enemy.capitalPos != null) moveToPoint(level, mob, enemy.capitalPos.getX() + 0.5, enemy.capitalPos.getZ() + 0.5, speed);
            return;
        }
        double tx = CountryData.keyX(best) * 16 + 8.5, tz = CountryData.keyZ(best) * 16 + 8.5;
        if (pc.x != CountryData.keyX(best) || pc.z != CountryData.keyZ(best) || mob.distanceToSqr(tx, mob.getY(), tz) > 36) {
            moveToPoint(level, mob, tx, tz, speed);
        } else {
            Walk.stop(mob); // standing in the chunk captures it
        }
    }

    private static void act(ServerLevel level, CountryData.Country own, CountryData.Country enemy,
                            CountryData.War w, Mob mob, Fighter f, long now) {
        RandomSource rnd = level.getRandom();
        Mob target = nearest(level, mob, WarManager.tagOf(enemy), 32);
        switch (f.role) {
            case BERSERKER -> { // rushes at everybody, fastest of all
                if (target != null) { chase(mob, target, 1.6); melee(level, mob, target, 4f, now, f); }
                else march(level, mob, own, enemy, w, 1.5);
            }
            case TANK -> { // slow, but with armour and a heavy hit
                if (target != null) { chase(mob, target, 1.0); melee(level, mob, target, 6f, now, f); }
                else march(level, mob, own, enemy, w, 1.0);
            }
            case COWARD -> { // keeps away and shoots a bow
                if (target == null) { march(level, mob, own, enemy, w, 1.1); break; }
                double d = mob.distanceTo(target);
                if (d < 8) {
                    double fx = mob.getX() + (mob.getX() - target.getX()) / Math.max(d, 0.1) * 10;
                    double fz = mob.getZ() + (mob.getZ() - target.getZ()) / Math.max(d, 0.1) * 10;
                    moveToPoint(level, mob, fx, fz, 1.5);
                    if (d > 3) shoot(level, mob, target, now, f);
                } else if (d <= 22) {
                    Walk.stop(mob);
                    shoot(level, mob, target, now, f);
                } else {
                    chase(mob, target, 1.1);
                }
            }
            case CHAOTIC -> { // anything at all
                switch (rnd.nextInt(9)) {
                    case 0, 1 -> {
                        if (target != null) chase(mob, target, 1.9);
                        else moveToPoint(level, mob, mob.getX() + rnd.nextInt(13) - 6, mob.getZ() + rnd.nextInt(13) - 6, 1.7);
                    }
                    case 2 -> moveToPoint(level, mob, mob.getX() + rnd.nextInt(11) - 5, mob.getZ() + rnd.nextInt(11) - 5, 1.9);
                    case 3 -> mob.getJumpControl().jump();
                    case 4 -> {
                        float yr = mob.getYRot() + 90 + rnd.nextInt(180);
                        mob.setYRot(yr);
                        mob.setYBodyRot(yr);
                        mob.setYHeadRot(yr);
                    }
                    case 5 -> { if (target != null) shoot(level, mob, target, now, f); }
                    case 6 -> moveToPoint(level, mob, mob.getX() + rnd.nextInt(41) - 20, mob.getZ() + rnd.nextInt(41) - 20, 1.6);
                    case 7 -> {
                        double nx = mob.getX() + rnd.nextInt(9) - 4, nz = mob.getZ() + rnd.nextInt(9) - 4;
                        if (level.hasChunk((int) Math.floor(nx) >> 4, (int) Math.floor(nz) >> 4)) {
                            int ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(nx), (int) Math.floor(nz));
                            mob.teleportTo(nx, ny, nz);
                        }
                    }
                    default -> Walk.stop(mob);
                }
                if (target != null) melee(level, mob, target, 3f, now, f);
            }
        }
    }
}
