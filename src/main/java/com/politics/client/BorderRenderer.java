package com.politics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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

/** Draws the capital chunk border as solid semi-transparent vertical pillars while a wooden hoe is held. */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID, value = Dist.CLIENT)
public class BorderRenderer {
    private static final float THICKNESS = 0.25f; // pillar width in blocks
    private static final int STEP = 2;            // distance between pillars in blocks
    private static final int ALPHA = 115;         // 0..255, opacity

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!ClientHooks.borderActive) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.getMainHandItem().is(Items.WOODEN_HOE) && !mc.player.getOffhandItem().is(Items.WOODEN_HOE)) return;
        if (!mc.level.dimension().location().toString().equals(ClientHooks.borderDimension)) return;

        int color = ClientHooks.borderColor;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        float x0 = ClientHooks.borderChunkX * 16f, z0 = ClientHooks.borderChunkZ * 16f;
        float x1 = x0 + 16f, z1 = z0 + 16f;
        float y0 = mc.level.getMinBuildHeight(), y1 = mc.level.getMaxBuildHeight();

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
        for (int i = 0; i <= 16; i += STEP) {
            pillar(bb, m, x0 + i, z0, y0, y1, r, g, b);
            pillar(bb, m, x0 + i, z1, y0, y1, r, g, b);
            if (i > 0 && i < 16) {
                pillar(bb, m, x0, z0 + i, y0, y1, r, g, b);
                pillar(bb, m, x1, z0 + i, y0, y1, r, g, b);
            }
        }
        BufferUploader.drawWithShader(bb.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ps.popPose();
    }

    private static void pillar(BufferBuilder bb, Matrix4f m, float cx, float cz, float y0, float y1, int r, int g, int b) {
        float h = THICKNESS / 2f;
        float xa = cx - h, xb = cx + h, za = cz - h, zb = cz + h;
        quad(bb, m, xa, za, xb, za, y0, y1, r, g, b);
        quad(bb, m, xa, zb, xb, zb, y0, y1, r, g, b);
        quad(bb, m, xa, za, xa, zb, y0, y1, r, g, b);
        quad(bb, m, xb, za, xb, zb, y0, y1, r, g, b);
    }

    private static void quad(BufferBuilder bb, Matrix4f m, float xa, float za, float xb, float zb,
                             float y0, float y1, int r, int g, int b) {
        bb.vertex(m, xa, y0, za).color(r, g, b, ALPHA).endVertex();
        bb.vertex(m, xb, y0, zb).color(r, g, b, ALPHA).endVertex();
        bb.vertex(m, xb, y1, zb).color(r, g, b, ALPHA).endVertex();
        bb.vertex(m, xa, y1, za).color(r, g, b, ALPHA).endVertex();
    }
}
