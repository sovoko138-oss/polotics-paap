package com.politics.client;

import com.politics.PoliticsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Messages in the right edge of the screen (letters, war declarations). They do not stop the game. */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID, value = Dist.CLIENT)
public class NoticeOverlay {
    private static final int W = 190;
    private static final int MAX_LINES = 8;
    private static final int MAX_SHOWN = 5;

    private static class Notice {
        String title;
        int color;
        List<FormattedCharSequence> lines;
        long start, duration;
        int height;
    }

    private static final List<Notice> LIST = new ArrayList<>();

    public static void add(String title, String body, int color) {
        Font font = Minecraft.getInstance().font;
        Notice n = new Notice();
        n.title = title;
        n.color = color & 0xFFFFFF;
        List<FormattedCharSequence> all = font.split(Component.literal(body), W - 14);
        n.lines = new ArrayList<>(all.size() > MAX_LINES ? all.subList(0, MAX_LINES) : all);
        if (all.size() > MAX_LINES) n.lines.set(MAX_LINES - 1, Component.literal("...").getVisualOrderText());
        n.start = System.currentTimeMillis();
        n.duration = Math.min(20000L, 8000L + body.length() * 40L);
        n.height = 8 + (title.isEmpty() ? 0 : 12) + n.lines.size() * 10;
        LIST.add(0, n);
        while (LIST.size() > MAX_SHOWN) LIST.remove(LIST.size() - 1);
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post e) {
        if (LIST.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        long now = System.currentTimeMillis();
        LIST.removeIf(n -> now - n.start > n.duration);

        GuiGraphics g = e.getGuiGraphics();
        Font font = mc.font;
        int sw = e.getWindow().getGuiScaledWidth();
        int y = 50;
        for (Notice n : LIST) {
            long age = now - n.start;
            long left = n.duration - age;
            float slide = age < 300 ? 1f - age / 300f : 0f;
            float fade = left < 500 ? Math.max(0f, left / 500f) : 1f;
            int x = sw - W - 6 + (int) (slide * (W + 10));
            int bgA = (int) (fade * 0xCC);
            int ta = Math.max(4, (int) (fade * 255));

            g.fill(x, y, x + W, y + n.height, (bgA << 24) | 0x101010);
            g.fill(x, y, x + 3, y + n.height, ((int) (fade * 255) << 24) | n.color);

            int ty = y + 4;
            if (!n.title.isEmpty()) {
                String t = font.plainSubstrByWidth(n.title, W - 14);
                g.drawString(font, Component.literal(t).withStyle(ChatFormatting.BOLD), x + 8, ty,
                        (ta << 24) | n.color, false);
                ty += 12;
            }
            for (FormattedCharSequence line : n.lines) {
                g.drawString(font, line, x + 8, ty, (ta << 24) | 0xFFFFFF, false);
                ty += 10;
            }
            y += n.height + 4;
        }
    }
}
