package com.politics.client;

import com.politics.CountryData;
import com.politics.PoliticsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * What the player has seen: the colour of the top block of every chunk that was ever loaded on this client.
 * It is remembered in a file, so the map keeps growing between sessions.
 */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID, value = Dist.CLIENT)
public class MapCache {
    public static class Tile {
        public final int[] px = new int[256]; // final ABGR colours of the 16x16 blocks (0 = nothing)
        public long time;
    }

    private static final Logger LOG = LoggerFactory.getLogger("politics");
    private static final Map<String, Map<Long, Tile>> DATA = new HashMap<>();
    private static String worldId = null;
    private static boolean dirty = false;
    private static long lastSave = 0;
    private static int counter = 0;

    public static Map<Long, Tile> tiles(String dimension) {
        return DATA.computeIfAbsent(dimension, k -> new HashMap<>());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (worldId == null) load(mc);
        if (++counter % 5 != 0) return;
        try {
            scan(mc);
        } catch (Throwable t) {
            LOG.warn("[politics] ошибка сканирования карты", t);
        }
        if (dirty && System.currentTimeMillis() - lastSave > 30000) save();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        if (worldId != null && dirty) save();
        DATA.clear();
        worldId = null;
        dirty = false;
    }

    // ------------------------------------------------------------------ scanning

