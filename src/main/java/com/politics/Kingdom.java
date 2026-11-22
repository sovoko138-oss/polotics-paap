package com.politics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The life of an NPC kingdom in the single-player mode: from time to time it has a child,
 * takes one more chunk and builds a new house or stall. (Their own AI comes later.)
 */
public class Kingdom {
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    public static void tick(ServerLevel level, CountryData data, CountryData.Country c, long now) {
        if (!level.getServer().isSingleplayer() || !Config.SOLO_KINGDOMS.get()) return;
        Long next = NEXT.get(c.id);
        long interval = 20L * Config.KINGDOM_CHECK_SECONDS.get();
        if (next == null) { NEXT.put(c.id, now + interval); return; }
        if (now < next) return;
        NEXT.put(c.id, now + interval);

        RandomSource r = level.getRandom();
        if (r.nextFloat() < 0.35f) birth(level, data, c);
        if (r.nextFloat() < 0.15f) expand(level, data, c);
        if (r.nextFloat() < 0.30f) build(level, data, c);
    }

    public static void reset() { NEXT.clear(); }

    // ------------------------------------------------------------------ children

    private static void birth(ServerLevel level, CountryData data, CountryData.Country c) {
        if (c.capitalPos == null || !level.isLoaded(c.capitalPos) || !WarManager.territoryLoaded(level, c)) return;
        int pop = NpcWorld.target(c);
        if (pop >= Math.max(NpcWorld.RESIDENTS, 3 * c.chunks.size())) return; // the land feeds only so many
        Mob baby = WarManager.createMob(level, c);
        if (baby == null) return;
        baby.setBaby(true); // creepers and wither skeletons have no baby form: they are born grown
        RandomSource r = level.getRandom();
        int x = c.capitalPos.getX() + r.nextInt(9) - 4, z = c.capitalPos.getZ() + r.nextInt(9) - 4;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        baby.moveTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360f, 0f);
        level.addFreshEntity(baby);
        c.population = pop + 1;
        c.addResident(NpcWorld.baseName(WarManager.typeOf(c)) + " " + (c.residents.size() + 1));
        data.setDirty();
    }

    // ------------------------------------------------------------------ land

    /** One more free chunk next to the territory, the nearest to the capital. */
    private static void expand(ServerLevel level, CountryData data, CountryData.Country c) {
        if (data.warOf(c) != null || c.capitalPos == null) return;
        if (c.chunks.size() >= Config.KINGDOM_MAX_CHUNKS.get()) return;
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

    // ------------------------------------------------------------------ houses

    private static void build(ServerLevel level, CountryData data, CountryData.Country c) {
        if (c.capitalPos == null || c.buildings >= 3 * c.chunks.size()) return;
        List<Long> chunks = new ArrayList<>(c.chunks);
        RandomSource r = level.getRandom();
        for (int attempt = 0; attempt < 10; attempt++) {
            long k = chunks.get(r.nextInt(chunks.size()));
            int cx = CountryData.keyX(k), cz = CountryData.keyZ(k);
            if (!level.hasChunk(cx, cz)) continue;
            int x = cx * 16 + 3 + r.nextInt(10), z = cz * 16 + 3 + r.nextInt(10); // the whole 5x5 stays inside the chunk
            if (Math.max(Math.abs(x - c.capitalPos.getX()), Math.abs(z - c.capitalPos.getZ())) < 7) continue;
            boolean stall = r.nextInt(3) == 0;
            if (placeBuilding(level, c, x, z, stall)) {
                c.buildings++;
                data.setDirty();
                return;
            }
        }
    }

    private static boolean natural(BlockState ground) {
        return ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(BlockTags.BASE_STONE_OVERWORLD);
    }

    /** Flat natural ground and free space, otherwise the site is refused. */
    private static boolean placeBuilding(ServerLevel level, CountryData.Country c, int x, int z, boolean stall) {
        int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int[][] probes = {{0, 0}, {-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] p : probes) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + p[0], z + p[1]);
            if (Math.abs(h - gy) > 1) return false;
            BlockState ground = level.getBlockState(new BlockPos(x + p[0], h - 1, z + p[1]));
            if (!ground.getFluidState().isEmpty() || !natural(ground)) return false;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int h = 0; h <= 3; h++) {
                    BlockState st = level.getBlockState(new BlockPos(x + dx, gy + h, z + dz));
                    if (!st.isAir() && !st.canBeReplaced() && !st.is(BlockTags.LEAVES)) return false;
                }
            }
        }
        BlockState[] pal = NpcWorld.palette(WarManager.typeOf(c));
        if (stall) buildStall(level, x, z, gy, pal); else buildHouse(level, x, z, gy, pal);
        return true;
    }

    private static void foundation(ServerLevel level, int x, int z, int gy, BlockState block) {
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos fp = new BlockPos(x, gy - dy, z);
            BlockState st = level.getBlockState(fp);
            if (st.blocksMotion() && st.getFluidState().isEmpty()) break;
            level.setBlock(fp, block, 3);
        }
    }

    /** A 5x5 house: walls 3 high, corner posts, windows, a door and a flat roof. */
    private static void buildHouse(ServerLevel level, int x, int z, int gy, BlockState[] pal) {
        BlockState wall = pal[0], trim = pal[1], air = Blocks.AIR.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                foundation(level, x + dx, z + dz, gy, wall);
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                for (int h = 0; h <= 2; h++) {
                    BlockState b = !edge ? air : (corner ? trim : wall);
                    level.setBlock(new BlockPos(x + dx, gy + h, z + dz), b, 3);
                }
                level.setBlock(new BlockPos(x + dx, gy + 3, z + dz), trim, 3); // roof
            }
        }
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        level.setBlock(new BlockPos(x - 2, gy + 1, z), pane, 3);
        level.setBlock(new BlockPos(x + 2, gy + 1, z), pane, 3);
        level.setBlock(new BlockPos(x, gy + 1, z - 2), pane, 3);
        level.setBlock(new BlockPos(x, gy, z + 2), air, 3);     // door opening on the south side
        level.setBlock(new BlockPos(x, gy + 1, z + 2), air, 3);
        level.setBlock(new BlockPos(x - 1, gy, z - 1), Blocks.TORCH.defaultBlockState(), 3);
    }

    /** A 3x3 market stall: four posts and a roof. */
    private static void buildStall(ServerLevel level, int x, int z, int gy, BlockState[] pal) {
        BlockState post = Blocks.OAK_FENCE.defaultBlockState(), roof = pal[1], air = Blocks.AIR.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                foundation(level, x + dx, z + dz, gy, pal[0]);
                boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                level.setBlock(new BlockPos(x + dx, gy, z + dz), corner ? post : air, 3);
                level.setBlock(new BlockPos(x + dx, gy + 1, z + dz), corner ? post : air, 3);
                level.setBlock(new BlockPos(x + dx, gy + 2, z + dz), roof, 3);
            }
        }
        level.setBlock(new BlockPos(x, gy, z), Blocks.BARREL.defaultBlockState(), 3);
    }
}
