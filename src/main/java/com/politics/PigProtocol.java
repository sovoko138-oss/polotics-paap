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
        c.ensureCore();
        data.add(c, level.getRandom());
        c.hue = 0.92f; // розовый
        c.color = Mth.hsvToRgb(c.hue, 0.45f, 1f);

        // столица + ближайшие свободные чанки, всего CHUNKS
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new long[]{cx, cz});
        seen.add(CountryData.chunkKey(cx, cz));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty() && c.chunks.size() < CHUNKS) {
            long[] cur = queue.poll();
            for (int[] d : dirs) {
                int nx = (int) cur[0] + d[0], nz = (int) cur[1] + d[1];
                long key = CountryData.chunkKey(nx, nz);
                if (!seen.add(key)) continue;
                if (data.byChunk(c.dimension, nx, nz) != null) continue;
                data.claim(c, nx, nz);
                queue.add(new long[]{nx, nz});
                if (c.chunks.size() >= CHUNKS) break;
            }
        }
        data.setDirty();
        be.setUsed(true);
        CountryService.announceNewCountry(server, c);
    }
}
