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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
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
    /** Seconds left for every chunk that is being captured right now (for the table). */
    private static final Map<Long, Integer> ETA = new HashMap<>();

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
        FightManager.reset();
        Kingdom.reset();
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
            if (h.npc || h.chunks.size() < need || data.warOf(h) != null || data.allied(pigs, h)) continue;
            WarService.declare(server, data, pigs, h);
            return;
        }
    }

    // ------------------------------------------------------------------ the war itself

    private static List<CountryData.Country> countries(CountryData data, List<UUID> ids) {
        List<CountryData.Country> out = new ArrayList<>();
        for (UUID id : ids) {
            CountryData.Country c = data.byId(id);
            if (c != null) out.add(c);
        }
        return out;
    }

    private static Set<Long> chunksOf(List<CountryData.Country> side) {
        Set<Long> out = new HashSet<>();
        for (CountryData.Country c : side) out.addAll(c.chunks);
        return out;
    }

    private static CountryData.Country ownerIn(List<CountryData.Country> side, long key) {
        for (CountryData.Country c : side) if (c.chunks.contains(key)) return c;
        return null;
    }

    private static void processWar(MinecraftServer server, CountryData data, CountryData.War w, long now) {
        List<CountryData.Country> A = countries(data, w.attackers), B = countries(data, w.defenders);
        if (A.isEmpty() || B.isEmpty()) {
            data.wars().remove(w);
            data.setDirty();
            return;
        }
        CountryData.Country a = A.get(0), b = B.get(0); // the leaders
        List<CountryData.Country> all = new ArrayList<>(A);
        all.addAll(B);
        Set<UUID> sideA = new HashSet<>();
        for (CountryData.Country c : A) sideA.add(c.id);

        if (!w.active) {
            if (w.scheduledStartMs > 0L) {
                long remainingSeconds = Math.max(0L, (w.scheduledStartMs - System.currentTimeMillis()) / 1000L);
                if (System.currentTimeMillis() < w.scheduledStartMs) {
                    sendStatus(server, A, B, w, 1, (int) Math.min(Integer.MAX_VALUE, remainingSeconds));
                    return;
                }
            } else {
                long startAt = w.declaredAt + Config.WAR_PREP_SECONDS.get() * 20L;
                if (now < startAt) {
                    sendStatus(server, A, B, w, 1, (int) ((startAt - now) / 20));
                    return;
                }
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
                boolean involved = false;
                for (CountryData.Country c : all) if (c.isMember(nick)) involved = true;
                if (involved) {
                    PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), notice);
                    sp.playNotifySound(HORN, SoundSource.MASTER, 1.0f, 1.0f); // the pillagers' horn
                }
            }
        }

        // in a war between two NPC countries each side sends its fighters (see FightManager)
        boolean npc1v1 = A.size() == 1 && B.size() == 1 && a.npc && b.npc;
        if (npc1v1) FightManager.maintain(server, data, a, b);

        Map<Long, CountryData.Country> owners = new HashMap<>();
        for (CountryData.Country c : all) for (long k : c.chunks) owners.put(k, c);

        // who stands where this second
        Map<Long, Integer> hit = new HashMap<>();    // chunk -> how many are capturing (or taking it back) there
        Map<Long, UUID> hitBy = new HashMap<>();     // chunk -> the country of those who stand in it
        Set<Long> defended = new HashSet<>();        // chunks that have someone from the owner's side standing in them
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.isSpectator() || !sp.isAlive()) continue;
            String nick = sp.getGameProfile().getName();
            CountryData.Country mine = null;
            for (CountryData.Country c : all) if (c.isMember(nick)) { mine = c; break; }
            if (mine == null) continue;
            ChunkPos cp = sp.chunkPosition();
            long k = CountryData.chunkKey(cp.x, cp.z);
            CountryData.Country owner = owners.get(k);
            if (owner == null) continue;
            if (!sp.level().dimension().location().toString().equals(owner.dimension)) continue;
            boolean same = sideA.contains(owner.id) == sideA.contains(mine.id);
            boolean cap = w.captured.contains(k);
            if (!same && !cap) { hit.merge(k, 1, Integer::sum); hitBy.put(k, mine.id); }   // capturing an enemy chunk
            else if (same && cap) { hit.merge(k, 1, Integer::sum); hitBy.put(k, mine.id); } // taking back a lost chunk
            if (same && !cap) defended.add(k);
        }
        for (CountryData.Country n : all) {
            if (!n.npc) continue;
            ServerLevel level = levelOf(server, n);
            Map<UUID, Mission> ms = MISSIONS.get(n.id);
            if (level == null || ms == null) continue;
            for (Map.Entry<UUID, Mission> en : ms.entrySet()) {
                Entity ent = level.getEntity(en.getKey());
                if (!(ent instanceof Mob pig) || !pig.isAlive()) continue;
                ChunkPos pc = pig.chunkPosition();
                long k = CountryData.chunkKey(pc.x, pc.z);
                CountryData.Country owner = owners.get(k);
                if (owner == null || k != en.getValue().chunk) continue;
                boolean same = sideA.contains(owner.id) == sideA.contains(n.id);
                boolean cap = w.captured.contains(k);
                if ((!same && !cap) || (same && cap)) { hit.merge(k, 1, Integer::sum); hitBy.put(k, n.id); }
            }
        }
        if (npc1v1) FightManager.addHits(server, a, b, w, hit, defended);

        // someone of the owner's side standing in the chunk that is being captured pauses the capture
        hit.keySet().removeIf(k -> defended.contains(k) && !w.captured.contains(k));

        // progress: +1 % per second while someone stands there (+50 % faster once the owner's capital chunk is captured),
        // slowly back down otherwise. A captured chunk stays marked until its owner is defeated.
        ETA.clear();
        for (Map.Entry<Long, Integer> he : hit.entrySet()) {
            long k = he.getKey();
            CountryData.Country owner = owners.get(k);
            if (owner == null) continue;
            boolean ownerOnA = sideA.contains(owner.id);
            CountryData.Country taker = data.byId(hitBy.getOrDefault(k, (ownerOnA ? b : a).id));
            if (taker == null) taker = ownerOnA ? b : a;
            boolean isCapital = isCapitalChunk(owner, k);
            boolean retake = w.captured.contains(k); // a captured chunk is being taken back
            // one player needs 50 s (capital: 100 s); every extra player (up to 5) takes 5 s off
            int standing = Math.min(Config.CAPTURE_MAX_PLAYERS.get(), he.getValue());
            int seconds = Math.max(1, baseSeconds(isCapital) - (standing - 1) * Config.CAPTURE_SECONDS_PER_EXTRA.get());
            float speed = (!retake && !isCapital && capitalLost(owner, w)) ? 1.5f : 1f;
            float rate = 100f / seconds * speed; // % per second
            float p = w.progress.getOrDefault(k, 0f) + rate;
            ETA.put(k, (int) Math.ceil((100f - p) / rate));
            if (p >= 99.99f) {
                w.progress.remove(k);
                Component msg;
                if (retake) {
                    w.captured.remove(k);
                    w.takers.remove(k);
                    msg = Component.literal("Чанк " + CountryData.keyX(k) + ", " + CountryData.keyZ(k) + " страны ")
                            .append(WarService.coloured(owner)).append(Component.literal(" отбит."));
                } else {
                    w.captured.add(k);
                    w.takers.put(k, taker.id);
                    msg = Component.literal("Чанк " + CountryData.keyX(k) + ", " + CountryData.keyZ(k) + " страны ")
                            .append(WarService.coloured(owner)).append(Component.literal(" захвачен: "))
                            .append(WarService.coloured(taker)).append(Component.literal("."));
                }
                for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                    String nick = sp.getGameProfile().getName();
                    for (CountryData.Country c : all) if (c.isMember(nick)) { sp.sendSystemMessage(msg); break; }
                }
            } else {
                w.progress.put(k, p);
            }
        }
        Iterator<Map.Entry<Long, Float>> it = w.progress.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Float> en = it.next();
            if (hit.containsKey(en.getKey())) continue;
            CountryData.Country owner = owners.get(en.getKey());
            if (owner == null) { it.remove(); continue; }
            // the percent melts 50 % faster while someone of the owner's side stands in the chunk
            boolean guarded = defended.contains(en.getKey()) && !w.captured.contains(en.getKey());
            float p = en.getValue() - 100f / baseSeconds(isCapitalChunk(owner, en.getKey())) * (guarded ? 1.5f : 1f);
            if (p <= 0) it.remove(); else en.setValue(p);
        }
        data.setDirty();

        // a country is defeated when every one of its chunks is captured
        for (CountryData.Country x : all) {
            if (!x.chunks.isEmpty() && w.captured.containsAll(x.chunks)) {
                defeat(server, data, w, x, sideA.contains(x.id) ? b : a);
                return;
            }
        }
        sendStatus(server, A, B, w, 2, 0);
    }

    private static int baseSeconds(boolean capital) {
        return Math.max(1, capital ? Config.CAPITAL_CAPTURE_SECONDS.get() : Config.CHUNK_CAPTURE_SECONDS.get());
    }

    private static boolean isCapitalChunk(CountryData.Country c, long key) {
        return c.capitalPos != null && key == CountryData.chunkKey(c.capitalPos.getX() >> 4, c.capitalPos.getZ() >> 4);
    }

    /** True once the enemy has captured the chunk with the country's capital block. */
    private static boolean capitalLost(CountryData.Country c, CountryData.War w) {
        if (c.capitalPos == null) return false;
        return w.captured.contains(CountryData.chunkKey(c.capitalPos.getX() >> 4, c.capitalPos.getZ() >> 4));
    }

    /** The country has lost all its chunks: every chunk goes to the country that captured it, and the country is gone. */
    private static void defeat(MinecraftServer server, CountryData data, CountryData.War w,
                               CountryData.Country loser, CountryData.Country fallbackWinner) {
        // the main winner is the country that captured most of the loser's chunks
        Map<UUID, Integer> count = new HashMap<>();
        for (long k : loser.chunks) {
            UUID t = w.takers.get(k);
            if (t != null && !t.equals(loser.id)) count.merge(t, 1, Integer::sum);
        }
        CountryData.Country winner = fallbackWinner;
        int best = 0;
        for (Map.Entry<UUID, Integer> en : count.entrySet()) {
            CountryData.Country c = data.byId(en.getKey());
            if (c != null && en.getValue() > best) { best = en.getValue(); winner = c; }
        }
        final CountryData.Country win = winner;

        Component msg = Component.literal("Страна ").append(WarService.coloured(loser))
                .append(Component.literal(" проиграла в войне со страной ")).append(WarService.coloured(win))
                .append(Component.literal(", территории поверженной страны переходят на сторону врага."));
        String plain = "Страна " + loser.name + " проиграла в войне со страной " + win.name
                + ", территории поверженной страны переходят на сторону врага.";

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String nick = sp.getGameProfile().getName();
            boolean won = win.isMember(nick);
            if (won || loser.isMember(nick)) {
                PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                        new NoticePacket("Война окончена", plain, win.color));
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

        for (long k : new ArrayList<>(loser.chunks)) {
            CountryData.Country t = w.takers.containsKey(k) ? data.byId(w.takers.get(k)) : null;
            if (t == null || t == loser) t = win;
            t.chunks.add(k);
            w.captured.remove(k);
            w.takers.remove(k);
            w.progress.remove(k);
        }
        loser.chunks.clear();
        MISSIONS.remove(loser.id);
        FightManager.releaseCountry(server, loser.id);
        data.remove(loser); // the defeated country ceases to exist (the war ends if one side has nobody left)
        data.setDirty();
        BorderSync.last = Long.MIN_VALUE; // resend all borders on the next tick
    }

    private static void sendStatus(MinecraftServer server, List<CountryData.Country> A, List<CountryData.Country> B,
                                   CountryData.War w, int phase, int seconds) {
        WarStatusPacket forA = null, forB = null;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String nick = sp.getGameProfile().getName();
            boolean onA = false, onB = false;
            for (CountryData.Country c : A) if (c.isMember(nick)) onA = true;
            for (CountryData.Country c : B) if (c.isMember(nick)) onB = true;
            WarStatusPacket p;
            if (onA) { if (forA == null) forA = buildStatus(A, B, w, phase, seconds); p = forA; }
            else if (onB) { if (forB == null) forB = buildStatus(B, A, w, phase, seconds); p = forB; }
            else continue;
            PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), p);
        }
    }

    private static WarStatusPacket buildStatus(List<CountryData.Country> mine, List<CountryData.Country> enemy,
                                               CountryData.War w, int phase, int seconds) {
        Set<Long> mineChunks = chunksOf(mine), enemyChunks = chunksOf(enemy);
        int myTotal = Math.max(1, mineChunks.size()), enemyTotal = Math.max(1, enemyChunks.size());
        int myLost = 0, enemyLost = 0;
        for (long k : mineChunks) if (w.captured.contains(k)) myLost++;
        for (long k : enemyChunks) if (w.captured.contains(k)) enemyLost++;

        List<WarStatusPacket.Row> rows = new ArrayList<>();
        float myProgress = 0, enemyProgress = 0;
        Set<Long> retaking = new HashSet<>();
        for (Map.Entry<Long, Float> en : w.progress.entrySet()) {
            long k = en.getKey();
            boolean isMine = mineChunks.contains(k);
            boolean retake = w.captured.contains(k);
            float frac = en.getValue() / 100f;
            // capturing adds to the "country captured" bar, taking a chunk back takes from it
            float delta = retake ? -frac : frac;
            if (isMine) myProgress += delta; else enemyProgress += delta;
            if (retake) retaking.add(k);
            CountryData.Country owner = ownerIn(isMine ? mine : enemy, k);
            rows.add(new WarStatusPacket.Row(CountryData.keyX(k), CountryData.keyZ(k),
                    Math.min(99, Math.round(en.getValue())), isMine, retake,
                    owner != null && isCapitalChunk(owner, k), retake, ETA.getOrDefault(k, 0)));
        }
        rows.sort(Comparator.comparingInt((WarStatusPacket.Row r) -> -r.percent));
        for (long k : w.captured) {
            if (retaking.contains(k)) continue;
            boolean isMine = mineChunks.contains(k);
            CountryData.Country owner = ownerIn(isMine ? mine : enemy, k);
            rows.add(new WarStatusPacket.Row(CountryData.keyX(k), CountryData.keyZ(k), 100, isMine, true,
                    owner != null && isCapitalChunk(owner, k), false, 0));
        }
        if (rows.size() > 64) rows = new ArrayList<>(rows.subList(0, 64));
        int myPercent = Math.max(0, Math.min(100, Math.round((myLost + myProgress) * 100f / myTotal)));
        int enemyPercent = Math.max(0, Math.min(100, Math.round((enemyLost + enemyProgress) * 100f / enemyTotal)));
        StringBuilder names = new StringBuilder();
        for (CountryData.Country c : enemy) { if (names.length() > 0) names.append(", "); names.append(c.name); }
        return new WarStatusPacket(phase, seconds, names.toString(), enemy.get(0).color, mine.get(0).color,
                myTotal, myLost, enemyTotal, enemyLost, myPercent, enemyPercent, rows);
    }

    // ------------------------------------------------------------------ the pigs

    static ServerLevel levelOf(MinecraftServer server, CountryData.Country c) {
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

        final String tag = tagOf(c);
        Set<Mob> pigs = new LinkedHashSet<>(level.getEntitiesOfClass(Mob.class, box,
                p -> p.isAlive() && p.getTags().contains(tag)));
        Map<UUID, Mission> ms = MISSIONS.computeIfAbsent(c.id, k -> new HashMap<>());
        Iterator<Map.Entry<UUID, Mission>> mit = ms.entrySet().iterator();
        while (mit.hasNext()) {
            Entity ent = level.getEntity(mit.next().getKey());
            if (ent instanceof Mob p && p.isAlive()) pigs.add(p); else mit.remove();
        }

        CountryData.War war = data.warOf(c);
        Fortify.tick(level, c, now);
        Kingdom.tick(level, data, c, now);

        // fighters move on their own (FightManager), the others stay at home
        Set<Mob> home = new LinkedHashSet<>();
        for (Mob m : pigs) if (!FightManager.isFighter(m.getUUID())) home.add(m);
        pigs.addAll(FightManager.fightersOf(level, c));
        // every inhabitant that dies comes back next to the capital block
        if (pigs.size() < NpcWorld.target(c) && territoryLoaded(level, c)) spawnMob(level, c, now);

        List<CountryData.Country> ownSide = new ArrayList<>(), enemySide = new ArrayList<>();
        if (war != null) {
            boolean onA = war.onAttackers(c.id);
            ownSide = countries(data, onA ? war.attackers : war.defenders);
            enemySide = countries(data, onA ? war.defenders : war.attackers);
        }
        boolean fighting = war != null && war.active && !enemySide.isEmpty();

        if (!fighting) {
            ms.clear();
        } else {
            final Set<Long> enemyChunks = chunksOf(enemySide), ownChunks = chunksOf(ownSide);
            // between two NPC countries only the fighters attack; the residents only take their land back
            boolean fightersOnly = c.npc && ownSide.size() == 1 && enemySide.size() == 1 && enemySide.get(0).npc;
            final CountryData.War fw = war;
            ms.values().removeIf(m -> !((enemyChunks.contains(m.chunk) && !fw.captured.contains(m.chunk))
                    || (ownChunks.contains(m.chunk) && fw.captured.contains(m.chunk))));
            Set<Long> taken = new HashSet<>();
            for (Mission m : ms.values()) taken.add(m.chunk);
            for (Mob pig : home) {
                if (ms.size() >= ARMY) break;
                if (ms.containsKey(pig.getUUID())) continue;
                ChunkPos pc = pig.chunkPosition();
                long best = 0;
                double bestD = Double.MAX_VALUE;
                boolean found = false;
                List<Long> cands = new ArrayList<>();
                if (!fightersOnly) for (long k : enemyChunks) if (!war.captured.contains(k)) cands.add(k);   // enemy chunks to capture
                for (long k : ownChunks) if (war.captured.contains(k)) cands.add(k);                         // lost chunks to take back
                for (long k : cands) {
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

        for (Mob pig : home) {
            Mission m = ms.get(pig.getUUID());
            if (m != null) goToChunk(level, pig, m);
            else keepInside(level, pig, c);
        }
    }

    /** "pig" (default) or "creeper": what the inhabitants and the army of an NPC country are. */
    public static String typeOf(CountryData.Country c) {
        return c.npcType == null || c.npcType.isEmpty() ? "pig" : c.npcType;
    }

    /** The entity tag that marks the inhabitants of a country ("politics_pig" for the pigs, as before). */
    public static String tagOf(CountryData.Country c) {
        return "politics_" + typeOf(c);
    }

    public static Mob createMob(ServerLevel level, CountryData.Country c) {
        net.minecraft.world.entity.EntityType<? extends Mob> type = switch (typeOf(c)) {
            case "creeper" -> EntityType.CREEPER;
            case "villager" -> EntityType.VILLAGER;
            case "wither_skeleton" -> EntityType.WITHER_SKELETON;
            case "piglin" -> EntityType.PIGLIN;
            default -> EntityType.PIG;
        };
        Mob mob = type.create(level);
        if (mob != null) {
            mob.addTag(tagOf(c));
            mob.setPersistenceRequired();
            // piglins would turn into zombified piglins in the overworld
            if (mob instanceof net.minecraft.world.entity.monster.piglin.Piglin pg) pg.setImmuneToZombification(true);
        }
        return mob;
    }

    static boolean territoryLoaded(ServerLevel level, CountryData.Country c) {
        for (long k : c.chunks) if (!level.hasChunk(CountryData.keyX(k), CountryData.keyZ(k))) return false;
        return true;
    }

    private static void spawnMob(ServerLevel level, CountryData.Country c, long now) {
        if (c.capitalPos == null || !level.isLoaded(c.capitalPos)) return;
        Long last = LAST_SPAWN.get(c.id);
        if (last != null && now - last < 400) return;
        LAST_SPAWN.put(c.id, now);
        Mob mob = createMob(level, c);
        if (mob == null) return;
        net.minecraft.util.RandomSource r = level.getRandom();
        int mx = c.capitalPos.getX() + r.nextInt(9) - 4, mz = c.capitalPos.getZ() + r.nextInt(9) - 4;
        int my = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mx, mz);
        mob.moveTo(mx + 0.5, my, mz + 0.5, r.nextFloat() * 360f, 0f);
        level.addFreshEntity(mob);
    }

    /** An army pig walks into the target chunk and stands there; if it is stuck for too long it is carried over. */
    private static void goToChunk(ServerLevel level, Mob pig, Mission m) {
        int cx = CountryData.keyX(m.chunk), cz = CountryData.keyZ(m.chunk);
        if (!level.hasChunk(cx, cz)) return;
        m.age++;
        double tx = cx * 16 + 8.5, tz = cz * 16 + 8.5;
        double ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) tx, (int) tz);
        ChunkPos pc = pig.chunkPosition();
        boolean inside = pc.x == cx && pc.z == cz;
        if (!inside) {
            if (m.age > (Walk.isBrainMob(pig) ? 15 : 45)) pig.teleportTo(tx, ty, tz);
            else Walk.to(pig, tx, ty, tz, 1.3);
        } else if (pig.distanceToSqr(tx, pig.getY(), tz) > 36) {
            Walk.to(pig, tx, ty, tz, 1.0);
        }
    }

    /** The pigs of the pig country walk only inside their own territory. */
    private static void keepInside(ServerLevel level, Mob pig, CountryData.Country c) {
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
        else Walk.to(pig, tx, ty, tz, 1.2);
    }
}
