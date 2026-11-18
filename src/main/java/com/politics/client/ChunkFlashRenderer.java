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

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        WarStatusPacket st = WarPanel.current();
        if (st == null || st.phase != 2 || st.rows.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        float y0 = mc.level.getMinBuildHeight(), y1 = mc.level.getMaxBuildHeight();
        double seconds = System.currentTimeMillis() / 1000.0;

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
            int color = row.mine ? st.enemyColor : st.myColor; // the capturer's colour
            int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
            double hz = 1.0 + 5.0 * Math.min(100, row.percent) / 100.0; // 1 .. 6 blinks per second
            double wave = 0.5 + 0.5 * Math.sin(seconds * hz * Math.PI * 2);
            int a = (int) (wave * MAX_ALPHA);
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
