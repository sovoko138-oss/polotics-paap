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
    private static final int ALPHA = 115;        // 0..255, opacity

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

        float y0 = mc.level.getMinBuildHeight(), y1 = mc.level.getMaxBuildHeight();
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

        for (int si = 0; si < sets.size(); si++) {
            Set<Long> chunks = new HashSet<>();
            for (long k : sets.get(si)) chunks.add(k);
            int color = colors.get(si);
            int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;

            Set<Long> corners = new HashSet<>();
            for (long k : chunks) {
                int cx = CountryData.keyX(k), cz = CountryData.keyZ(k);
                float x0 = cx * 16f, z0 = cz * 16f, x1 = x0 + 16f, z1 = z0 + 16f;

                if (!chunks.contains(CountryData.chunkKey(cx, cz - 1))) { // north side
                    wallX(bb, m, x0, x1, z0, h, y0, y1, r, g, b);
                    corners.add(CountryData.chunkKey(cx, cz)); corners.add(CountryData.chunkKey(cx + 1, cz));
                }
                if (!chunks.contains(CountryData.chunkKey(cx, cz + 1))) { // south side
                    wallX(bb, m, x0, x1, z1, h, y0, y1, r, g, b);
                    corners.add(CountryData.chunkKey(cx, cz + 1)); corners.add(CountryData.chunkKey(cx + 1, cz + 1));
                }
                if (!chunks.contains(CountryData.chunkKey(cx - 1, cz))) { // west side
                    wallZ(bb, m, x0, z0, z1, h, y0, y1, r, g, b);
                    corners.add(CountryData.chunkKey(cx, cz)); corners.add(CountryData.chunkKey(cx, cz + 1));
                }
                if (!chunks.contains(CountryData.chunkKey(cx + 1, cz))) { // east side
                    wallZ(bb, m, x1, z0, z1, h, y0, y1, r, g, b);
                    corners.add(CountryData.chunkKey(cx + 1, cz)); corners.add(CountryData.chunkKey(cx + 1, cz + 1));
                }
            }
            // Posts at the corners so that walls meeting at an angle have no gap
            for (long k : corners) {
                float cxp = CountryData.keyX(k) * 16f, czp = CountryData.keyZ(k) * 16f;
                wallX(bb, m, cxp - h, cxp + h, czp - h, 0, y0, y1, r, g, b);
                wallX(bb, m, cxp - h, cxp + h, czp + h, 0, y0, y1, r, g, b);
                wallZ(bb, m, cxp - h, czp - h, czp + h, 0, y0, y1, r, g, b);
                wallZ(bb, m, cxp + h, czp - h, czp + h, 0, y0, y1, r, g, b);
            }
        }

        BufferUploader.drawWithShader(bb.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ps.popPose();
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
