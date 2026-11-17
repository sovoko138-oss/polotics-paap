package com.politics.client;

import com.politics.PoliticsMod;
import com.politics.WarStatusPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The table on the right edge during a war: how far each chunk is captured. It never stops the game. */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID, value = Dist.CLIENT)
public class WarPanel {
    private static final int W = 164;
    private static WarStatusPacket cur;
    private static long updated;

    public static void update(WarStatusPacket m) {
        cur = m;
        updated = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post e) {
        WarStatusPacket m = cur;
        if (m == null) return;
        if (System.currentTimeMillis() - updated > 3000) { cur = null; return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        GuiGraphics g = e.getGuiGraphics();
        Font font = mc.font;
        int sw = e.getWindow().getGuiScaledWidth(), sh = e.getWindow().getGuiScaledHeight();
        int h = 8 + 12 + 12 + 22 + m.rows.size() * 15;
        int x = sw - W - 4;
        int y = Math.max(4, (sh - h) / 2);

        g.fill(x, y, x + W, y + h, 0xB0101010);
        g.fill(x, y, x + 3, y + h, 0xFF000000 | (m.enemyColor & 0xFFFFFF));

        int tx = x + 8, ty = y + 4;
        g.drawString(font, Component.literal("Война: " + m.enemyName).withStyle(ChatFormatting.BOLD), tx, ty,
                0xFF000000 | (m.enemyColor & 0xFFFFFF), false);
        ty += 12;
        if (m.phase == 1) {
            int s = Math.max(0, m.seconds);
            g.drawString(font, "Бои начнутся через " + (s / 60) + ":" + String.format("%02d", s % 60), tx, ty, 0xFFFFD27F, false);
        } else {
            g.drawString(font, "Боевые действия", tx, ty, 0xFFFF6B6B, false);
        }
        ty += 12;
        g.drawString(font, "Ваши земли потеряны: " + m.myLost + "/" + m.myTotal, tx, ty, 0xFFFFFFFF, false);
        ty += 10;
        g.drawString(font, "Земли врага взяты: " + m.enemyLost + "/" + m.enemyTotal, tx, ty, 0xFFFFFFFF, false);
        ty += 12;

        for (WarStatusPacket.Row r : m.rows) {
            int color = r.mine ? 0xFFFF5555 : 0xFF55FF55;
            String label = "Чанк " + r.cx + ", " + r.cz;
            g.drawString(font, label, tx, ty, 0xFFFFFFFF, false);
            String pct = r.done ? "захвачен" : r.percent + "%";
            g.drawString(font, pct, x + W - 6 - font.width(pct), ty, color, false);
            int bw = W - 16;
            g.fill(tx, ty + 10, tx + bw, ty + 13, 0x80000000);
            g.fill(tx, ty + 10, tx + bw * Math.min(100, r.percent) / 100, ty + 13, color);
            ty += 15;
        }
    }
}
