package com.politics;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The course of a war, once per second: preparation, capturing chunks (100 s of standing in a chunk),
 * the pigs' army, the table on the screen and the end of the war.
 */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID)
public class WarManager {
    /** Tag of the pigs that belong to the pig country. */
    public static final String TAG = "politics_pig";
    private static final int ARMY = 2;
    private static final Logger LOG = LoggerFactory.getLogger("politics");

    private static final SoundEvent HORN = SoundEvent.createVariableRangeEvent(new ResourceLocation("minecraft", "event.raid.horn"));
    private static final SoundEvent WIN = SoundEvent.createVariableRangeEvent(new ResourceLocation("minecraft", "ui.toast.challenge_complete"));
    private static final SoundEvent LOSE = SoundEvent.createVariableRangeEvent(new ResourceLocation("minecraft", "entity.wither.death"));

    private static class Mission {
        long chunk;
        int age;
    }

    private static final Map<UUID, Map<UUID, Mission>> MISSIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_SPAWN = new HashMap<>();
    private static int counter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (++counter % 20 != 0) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        try {
            second(server);
        } catch (Throwable t) {
            LOG.warn("[politics] ошибка в WarManager", t);
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        MISSIONS.clear();
        LAST_SPAWN.clear();
        counter = 0;
        BorderSync.last = Long.MIN_VALUE;
        PigProtocol.reset();
    }

    private static void second(MinecraftServer server) {
        CountryData data = CountryData.get(server);
        long now = server.overworld().getGameTime();

        PigProtocol.growTick(server, data);
        autoDeclare(server, data);
        for (CountryData.War w : new ArrayList<>(data.wars())) processWar(server, data, w, now);
        for (CountryData.Country c : new ArrayList<>(data.all())) if (c.npc) pigsTick(server, data, c, now);
        BorderSync.tick(server, data);
    }

    /** The pigs declare war on any player country that has reached the needed number of chunks. */
    private static void autoDeclare(MinecraftServer server, CountryData data) {
        CountryData.Country pigs = data.byName(PigProtocol.NAME);
        if (pigs == null || !pigs.npc || data.warOf(pigs) != null) return;
        int need = Config.WAR_MIN_CHUNKS.get();
        for (CountryData.Country h : new ArrayList<>(data.all())) {
            if (h.npc || h.chunks.size() < need || data.warOf(h) != null) continue;
            WarService.declare(server, data, pigs, h);
            return;
        }
    }

    // ------------------------------------------------------------------ the war itself

