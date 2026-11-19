package com.politics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The two start countries: Свиностан (pigs) and Криперляндия (creepers that never explode).
 * They appear by themselves 3-4 chunks from the player when a player first joins a world,
 * each with 8 inhabitants and 8 chunks.
 */
public class NpcWorld {
    public static final String CREEPER_NAME = "Криперляндия";
    public static final String CREEPER_CAPITAL = "Криперск";
    public static final String CREEPER_PRESIDENT = "Главный Крипер";
    public static final int RESIDENTS = 8;
    public static final int CHUNKS = 8;

    private static final Logger LOG = LoggerFactory.getLogger("politics");

    /** Chunk offsets from the player, 3-4 chunks away, in the order they are tried. */
    private static final int[][] OFFSETS = {
            {4, 0}, {-4, 0}, {0, 4}, {0, -4}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3},
            {4, 2}, {-4, 2}, {4, -2}, {-4, -2}, {2, 4}, {-2, 4}, {2, -4}, {-2, -4},
            {4, 1}, {-4, 1}, {4, -1}, {-4, -1}, {1, 4}, {-1, 4}, {1, -4}, {-1, -4}};

    public static void ensure(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CountryData data = CountryData.get(server);
        if (data.npcWorldDone()) return;
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) return;
        try {
            int pcx = player.chunkPosition().x, pcz = player.chunkPosition().z;
            List<int[]> placed = new ArrayList<>();
            CountryData.Country pigs = data.byName(PigProtocol.NAME);
            CountryData.Country creepers = data.byName(CREEPER_NAME);
            if (pigs != null && pigs.capitalPos != null) placed.add(new int[]{pigs.capitalPos.getX() >> 4, pigs.capitalPos.getZ() >> 4});
            if (creepers != null && creepers.capitalPos != null) placed.add(new int[]{creepers.capitalPos.getX() >> 4, creepers.capitalPos.getZ() >> 4});

            if (pigs == null) {
                int[] spot = pick(level, data, pcx, pcz, placed);
                if (spot != null) {
                    create(server, level, data, spot, PigProtocol.NAME, PigProtocol.CAPITAL, PigProtocol.PRESIDENT,
                            "pig", "minecraft:pink_banner", Mth.hsvToRgb(0.92f, 0.45f, 1f));
                    placed.add(spot);
                }
            }
            if (creepers == null) {
                int[] spot = pick(level, data, pcx, pcz, placed);
                if (spot != null) {
                    create(server, level, data, spot, CREEPER_NAME, CREEPER_CAPITAL, CREEPER_PRESIDENT,
                            "creeper", "minecraft:lime_banner", 0x55CC55);
                    placed.add(spot);
                }
            }

            CountryData.Country p2 = data.byName(PigProtocol.NAME), c2 = data.byName(CREEPER_NAME);
            if (p2 != null && c2 != null) {
                if (p2.npc) populate(level, p2); // a Свиностан from before gets its 8 inhabitants too
                data.setNpcWorldDone(true);
            }
        } catch (Throwable t) {
            LOG.warn("[politics] не удалось создать стартовые страны", t);
        }
    }

    /** A free chunk 3-4 chunks from the player, on dry land, not too close to another start country. */
    private static int[] pick(ServerLevel level, CountryData data, int pcx, int pcz, List<int[]> placed) {
        String dim = level.dimension().location().toString();
        for (int[] o : OFFSETS) {
            int cx = pcx + o[0], cz = pcz + o[1];
            if (data.byChunk(dim, cx, cz) != null) continue;
            boolean near = false;
            for (int[] p : placed) if (Math.max(Math.abs(p[0] - cx), Math.abs(p[1] - cz)) < 5) near = true;
            if (near) continue;
            int x = cx * 16 + 8, z = cz * 16 + 8;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockState ground = level.getBlockState(new BlockPos(x, y - 1, z));
            if (!ground.getFluidState().isEmpty() || !ground.blocksMotion() || ground.is(BlockTags.LEAVES)) continue;
            return new int[]{cx, cz};
        }
        return null;
    }

    private static void create(MinecraftServer server, ServerLevel level, CountryData data, int[] spot, String name,
                               String capital, String president, String type, String flag, int color) {
        int x = spot[0] * 16 + 8, z = spot[1] * 16 + 8;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);

        level.setBlock(pos, ModBlocks.CAPITAL_BLOCK.get().defaultBlockState(), 3);

        CountryData.Country c = new CountryData.Country();
        c.name = name;
        c.capitalName = capital;
        c.presidentId = java.util.UUID.nameUUIDFromBytes(("politics:" + type).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        c.presidentName = president;
        c.flagItem = flag;
        c.cityFlag = flag;
        c.mayor = president;
        c.dimension = level.dimension().location().toString();
        c.capitalPos = pos;
        c.npc = true;
        c.npcType = type;
        c.ensureCore();
        data.add(c, level.getRandom());
        c.color = color & 0xFFFFFF;
        c.hue = java.awt.Color.RGBtoHSB(c.color >> 16 & 0xFF, c.color >> 8 & 0xFF, c.color & 0xFF, null)[0];

        // the capital chunk and the nearest free chunks, CHUNKS in all
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new int[]{spot[0], spot[1]});
        seen.add(CountryData.chunkKey(spot[0], spot[1]));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty() && c.chunks.size() < CHUNKS) {
            int[] cur = queue.poll();
            for (int[] d : dirs) {
                int nx = cur[0] + d[0], nz = cur[1] + d[1];
                if (!seen.add(CountryData.chunkKey(nx, nz))) continue;
                if (data.byChunk(c.dimension, nx, nz) != null) continue;
                data.claim(c, nx, nz);
                queue.add(new int[]{nx, nz});
                if (c.chunks.size() >= CHUNKS) break;
            }
        }

        if (level.getBlockEntity(pos) instanceof CapitalBlockEntity be) {
            be.setOwner(c.presidentId, c.presidentName);
            be.setUsed(true);
        }
        populate(level, c);
        data.setDirty();
        CountryService.announceNewCountry(server, c);
    }

    /** Makes sure the country has RESIDENTS residents on the list and RESIDENTS mobs standing around its capital. */
    public static void populate(ServerLevel level, CountryData.Country c) {
        String base = WarManager.typeOf(c).equals("creeper") ? "Крипер" : "Хряк";
        for (int i = 1; c.residents.size() < RESIDENTS && i < 1000; i++) c.addResident(base + " " + i);

        if (c.capitalPos == null || !level.isLoaded(c.capitalPos)) return;
        final String tag = WarManager.tagOf(c);
        AABB box = new AABB(c.capitalPos).inflate(56, 80, 56);
        int have = level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.getTags().contains(tag)).size();
        RandomSource rnd = level.getRandom();
        for (int i = have; i < RESIDENTS; i++) {
            Mob mob = WarManager.createMob(level, c);
            if (mob == null) return;
            int mx = c.capitalPos.getX() + rnd.nextInt(13) - 6;
            int mz = c.capitalPos.getZ() + rnd.nextInt(13) - 6;
            int my = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mx, mz);
            mob.moveTo(mx + 0.5, my, mz + 0.5, rnd.nextFloat() * 360f, 0f);
            level.addFreshEntity(mob);
        }
    }
}
