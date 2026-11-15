package com.politics.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Standard Minecraft window background (the grey panel), stretched to any size. */
public class Panel {
    private static final ResourceLocation BG = new ResourceLocation("textures/gui/demo_background.png");
    private static final int TW = 256, TH = 256; // texture sheet size
    private static final int RW = 248, RH = 166; // used region of the sheet
    private static final int E = 4;              // border size

    public static void draw(GuiGraphics g, int x, int y, int w, int h) {
        // corners
        g.blit(BG, x, y, E, E, 0, 0, E, E, TW, TH);
        g.blit(BG, x + w - E, y, E, E, RW - E, 0, E, E, TW, TH);
        g.blit(BG, x, y + h - E, E, E, 0, RH - E, E, E, TW, TH);
        g.blit(BG, x + w - E, y + h - E, E, E, RW - E, RH - E, E, E, TW, TH);
        // edges
        g.blit(BG, x + E, y, w - 2 * E, E, E, 0, RW - 2 * E, E, TW, TH);
        g.blit(BG, x + E, y + h - E, w - 2 * E, E, E, RH - E, RW - 2 * E, E, TW, TH);
        g.blit(BG, x, y + E, E, h - 2 * E, 0, E, E, RH - 2 * E, TW, TH);
        g.blit(BG, x + w - E, y + E, E, h - 2 * E, RW - E, E, E, RH - 2 * E, TW, TH);
        // centre
        g.blit(BG, x + E, y + E, w - 2 * E, h - 2 * E, E, E, RW - 2 * E, RH - 2 * E, TW, TH);
    }

    /** Text wrapped to maxW pixels, vertically centred on centerY (so it stays next to an icon or button). */
    public static void drawWrappedMiddle(GuiGraphics g, Font font, Component c, int x, int centerY,
                                         int maxW, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(c, maxW);
        int n = Math.min(maxLines, lines.size());
        int y = centerY - n * 5 + 1;
        for (int i = 0; i < n; i++) {
            g.drawString(font, lines.get(i), x, y, color, false);
            y += 10;
        }
    }

    /** Text wrapped to maxW pixels, each line centred on centerX, starting at y. */
    public static void drawWrappedCentered(GuiGraphics g, Font font, String text, int centerX, int y,
                                           int maxW, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), maxW);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            FormattedCharSequence seq = lines.get(i);
            g.drawString(font, seq, centerX - font.width(seq) / 2, y, color, false);
            y += 10;
        }
    }

    /** A vanilla-looking item slot, 18x18. */
    public static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }
}
