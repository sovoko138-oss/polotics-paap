package com.politics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NPC countries slowly build watchtowers along their border, one every fortSeconds seconds. */
public class Fortify {
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public static void tick(ServerLevel level, CountryData.Country c, long now) {
        List<int[]> sites = sites(c);
        if (c.fortBuilt >= sites.size()) return;
        Long last = LAST.get(c.id);
        if (last != null && now - last < Config.FORT_SECONDS.get() * 20L) return;
        int[] s = sites.get(c.fortBuilt);
        if (!level.hasChunk(s[0] >> 4, s[1] >> 4)) return;
        LAST.put(c.id, now);
        c.fortBuilt++;
        CountryData.get(level.getServer()).setDirty();
        build(level, c, s[0], s[1]);
    }

    /** One tower site per border chunk (a chunk that has a side facing outside the country), always the same order. */
    private static List<int[]> sites(CountryData.Country c) {
        List<Long> keys = new ArrayList<>(c.chunks);
        Collections.sort(keys);
        int capX = c.capitalPos == null ? Integer.MIN_VALUE : c.capitalPos.getX();
        int capZ = c.capitalPos == null ? Integer.MIN_VALUE : c.capitalPos.getZ();
        int[][] sides = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        List<int[]> out = new ArrayList<>();
        for (long k : keys) {
            int cx = CountryData.keyX(k), cz = CountryData.keyZ(k);
            for (int[] d : sides) {
                if (c.chunks.contains(CountryData.chunkKey(cx + d[0], cz + d[1]))) continue;
                int x = cx * 16 + 8 + d[0] * 4, z = cz * 16 + 8 + d[1] * 4; // 4 blocks from the middle, near the outer edge
                if (Math.max(Math.abs(x - capX), Math.abs(z - capZ)) < 8) break; // not on top of the capital block
                out.add(new int[]{x, z});
                break;
            }
        }
        return out;
    }

    /** A 3x3 tower, 8 blocks high, with a door on the south side, a ladder inside and a battlement on top. */
    private static void build(ServerLevel level, CountryData.Country c, int x, int z) {
        BlockState[] pal = NpcWorld.palette(WarManager.typeOf(c));
        BlockState wall = pal[0], trim = pal[1];
        BlockState air = Blocks.AIR.defaultBlockState();

        int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockState ground = level.getBlockState(new BlockPos(x, gy - 1, z));
        if (!ground.getFluidState().isEmpty() || ground.is(BlockTags.LEAVES) || !ground.blocksMotion()) return; // water or trees: skip this site

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean center = dx == 0 && dz == 0;
                boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                // foundation down to solid ground
                for (int dy = 1; dy <= 4; dy++) {
                    BlockPos fp = new BlockPos(x + dx, gy - dy, z + dz);
                    BlockState st = level.getBlockState(fp);
                    if (st.blocksMotion() && st.getFluidState().isEmpty()) break;
                    level.setBlock(fp, wall, 3);
                }
                for (int h = 0; h <= 6; h++) level.setBlock(new BlockPos(x + dx, gy + h, z + dz), center ? air : wall, 3);
                // battlement: corners and the north middle (it carries the top of the ladder)
                boolean merlon = corner || (dx == 0 && dz == -1);
                level.setBlock(new BlockPos(x + dx, gy + 7, z + dz), center ? air : (merlon ? trim : air), 3);
                level.setBlock(new BlockPos(x + dx, gy + 8, z + dz), corner ? Blocks.TORCH.defaultBlockState() : air, 3);
            }
        }
        level.setBlock(new BlockPos(x, gy, z + 1), air, 3);     // door
        level.setBlock(new BlockPos(x, gy + 1, z + 1), air, 3);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int h = 0; h <= 7; h++) level.setBlock(new BlockPos(x, gy + h, z), ladder, 3);
    }
}
