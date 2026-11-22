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
import net.minecraft.world.level.block.Blocks;
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
    public static final String CREEPER_CAPITAL = "Кабул";
    public static final String CREEPER_PRESIDENT = "Папа арменина";
    public static final int RESIDENTS = 8;
    public static final int CHUNKS = 8;

    /** The five kingdoms of the single-player mode: type, name, capital, president, flag. */
    private static final String[][] KINGDOMS = {
            {"pig", PigProtocol.NAME, PigProtocol.CAPITAL, PigProtocol.PRESIDENT, "minecraft:pink_banner"},
            {"creeper", CREEPER_NAME, CREEPER_CAPITAL, CREEPER_PRESIDENT, "minecraft:lime_banner"},
            {"villager", "Селяндия", "Селянск", "Староста", "minecraft:brown_banner"},
            {"wither_skeleton", "Иссушерия", "Иссушград", "Главный Иссушитель", "minecraft:black_banner"},
            {"piglin", "Пиглинстан", "Пиглинск", "Главный Пиглин", "minecraft:orange_banner"}};

    private static int colorOf(String type) {
        return switch (type) {
            case "creeper" -> 0x55CC55;
            case "villager" -> 0xC99A5B;
            case "wither_skeleton" -> 0x6E6E6E;
            case "piglin" -> 0xE6A23C;
            default -> Mth.hsvToRgb(0.92f, 0.45f, 1f);
        };
    }

    /** The name part of the residents of each kingdom. */
    public static String baseName(String type) {
        return switch (type) {
            case "creeper" -> "Крипер";
            case "villager" -> "Селянин";
            case "wither_skeleton" -> "Иссушитель";
            case "piglin" -> "Пиглин";
            default -> "Хряк";
        };
    }

    /** Wall and trim blocks of the buildings of each kingdom. */
    public static BlockState[] palette(String type) {
        return switch (type) {
            case "creeper" -> new BlockState[]{Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState()};
            case "villager" -> new BlockState[]{Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState()};
            case "wither_skeleton" -> new BlockState[]{Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState()};
            case "piglin" -> new BlockState[]{Blocks.BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()};
            default -> new BlockState[]{Blocks.COBBLESTONE.defaultBlockState(), Blocks.BRICKS.defaultBlockState()};
        };
    }

    /** How many inhabitants the country wants: 8 at the start, more with every birth. */
    public static int target(CountryData.Country c) { return Math.max(RESIDENTS, c.population); }

    private static final Logger LOG = LoggerFactory.getLogger("politics");

    /** Chunk offsets from the player, 3-4 chunks away, in the order they are tried. */
    private static final int[][] OFFSETS = {
            {4, 0}, {-4, 0}, {0, 4}, {0, -4}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3},
            {4, 2}, {-4, 2}, {4, -2}, {-4, -2}, {2, 4}, {-2, 4}, {2, -4}, {-2, -4},
            {4, 1}, {-4, 1}, {4, -1}, {-4, -1}, {1, 4}, {-1, 4}, {1, -4}, {-1, -4}};

    /** A Криперляндия from an older build gets the new capital name and president. */
    private static void migrate(CountryData data) {
        CountryData.Country k = data.byName(CREEPER_NAME);
        if (k == null || !k.npc) return;
        boolean changed = false;
        if (!CREEPER_CAPITAL.equals(k.capitalName)) { k.capitalName = CREEPER_CAPITAL; changed = true; }
        if (!CREEPER_PRESIDENT.equals(k.presidentName)) {
            CountryData.Resident r = k.findResident(k.presidentName);
            if (r != null) r.name = CREEPER_PRESIDENT;
            k.presidentName = CREEPER_PRESIDENT;
            k.mayor = CREEPER_PRESIDENT;
            changed = true;
        }
        if (changed) data.setDirty();
    }

    public static void ensure(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CountryData data = CountryData.get(server);
        migrate(data);
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) return;
        if (server.isSingleplayer() && Config.SOLO_KINGDOMS.get()) { // single-player mode: five kingdoms
            try {
                ensureSolo(player, server, data, level);
            } catch (Throwable t) {
                LOG.warn("[politics] не удалось создать королевства одиночного режима", t);
            }
            return;
        }
        if (data.npcWorldDone()) return;
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

    private static void ensureSolo(ServerPlayer player, MinecraftServer server, CountryData data, ServerLevel level) {
        if (data.soloDone()) return;
        int pcx = player.chunkPosition().x, pcz = player.chunkPosition().z;
        List<int[]> placed = new ArrayList<>();
        for (CountryData.Country c : data.all()) {
            if (c.npc && c.capitalPos != null) placed.add(new int[]{c.capitalPos.getX() >> 4, c.capitalPos.getZ() >> 4});
        }
        for (int i = 0; i < KINGDOMS.length; i++) {
            String[] k = KINGDOMS[i];
            if (data.byName(k[1]) != null) continue;
            int[] spot = pickRing(level, data, pcx, pcz, placed, i);
            if (spot == null) continue;
            create(server, level, data, spot, k[1], k[2], k[3], k[0], k[4], colorOf(k[0]));
            placed.add(spot);
        }
        boolean all = true;
        for (String[] k : KINGDOMS) {
            CountryData.Country c = data.byName(k[1]);
            if (c == null) { all = false; continue; }
            if (c.npc) populate(level, c); // kingdoms from before get their 8 inhabitants too
        }
        if (all) data.setSoloDone(true);
    }

    /** A spot about 5-8 chunks from the player, every kingdom in its own direction. */
    private static int[] pickRing(ServerLevel level, CountryData data, int pcx, int pcz, List<int[]> placed, int index) {
        String dim = level.dimension().location().toString();
        int[] radii = {6, 7, 5, 8};
        for (int step = 0; step < 15; step++) {
            double ang = Math.toRadians(index * 72.0 + step * 24.0);
            for (int r : radii) {
                int cx = pcx + (int) Math.round(r * Math.cos(ang)), cz = pcz + (int) Math.round(r * Math.sin(ang));
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
        }
        return null;
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
        String base = baseName(WarManager.typeOf(c));
        int want = target(c);
        for (int i = 1; c.residents.size() < want && i < 1000; i++) c.addResident(base + " " + i);

        if (c.capitalPos == null || !level.isLoaded(c.capitalPos)) return;
        final String tag = WarManager.tagOf(c);
        AABB box = new AABB(c.capitalPos).inflate(56, 80, 56);
        int have = level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.getTags().contains(tag)).size();
        RandomSource rnd = level.getRandom();
        for (int i = have; i < want; i++) {
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