    private static void processWar(MinecraftServer server, CountryData data, CountryData.War w, long now) {
        CountryData.Country a = data.byId(w.attacker), b = data.byId(w.defender);
        if (a == null || b == null) {
            data.wars().remove(w);
            data.setDirty();
            return;
        }

        if (!w.active) {
            long startAt = w.declaredAt + Config.WAR_PREP_SECONDS.get() * 20L;
            if (now < startAt) {
                sendStatus(server, a, b, w, 1, (int) ((startAt - now) / 20));
                return;
            }
            w.active = true;
            w.attackerStart = a.chunks.size();
            w.defenderStart = b.chunks.size();
            data.setDirty();
            Component msg = Component.literal("Началась война, начинаются боевые действия!").withStyle(ChatFormatting.RED);
            server.getPlayerList().broadcastSystemMessage(msg, false);
            NoticePacket notice = new NoticePacket("Война!", "Началась война, начинаются боевые действия!", WarService.WAR_RED);
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                String nick = sp.getGameProfile().getName();
                if (a.isMember(nick) || b.isMember(nick)) {
                    PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), notice);
                    sp.playNotifySound(HORN, SoundSource.MASTER, 1.0f, 1.0f); // the pillagers' horn
                }
            }
        }

        // who stands in an enemy chunk this second
        Set<Long> hit = new HashSet<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.isSpectator() || !sp.isAlive()) continue;
            String nick = sp.getGameProfile().getName();
            CountryData.Country enemy = a.isMember(nick) ? b : (b.isMember(nick) ? a : null);
            if (enemy == null) continue;
            if (!sp.level().dimension().location().toString().equals(enemy.dimension)) continue;
            ChunkPos cp = sp.chunkPosition();
            long k = CountryData.chunkKey(cp.x, cp.z);
            if (enemy.chunks.contains(k)) hit.add(k);
        }
        for (CountryData.Country n : new CountryData.Country[]{a, b}) {
            if (!n.npc) continue;
            CountryData.Country enemy = n == a ? b : a;
            ServerLevel level = levelOf(server, n);
            Map<UUID, Mission> ms = MISSIONS.get(n.id);
            if (level == null || ms == null) continue;
            for (Map.Entry<UUID, Mission> en : ms.entrySet()) {
                Entity ent = level.getEntity(en.getKey());
                if (!(ent instanceof Pig pig) || !pig.isAlive()) continue;
                ChunkPos pc = pig.chunkPosition();
                long k = CountryData.chunkKey(pc.x, pc.z);
                if (k == en.getValue().chunk && enemy.chunks.contains(k)) hit.add(k);
            }
        }

        // progress: +1 % per second while someone stands there (+50 % faster once the owner's capital chunk is lost),
        // slowly back down otherwise. A captured chunk changes owner at once.
        float step = 100f / Math.max(1, Config.CAPTURE_SECONDS.get());
        CountryData.Country defeated = null, victor = null;
        for (long k : hit) {
            CountryData.Country owner = a.chunks.contains(k) ? a : b;
            CountryData.Country taker = owner == a ? b : a;
            float speed = capitalLost(owner) ? 1.5f : 1f;
            float p = w.progress.getOrDefault(k, 0f) + step * speed;
            if (p >= 99.99f) {
                w.progress.remove(k);
                owner.chunks.remove(k);
                taker.chunks.add(k);
                Component msg = Component.literal("Чанк " + CountryData.keyX(k) + ", " + CountryData.keyZ(k) + " страны ")
                        .append(WarService.coloured(owner)).append(Component.literal(" захвачен: "))
                        .append(WarService.coloured(taker)).append(Component.literal("."));
                for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                    String nick = sp.getGameProfile().getName();
                    if (a.isMember(nick) || b.isMember(nick)) sp.sendSystemMessage(msg);
                }
                if (owner.chunks.isEmpty()) { defeated = owner; victor = taker; }
            } else {
                w.progress.put(k, p);
            }
        }
        Iterator<Map.Entry<Long, Float>> it = w.progress.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Float> en = it.next();
            if (hit.contains(en.getKey())) continue;
            float p = en.getValue() - step;
            if (p <= 0) it.remove(); else en.setValue(p);
        }
        data.setDirty();

        if (defeated != null) {
            endWar(server, data, victor, defeated);
            return;
        }
        sendStatus(server, a, b, w, 2, 0);
    }

    /** True once the enemy has taken the chunk with the country's capital block. */
    private static boolean capitalLost(CountryData.Country c) {
        if (c.capitalPos == null) return false;
        return !c.chunks.contains(CountryData.chunkKey(c.capitalPos.getX() >> 4, c.capitalPos.getZ() >> 4));
    }

    private static void endWar(MinecraftServer server, CountryData data, CountryData.Country winner, CountryData.Country loser) {
        Component msg = Component.literal("Страна ").append(WarService.coloured(loser))
                .append(Component.literal(" проиграла в войне со страной ")).append(WarService.coloured(winner))
                .append(Component.literal(", территории поверженной страны переходят на сторону врага."));
        String plain = "Страна " + loser.name + " проиграла в войне со страной " + winner.name
                + ", территории поверженной страны переходят на сторону врага.";

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String nick = sp.getGameProfile().getName();
            boolean won = winner.isMember(nick);
            if (won || loser.isMember(nick)) {
                PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                        new NoticePacket("Война окончена", plain, winner.color));
                // sound and a big title at the top of the screen
                sp.playNotifySound(won ? WIN : LOSE, SoundSource.MASTER, 1.0f, 1.0f);
                sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 30));
                sp.connection.send(new ClientboundSetSubtitleTextPacket(
                        Component.literal(won ? "Страна " + loser.name + " повержена" : "Ваша страна повержена")
                                .withStyle(ChatFormatting.GRAY)));
                sp.connection.send(new ClientboundSetTitleTextPacket(
                        Component.literal(won ? "ПОБЕДА" : "ПРОИГРЫШ")
                                .withStyle(won ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD)));
            }
        }
        server.getPlayerList().broadcastSystemMessage(msg, false);

        MISSIONS.remove(loser.id);
        MISSIONS.remove(winner.id);
        data.remove(loser); // the defeated country ceases to exist; all its land is already the winner's
        data.setDirty();
        BorderSync.last = Long.MIN_VALUE; // resend all borders on the next tick
    }

    private static void sendStatus(MinecraftServer server, CountryData.Country a, CountryData.Country b,
                                   CountryData.War w, int phase, int seconds) {
        WarStatusPacket forA = buildStatus(a, b, w, phase, seconds);
        WarStatusPacket forB = buildStatus(b, a, w, phase, seconds);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String nick = sp.getGameProfile().getName();
            WarStatusPacket p = a.isMember(nick) ? forA : (b.isMember(nick) ? forB : null);
            if (p != null) PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), p);
        }
    }

    private static WarStatusPacket buildStatus(CountryData.Country mine, CountryData.Country enemy,
                                               CountryData.War w, int phase, int seconds) {
        int myStart = Math.max(1, mine.id.equals(w.attacker) ? w.attackerStart : w.defenderStart);
        int enemyStart = Math.max(1, mine.id.equals(w.attacker) ? w.defenderStart : w.attackerStart);
        int myLost = Math.max(0, myStart - mine.chunks.size());
        int enemyLost = Math.max(0, enemyStart - enemy.chunks.size());

        List<WarStatusPacket.Row> rows = new ArrayList<>();
        float myProgress = 0, enemyProgress = 0;
        for (Map.Entry<Long, Float> en : w.progress.entrySet()) {
            long k = en.getKey();
            boolean isMine = mine.chunks.contains(k);
            if (isMine) myProgress += en.getValue() / 100f; else enemyProgress += en.getValue() / 100f;
            rows.add(new WarStatusPacket.Row(CountryData.keyX(k), CountryData.keyZ(k),
                    Math.min(99, Math.round(en.getValue())), isMine, false));
        }
        rows.sort(Comparator.comparingInt((WarStatusPacket.Row r) -> -r.percent));
        if (rows.size() > 12) rows = new ArrayList<>(rows.subList(0, 12));
        int myPercent = Math.min(100, Math.round((myLost + myProgress) * 100f / myStart));
        int enemyPercent = Math.min(100, Math.round((enemyLost + enemyProgress) * 100f / enemyStart));
        return new WarStatusPacket(phase, seconds, enemy.name, enemy.color, mine.color,
                myStart, myLost, enemyStart, enemyLost, myPercent, enemyPercent, rows);
    }

    // ------------------------------------------------------------------ the pigs

    private static ServerLevel levelOf(MinecraftServer server, CountryData.Country c) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(c.dimension)));
    }

    private static void pigsTick(MinecraftServer server, CountryData data, CountryData.Country c, long now) {
        ServerLevel level = levelOf(server, c);
        if (level == null || c.chunks.isEmpty()) return;

        // territory box
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (long k : c.chunks) {
            minX = Math.min(minX, CountryData.keyX(k)); maxX = Math.max(maxX, CountryData.keyX(k));
            minZ = Math.min(minZ, CountryData.keyZ(k)); maxZ = Math.max(maxZ, CountryData.keyZ(k));
        }
        AABB box = new AABB((minX - 1) * 16, level.getMinBuildHeight(), (minZ - 1) * 16,
                (maxX + 2) * 16, level.getMaxBuildHeight(), (maxZ + 2) * 16);

        Set<Pig> pigs = new LinkedHashSet<>(level.getEntitiesOfClass(Pig.class, box,
                p -> p.isAlive() && p.getTags().contains(TAG)));
        Map<UUID, Mission> ms = MISSIONS.computeIfAbsent(c.id, k -> new HashMap<>());
        Iterator<Map.Entry<UUID, Mission>> mit = ms.entrySet().iterator();
        while (mit.hasNext()) {
            Entity ent = level.getEntity(mit.next().getKey());
            if (ent instanceof Pig p && p.isAlive()) pigs.add(p); else mit.remove();
        }

        CountryData.War war = data.warOf(c);
        if (war != null && pigs.size() < ARMY) spawnPig(level, c, now);

        CountryData.Country enemy = null;
        if (war != null) enemy = data.byId(war.attacker.equals(c.id) ? war.defender : war.attacker);
        boolean fighting = war != null && war.active && enemy != null;

        if (!fighting) {
            ms.clear();
        } else {
            final CountryData.Country en = enemy;
            ms.values().removeIf(m -> !en.chunks.contains(m.chunk));
            Set<Long> taken = new HashSet<>();
            for (Mission m : ms.values()) taken.add(m.chunk);
            for (Pig pig : pigs) {
                if (ms.size() >= ARMY) break;
                if (ms.containsKey(pig.getUUID())) continue;
                ChunkPos pc = pig.chunkPosition();
                long best = 0;
                double bestD = Double.MAX_VALUE;
                boolean found = false;
                for (long k : en.chunks) {
                    if (taken.contains(k)) continue;
                    if (!level.hasChunk(CountryData.keyX(k), CountryData.keyZ(k))) continue;
                    double dx = CountryData.keyX(k) - pc.x, dz = CountryData.keyZ(k) - pc.z;
                    double d = dx * dx + dz * dz;
                    if (d < bestD) { bestD = d; best = k; found = true; }
                }
                if (!found) break;
                Mission m = new Mission();
                m.chunk = best;
                ms.put(pig.getUUID(), m);
                taken.add(best);
            }
        }

        for (Pig pig : pigs) {
            Mission m = ms.get(pig.getUUID());
            if (m != null) goToChunk(level, pig, m);
            else keepInside(level, pig, c);
        }
    }

    private static void spawnPig(ServerLevel level, CountryData.Country c, long now) {
        if (c.capitalPos == null || !level.isLoaded(c.capitalPos)) return;
        Long last = LAST_SPAWN.get(c.id);
        if (last != null && now - last < 600) return;
        LAST_SPAWN.put(c.id, now);
        Pig pig = EntityType.PIG.create(level);
        if (pig == null) return;
        pig.moveTo(c.capitalPos.getX() + 0.5, c.capitalPos.getY() + 1, c.capitalPos.getZ() + 0.5, 0f, 0f);
        pig.addTag(TAG);
        pig.setPersistenceRequired();
        level.addFreshEntity(pig);
    }

    /** An army pig walks into the target chunk and stands there; if it is stuck for too long it is carried over. */
    private static void goToChunk(ServerLevel level, Pig pig, Mission m) {
        int cx = CountryData.keyX(m.chunk), cz = CountryData.keyZ(m.chunk);
        if (!level.hasChunk(cx, cz)) return;
        m.age++;
        double tx = cx * 16 + 8.5, tz = cz * 16 + 8.5;
        double ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) tx, (int) tz);
        ChunkPos pc = pig.chunkPosition();
        boolean inside = pc.x == cx && pc.z == cz;
        if (!inside) {
            if (m.age > 45) pig.teleportTo(tx, ty, tz);
            else pig.getNavigation().moveTo(tx, ty, tz, 1.3);
        } else if (pig.distanceToSqr(tx, pig.getY(), tz) > 36) {
            pig.getNavigation().moveTo(tx, ty, tz, 1.0);
        }
    }

    /** The pigs of the pig country walk only inside their own territory. */
    private static void keepInside(ServerLevel level, Pig pig, CountryData.Country c) {
        ChunkPos pc = pig.chunkPosition();
        if (c.chunks.contains(CountryData.chunkKey(pc.x, pc.z))) return;
        long best = 0;
        double bestD = Double.MAX_VALUE;
        for (long k : c.chunks) {
            double dx = CountryData.keyX(k) - pc.x, dz = CountryData.keyZ(k) - pc.z;
            double d = dx * dx + dz * dz;
            if (d < bestD) { bestD = d; best = k; }
        }
        int cx = CountryData.keyX(best), cz = CountryData.keyZ(best);
        if (!level.hasChunk(cx, cz)) return;
        double tx = cx * 16 + 8.5, tz = cz * 16 + 8.5;
        double ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) tx, (int) tz);
        if (pig.distanceToSqr(tx, pig.getY(), tz) > 48 * 48) pig.teleportTo(tx, ty, tz);
        else pig.getNavigation().moveTo(tx, ty, tz, 1.2);
    }
}
