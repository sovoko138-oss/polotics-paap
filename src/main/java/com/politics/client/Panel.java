package com.politics.client;

import net.minecraft.client.gui.GuiGraphics;
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

    /** A vanilla-looking item slot, 18x18. */
    public static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }
}
