package com.politics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.politics.PoliticsMod;
import com.politics.WarStatusPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** A chunk that is being captured blinks in the capturer's colour; the more captured, the faster it blinks. */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID, value = Dist.CLIENT)
public class ChunkFlashRenderer {
    private static final float HALF = 0.15f;
    private static final int MAX_ALPHA = 150;

    /** Blink phase of every chunk, accumulated frame by frame so that a change of speed never makes the blink jump. */
    private static final java.util.Map<Long, Double> PHASE = new java.util.HashMap<>();
    private static long lastNanos = 0;

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        WarStatusPacket st = WarPanel.current();
        if (st == null || st.phase != 2 || st.rows.isEmpty()) {
            PHASE.clear();
            lastNanos = 0;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        float y0 = mc.level.getMinBuildHeight(), y1 = mc.level.getMaxBuildHeight();
        long nowNanos = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (nowNanos - lastNanos) / 1e9);
        lastNanos = nowNanos;
        java.util.Set<Long> alive = new java.util.HashSet<>();

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
        boolean any = false;
        for (WarStatusPacket.Row row : st.rows) {
            if (row.percent <= 0) continue;
            int a;
            int color;
            if (row.retake) {
                color = row.mine ? st.myColor : st.enemyColor; // the one taking the chunk back, blinking
                a = 0;
            } else if (row.done) {
                color = row.mine ? 0xFF3030 : 0x30FF30; // lost = red, captured = green, steady
                a = 150;
            } else {
                color = row.mine ? st.enemyColor : st.myColor; // the capturer's colour, blinking
                a = 0;
            }
            int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
            double hz = 0.2 + 2.8 * Math.min(100, row.percent) / 100.0; // 0.2 .. 3 blinks per second
            long key = com.politics.CountryData.chunkKey(row.cx, row.cz);
            alive.add(key);
            double phase = PHASE.getOrDefault(key, 0.0) + dt * hz;
            PHASE.put(key, phase);
            double wave = 0.5 + 0.5 * Math.sin(phase * Math.PI * 2);
            if (a == 0) a = (int) (wave * MAX_ALPHA);
            if (a < 3) continue;
            float x0 = row.cx * 16f, z0 = row.cz * 16f, x1 = x0 + 16f, z1 = z0 + 16f;
            wall(bb, m, x0, z0 - HALF, x1, z0 - HALF, y0, y1, r, g, b, a);
            wall(bb, m, x0, z0 + HALF, x1, z0 + HALF, y0, y1, r, g, b, a);
            wall(bb, m, x0, z1 - HALF, x1, z1 - HALF, y0, y1, r, g, b, a);
            wall(bb, m, x0, z1 + HALF, x1, z1 + HALF, y0, y1, r, g, b, a);
            wall(bb, m, x0 - HALF, z0, x0 - HALF, z1, y0, y1, r, g, b, a);
            wall(bb, m, x0 + HALF, z0, x0 + HALF, z1, y0, y1, r, g, b, a);
            wall(bb, m, x1 - HALF, z0, x1 - HALF, z1, y0, y1, r, g, b, a);
            wall(bb, m, x1 + HALF, z0, x1 + HALF, z1, y0, y1, r, g, b, a);
            any = true;
        }
        PHASE.keySet().retainAll(alive);
        if (any) BufferUploader.drawWithShader(bb.end()); else bb.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ps.popPose();
    }

    private static void wall(BufferBuilder bb, Matrix4f m, float xa, float za, float xb, float zb,
                             float y0, float y1, int r, int g, int b, int a) {
        bb.vertex(m, xa, y0, za).color(r, g, b, a).endVertex();
        bb.vertex(m, xb, y0, zb).color(r, g, b, a).endVertex();
        bb.vertex(m, xb, y1, zb).color(r, g, b, a).endVertex();
        bb.vertex(m, xa, y1, za).color(r, g, b, a).endVertex();
    }
}
