package com.politics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Протокол войны": когда рядом со свободным блоком столицы стоят свиньи, они основывают
 * страну Свиностан со столицей Свинск и забирают CHUNKS чанков рядом.
 */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID)
public class PigProtocol {
    public static final String NAME = "Свиностан";
    public static final String CAPITAL = "Свинск";
    public static final String PRESIDENT = "Главный Хряк";
    public static final UUID PRESIDENT_ID = UUID.nameUUIDFromBytes("politics:pigs".getBytes(StandardCharsets.UTF_8));
    /** Территория свиней вместе с чанком столицы. */
    public static final int CHUNKS = 8;

    private static final Set<GlobalPos> CAPITALS = ConcurrentHashMap.newKeySet();
    private static int counter = 0;

    public static void register(ResourceKey<Level> dim, BlockPos pos) { CAPITALS.add(GlobalPos.of(dim, pos)); }

    public static void unregister(ResourceKey<Level> dim, BlockPos pos) { CAPITALS.remove(GlobalPos.of(dim, pos)); }

    public static void reset() { CAPITALS.clear(); counter = 0; seconds = 0; }

    private static int seconds = 0;

    /** Once per second: the pigs take one more free chunk next to their territory, up to CHUNKS. */
    public static void growTick(MinecraftServer server, CountryData data) {
        CountryData.Country c = data.byName(NAME);
        if (c == null || !c.npc || c.chunks.size() >= CHUNKS) return;
        if (data.warOf(c) != null) return;
        if (++seconds % Config.PIG_CLAIM_SECONDS.get() != 0) return;

        int capX = c.capitalPos.getX() >> 4, capZ = c.capitalPos.getZ() >> 4;
        long best = 0;
        long bestDist = Long.MAX_VALUE;
        boolean found = false;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (long k : c.chunks) {
            for (int[] d : dirs) {
                int nx = CountryData.keyX(k) + d[0], nz = CountryData.keyZ(k) + d[1];
                if (data.byChunk(c.dimension, nx, nz) != null) continue;
                long dist = (long) (nx - capX) * (nx - capX) + (long) (nz - capZ) * (nz - capZ);
                if (dist < bestDist) { bestDist = dist; best = CountryData.chunkKey(nx, nz); found = true; }
            }
        }
        if (found) data.claim(c, CountryData.keyX(best), CountryData.keyZ(best));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (++counter % 20 != 0) return;
        if (CAPITALS.isEmpty()) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        CountryData data = CountryData.get(server);
        if (data.byName(NAME) != null) return; // свиньи уже основали страну

        int need = Config.PIGS_REQUIRED.get();
        for (GlobalPos gp : new ArrayList<>(CAPITALS)) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null) continue;
            BlockPos pos = gp.pos();
            if (!level.isLoaded(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof CapitalBlockEntity be) || be.isUsed()) continue;

            AABB area = new AABB(pos.getX() - 3, pos.getY() - 2, pos.getZ() - 3,
                    pos.getX() + 4, pos.getY() + 3, pos.getZ() + 4);
            int pigs = level.getEntitiesOfClass(Pig.class, area, Pig::isAlive).size();
            if (pigs >= need) {
                form(server, level, pos, be, data);
                return;
            }
        }
    }

    private static void form(MinecraftServer server, ServerLevel level, BlockPos pos,
                             CapitalBlockEntity be, CountryData data) {
        CountryData.Country c = new CountryData.Country();
        c.name = NAME;
        c.capitalName = CAPITAL;
        c.presidentId = PRESIDENT_ID;
        c.presidentName = PRESIDENT;
        c.flagItem = "minecraft:pink_banner";
        c.cityFlag = "minecraft:pink_banner";
        c.mayor = PRESIDENT;
        c.dimension = level.dimension().location().toString();
        c.capitalPos = pos;
        c.npc = true;
        c.npcType = "pig";
        c.ensureCore();
        data.add(c, level.getRandom());
        c.hue = 0.92f; // розовый
        c.color = Mth.hsvToRgb(c.hue, 0.45f, 1f);

        // свиньи, стоявшие у блока, становятся армией Свиностана
        AABB box = new AABB(pos.getX() - 3, pos.getY() - 2, pos.getZ() - 3,
                pos.getX() + 4, pos.getY() + 3, pos.getZ() + 4);
        for (Pig pig : level.getEntitiesOfClass(Pig.class, box, Pig::isAlive)) {
            pig.addTag(WarManager.TAG);
            pig.setPersistenceRequired();
        }
        NpcWorld.populate(level, c);
        data.setDirty();
        be.setUsed(true);
        CountryService.announceNewCountry(server, c);
    }
}
