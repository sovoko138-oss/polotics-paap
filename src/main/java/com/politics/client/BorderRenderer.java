package com.politics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.politics.ClientBorderState;
import com.politics.CountryData;
import com.politics.PoliticsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

/**
 * Draws the outer border of the country's territory as a solid semi-transparent wall
 * while a wooden hoe is held. Borders between two of the country's own chunks are not drawn.
 */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID, value = Dist.CLIENT)
public class BorderRenderer {
    private static final float THICKNESS = 0.3f; // wall thickness in blocks
    private static final int ALPHA = 55;         // 0..255, opacity

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.getMainHandItem().is(Items.WOODEN_HOE) && !mc.player.getOffhandItem().is(Items.WOODEN_HOE)) return;
        String dim = mc.level.dimension().location().toString();

        // DEBUG: with a wooden hoe every country's territory is shown; without the debug data only the own one
        java.util.List<long[]> sets = new java.util.ArrayList<>();
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        for (com.politics.AllBordersPacket.Entry en : DebugBorders.entries) {
            if (!en.dimension.equals(dim) || en.chunks.length == 0) continue;
            sets.add(en.chunks);
            colors.add(en.color);
        }
        if (sets.isEmpty() && ClientBorderState.active && dim.equals(ClientBorderState.dimension)
                && !ClientBorderState.chunks.isEmpty()) {
            sets.add(ClientBorderState.chunks.stream().mapToLong(Long::longValue).toArray());
            colors.add(ClientBorderState.color);
        }
        if (sets.isEmpty()) return;

        // low walls around the player's height instead of a wall up to the sky
        float y0 = (float) Math.floor(mc.player.getY()) - 1f, y1 = y0 + 4f;
        float h = THICKNESS / 2f;

        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // which country owns which chunk, to find the borders between two countries
        java.util.Map<Long, Integer> ownerIdx = new java.util.HashMap<>();
        for (int si = 0; si < sets.size(); si++) for (long k : sets.get(si)) ownerIdx.put(k, si);
        final int GRAY = 0x9A;

        for (int si = 0; si < sets.size(); si++) {
            Set<Long> chunks = new HashSet<>();
            for (long k : sets.get(si)) chunks.add(k);
            int color = colors.get(si);
            int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;

            Set<Long> corners = new HashSet<>();
            Set<Long> grayCorners = new HashSet<>();
            for (long k : chunks) {
                int cx = CountryData.keyX(k), cz = CountryData.keyZ(k);
                float x0 = cx * 16f, z0 = cz * 16f, x1 = x0 + 16f, z1 = z0 + 16f;

                for (int side = 0; side < 4; side++) {
                    int nx = cx + (side == 2 ? -1 : (side == 3 ? 1 : 0));
                    int nz = cz + (side == 0 ? -1 : (side == 1 ? 1 : 0));
                    long nk = CountryData.chunkKey(nx, nz);
                    if (chunks.contains(nk)) continue;
                    Integer oi = ownerIdx.get(nk);
                    boolean foreign = oi != null && oi != si; // the neighbour belongs to another country
                    if (foreign && oi < si) continue;         // the wall is drawn once, from the lower-numbered country
                    int wr = foreign ? GRAY : r, wg = foreign ? GRAY : g, wb = foreign ? GRAY : b;
                    Set<Long> cs = foreign ? grayCorners : corners;
                    if (side == 0) {        // north: trace the outside of edge blocks
                        for (int i = 0; i < 16; i++) {
                            float xa = x0 + i, xb = xa + 1;
                            float edge = z0, shifted = z0 - 1;
                            boolean blocked = edgeHasBlock(mc, cx * 16 + i, cz * 16, (int)y0, (int)y1);
                            float line = blocked ? shifted : edge;
                            wallX(bb, m, xa, xb, line, h, y0, y1, wr, wg, wb);
                            if (blocked && (i == 0 || !edgeHasBlock(mc, cx * 16 + i - 1, cz * 16, (int)y0, (int)y1))) wallZ(bb, m, xa, edge, line, h, y0, y1, wr, wg, wb);
                            if (blocked && (i == 15 || !edgeHasBlock(mc, cx * 16 + i + 1, cz * 16, (int)y0, (int)y1))) wallZ(bb, m, xb, edge, line, h, y0, y1, wr, wg, wb);
                        }
                        cs.add(CountryData.chunkKey(cx, cz)); cs.add(CountryData.chunkKey(cx + 1, cz));
                    } else if (side == 1) { // south
                        for (int i = 0; i < 16; i++) {
                            float xa = x0 + i, xb = xa + 1;
                            float edge = z1, shifted = z1 + 1;
                            boolean blocked = edgeHasBlock(mc, cx * 16 + i, cz * 16 + 15, (int)y0, (int)y1);
                            float line = blocked ? shifted : edge;
                            wallX(bb, m, xa, xb, line, h, y0, y1, wr, wg, wb);
                            if (blocked && (i == 0 || !edgeHasBlock(mc, cx * 16 + i - 1, cz * 16 + 15, (int)y0, (int)y1))) wallZ(bb, m, xa, edge, line, h, y0, y1, wr, wg, wb);
                            if (blocked && (i == 15 || !edgeHasBlock(mc, cx * 16 + i + 1, cz * 16 + 15, (int)y0, (int)y1))) wallZ(bb, m, xb, edge, line, h, y0, y1, wr, wg, wb);
                        }
                        cs.add(CountryData.chunkKey(cx, cz + 1)); cs.add(CountryData.chunkKey(cx + 1, cz + 1));
                    } else if (side == 2) { // west
                        for (int i = 0; i < 16; i++) {
                            float za = z0 + i, zb = za + 1;
                            float edge = x0, shifted = x0 - 1;
                            boolean blocked = edgeHasBlock(mc, cx * 16, cz * 16 + i, (int)y0, (int)y1);
                            float line = blocked ? shifted : edge;
                            wallZ(bb, m, line, za, zb, h, y0, y1, wr, wg, wb);
                            if (blocked && (i == 0 || !edgeHasBlock(mc, cx * 16, cz * 16 + i - 1, (int)y0, (int)y1))) wallX(bb, m, edge, line, za, h, y0, y1, wr, wg, wb);
                            if (blocked && (i == 15 || !edgeHasBlock(mc, cx * 16, cz * 16 + i + 1, (int)y0, (int)y1))) wallX(bb, m, edge, line, zb, h, y0, y1, wr, wg, wb);
                        }
                        cs.add(CountryData.chunkKey(cx, cz)); cs.add(CountryData.chunkKey(cx, cz + 1));
                    } else {                // east
                        for (int i = 0; i < 16; i++) {
                            float za = z0 + i, zb = za + 1;
                            float edge = x1, shifted = x1 + 1;
                            boolean blocked = edgeHasBlock(mc, cx * 16 + 15, cz * 16 + i, (int)y0, (int)y1);
                            float line = blocked ? shifted : edge;
                            wallZ(bb, m, line, za, zb, h, y0, y1, wr, wg, wb);
                            if (blocked && (i == 0 || !edgeHasBlock(mc, cx * 16 + 15, cz * 16 + i - 1, (int)y0, (int)y1))) wallX(bb, m, edge, line, za, h, y0, y1, wr, wg, wb);
                            if (blocked && (i == 15 || !edgeHasBlock(mc, cx * 16 + 15, cz * 16 + i + 1, (int)y0, (int)y1))) wallX(bb, m, edge, line, zb, h, y0, y1, wr, wg, wb);
                        }
                        cs.add(CountryData.chunkKey(cx + 1, cz)); cs.add(CountryData.chunkKey(cx + 1, cz + 1));
                    }
                }
            }
            corners.removeAll(grayCorners);
            // Posts at the corners so that walls meeting at an angle have no gap
            for (long k : corners) {
                float cxp = CountryData.keyX(k) * 16f, czp = CountryData.keyZ(k) * 16f;
                wallX(bb, m, cxp - h, cxp + h, czp - h, 0, y0, y1, r, g, b);
                wallX(bb, m, cxp - h, cxp + h, czp + h, 0, y0, y1, r, g, b);
                wallZ(bb, m, cxp - h, czp - h, czp + h, 0, y0, y1, r, g, b);
                wallZ(bb, m, cxp + h, czp - h, czp + h, 0, y0, y1, r, g, b);
            }
            for (long k : grayCorners) {
                float cxp = CountryData.keyX(k) * 16f, czp = CountryData.keyZ(k) * 16f;
                wallX(bb, m, cxp - h, cxp + h, czp - h, 0, y0, y1, GRAY, GRAY, GRAY);
                wallX(bb, m, cxp - h, cxp + h, czp + h, 0, y0, y1, GRAY, GRAY, GRAY);
                wallZ(bb, m, cxp - h, czp - h, czp + h, 0, y0, y1, GRAY, GRAY, GRAY);
                wallZ(bb, m, cxp + h, czp - h, czp + h, 0, y0, y1, GRAY, GRAY, GRAY);
            }
        }

        BufferUploader.drawWithShader(bb.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ps.popPose();
    }

    /** If any non-air block occupies this boundary column within the displayed wall height, the border is pushed one block outward. */
    private static boolean edgeHasBlock(Minecraft mc, int x, int z, int y0, int y1) {
        if (mc.level == null) return false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y0, z);
        for (int y = y0; y <= y1; y++) {
            pos.set(x, y, z);
            if (!mc.level.getBlockState(pos).isAir()) return true;
        }
        return false;
    }

    /** A wall running along the X axis at the given z: two faces, h to each side of the line. */
    private static void wallX(BufferBuilder bb, Matrix4f m, float xa, float xb, float z, float h,
                              float y0, float y1, int r, int g, int b) {
        quad(bb, m, xa, z - h, xb, z - h, y0, y1, r, g, b);
        if (h > 0) quad(bb, m, xa, z + h, xb, z + h, y0, y1, r, g, b);
    }

    /** A wall running along the Z axis at the given x. */
    private static void wallZ(BufferBuilder bb, Matrix4f m, float x, float za, float zb, float h,
                              float y0, float y1, int r, int g, int b) {
        quad(bb, m, x - h, za, x - h, zb, y0, y1, r, g, b);
        if (h > 0) quad(bb, m, x + h, za, x + h, zb, y0, y1, r, g, b);
    }

    private static void quad(BufferBuilder bb, Matrix4f m, float xa, float za, float xb, float zb,
                             float y0, float y1, int r, int g, int b) {
        bb.vertex(m, xa, y0, za).color(r, g, b, ALPHA).endVertex();
        bb.vertex(m, xb, y0, zb).color(r, g, b, ALPHA).endVertex();
        bb.vertex(m, xb, y1, zb).color(r, g, b, ALPHA).endVertex();
        bb.vertex(m, xa, y1, za).color(r, g, b, ALPHA).endVertex();
    }
}