    private static void scan(Minecraft mc) {
        ClientLevel level = mc.level;
        String dim = level.dimension().location().toString();
        Map<Long, Tile> tiles = tiles(dim);
        int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z;
        int r = Math.min(mc.options.renderDistance().get(), 16);
        long now = System.currentTimeMillis();
        int budget = 12, refresh = 2;

        for (int d = 0; d <= r && (budget > 0 || refresh > 0); d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue;
                    int cx = pcx + dx, cz = pcz + dz;
                    long k = CountryData.chunkKey(cx, cz);
                    Tile t = tiles.get(k);
                    if (t == null) {
                        if (budget <= 0) continue;
                        Tile nt = scanChunk(mc, level, cx, cz);
                        if (nt != null) { tiles.put(k, nt); budget--; dirty = true; }
                    } else if (d <= 3 && refresh > 0 && now - t.time > 5000) {
                        if (scanChunk(mc, level, cx, cz, t)) { refresh--; dirty = true; }
                    }
                }
            }
        }
    }

    private static Tile scanChunk(Minecraft mc, ClientLevel level, int cx, int cz) {
        Tile t = new Tile();
        return scanChunk(mc, level, cx, cz, t) ? t : null;
    }

    private static boolean scanChunk(Minecraft mc, ClientLevel level, int cx, int cz, Tile out) {
        ChunkAccess ca = level.getChunk(cx, cz, ChunkStatus.FULL, false);
        if (ca == null) return false;
        boolean ceiling = level.dimensionType().hasCeiling();
        int minY = level.getMinBuildHeight();
        int playerY = (int) mc.player.getY();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int[] ys = new int[256];
        MapColor[] colors = new MapColor[256];
        int[] grass = new int[256]; // biome grass colour (RGB) for grass blocks, or -1
        boolean[] water = new boolean[256];
        int[] depth = new int[256];

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = cx * 16 + x, wz = cz * 16 + z;
                int top = ca.getHeight(Heightmap.Types.WORLD_SURFACE, x, z); // already the y of the top block
                int y = top;
                if (ceiling) {
                    y = Math.min(playerY, top);
                    while (y < top && !ca.getBlockState(mp.set(wx, y, wz)).isAir()) y++;
                    while (y > minY && ca.getBlockState(mp.set(wx, y, wz)).isAir()) y--;
                }
                BlockState st = ca.getBlockState(mp.set(wx, y, wz));
                // plants (grass, ferns, flowers, crops...) are not part of the ground: look at what is under them
                while (y > minY && (st.isAir() || st.getBlock() instanceof BushBlock || st.getMapColor(level, mp) == MapColor.NONE)) {
                    y--;
                    st = ca.getBlockState(mp.set(wx, y, wz));
                }
                int i = x + z * 16;
                ys[i] = y;
                grass[i] = -1;
                if (st.getFluidState().is(FluidTags.WATER)) {
                    water[i] = true;
                    int yy = y, dep = 0;
                    while (yy > minY && ca.getBlockState(mp.set(wx, yy, wz)).getFluidState().is(FluidTags.WATER)) { dep++; yy--; }
                    depth[i] = dep;
                    colors[i] = MapColor.WATER;
                } else {
                    colors[i] = st.getMapColor(level, mp);
                    if (st.is(Blocks.GRASS_BLOCK)) grass[i] = level.getBiome(mp).value().getGrassColor(wx, wz) & 0xFFFFFF;
                }
            }
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int i = x + z * 16;
                MapColor.Brightness b;
                if (water[i]) {
                    b = depth[i] < 3 ? MapColor.Brightness.HIGH : (depth[i] < 6 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW);
                } else {
                    int north = z > 0 ? ys[x + (z - 1) * 16] : ys[i];
                    b = ys[i] > north ? MapColor.Brightness.HIGH : (ys[i] < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL);
                }
                if (colors[i] == null) {
                    out.px[i] = 0;
                } else if (grass[i] >= 0) {
                    // grass blocks (and the plants on them) are painted in the colour of their biome
                    int mod = b == MapColor.Brightness.HIGH ? 255 : (b == MapColor.Brightness.LOW ? 180 : 220);
                    int rr = (grass[i] >> 16 & 0xFF) * mod / 255, gg = (grass[i] >> 8 & 0xFF) * mod / 255, bb = (grass[i] & 0xFF) * mod / 255;
                    out.px[i] = 0xFF000000 | (bb << 16) | (gg << 8) | rr;
                } else {
                    out.px[i] = MapColor.getColorFromPackedId(colors[i].getPackedId(b) & 0xFF);
                }
            }
        }
        out.time = System.currentTimeMillis();
        return true;
    }

    // ------------------------------------------------------------------ the file

    private static Path file(Minecraft mc) {
        ServerData sd = mc.getCurrentServer();
        String id;
        if (sd != null) id = "mp_" + sd.ip;
        else if (mc.getSingleplayerServer() != null) id = "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        else id = "unknown";
        worldId = id.replaceAll("[^A-Za-z0-9._-]", "_");
        File dir = new File(mc.gameDirectory, "politics_map");
        return dir.toPath().resolve(worldId + ".bin");
    }

    private static void load(Minecraft mc) {
        DATA.clear();
        Path path = file(mc);
        if (!Files.exists(path)) return;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            if (in.readInt() != 3) return; // older files have other colours and are dropped
            int dims = in.readInt();
            for (int d = 0; d < dims; d++) {
                String dim = in.readUTF();
                int n = in.readInt();
                Map<Long, Tile> tiles = tiles(dim);
                for (int i = 0; i < n; i++) {
                    long k = in.readLong();
                    Tile t = new Tile();
                    for (int pi = 0; pi < 256; pi++) t.px[pi] = in.readInt();
                    tiles.put(k, t);
                }
            }
        } catch (IOException ex) {
            LOG.warn("[politics] не удалось прочитать карту", ex);
        }
    }

    public static void save() {
        Minecraft mc = Minecraft.getInstance();
        if (worldId == null) return;
        Path path = file(mc);
        try {
            Files.createDirectories(path.getParent());
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))) {
                out.writeInt(3);
                out.writeInt(DATA.size());
                for (Map.Entry<String, Map<Long, Tile>> de : DATA.entrySet()) {
                    out.writeUTF(de.getKey());
                    out.writeInt(de.getValue().size());
                    for (Map.Entry<Long, Tile> te : de.getValue().entrySet()) {
                        out.writeLong(te.getKey());
                        for (int pi = 0; pi < 256; pi++) out.writeInt(te.getValue().px[pi]);
                    }
                }
            }
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (IOException ex) {
            LOG.warn("[politics] не удалось сохранить карту", ex);
        }
    }
}
