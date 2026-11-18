package com.politics.client;

import com.politics.JoinAnswerPacket;
import com.politics.JoinListPacket;
import com.politics.PoliticsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The /accept window: everybody who wants to join, with a tick (accept) and a cross (decline). */
public class JoinRequestsScreen extends Screen implements ResultScreen {
    private static final int W = 300, H = 232;
    private static final int VISIBLE = 9, ROW_H = 18;
    private static final int ZEBRA_A = 0xFF2B2B2B, ZEBRA_B = 0xFF3B3B3B, INSET = 0xFF1E1E1E;

    private UUID countryId;
    private String countryName = "";
    private final List<JoinListPacket.Entry> rows = new ArrayList<>();
    private int scroll = 0;
    private String status = "";
    private int statusColor = 0x404040;
    private int left, top;

    public JoinRequestsScreen(JoinListPacket m) {
        super(Component.literal("Заявки на вступление"));
        apply(m);
    }

    public UUID countryId() { return countryId; }

    public void update(JoinListPacket m) { apply(m); }

    private void apply(JoinListPacket m) {
        countryId = m.countryId;
        countryName = m.countryName;
        rows.clear();
        rows.addAll(m.entries);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - VISIBLE)));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, height / 2 - H / 2);
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(left + W / 2 - 45, top + H - 30, 90, 20).build());
    }

    @Override
    public void onResult(boolean ok, String message) {
        status = message;
        statusColor = ok ? 0x1E7B1E : 0xAA0000;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---- geometry ----
    private int ax() { return left + 12; }
    private int ay() { return top + 24; }
    private int aw() { return W - 24; }
    private int tickX() { return ax() + aw() - 38; }
    private int crossX() { return ax() + aw() - 20; }

    private int rowIndexAt(double mx, double my) {
        if (mx < ax() || mx >= ax() + aw() || my < ay()) return -1;
        int vi = (int) ((my - ay()) / ROW_H);
        if (vi < 0 || vi >= VISIBLE) return -1;
        int idx = scroll + vi;
        return idx < rows.size() ? idx : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int idx = rowIndexAt(mx, my);
        if (idx < 0) return false;
        String nick = rows.get(idx).nick;
        if (mx >= tickX() && mx < tickX() + 16) {
            PoliticsNetwork.CHANNEL.sendToServer(new JoinAnswerPacket(countryId, nick, true));
            return true;
        }
        if (mx >= crossX() && mx < crossX() + 16) {
            PoliticsNetwork.CHANNEL.sendToServer(new JoinAnswerPacket(countryId, nick, false));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - VISIBLE)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Panel.draw(g, left, top, W, H);
        String title = "Заявки на вступление в «" + countryName + "»";
        g.drawString(font, title, (width - font.width(title)) / 2, top + 8, 0x404040, false);

        int ax = ax(), ay = ay(), aw = aw();
        g.fill(ax - 1, ay - 1, ax + aw + 1, ay + VISIBLE * ROW_H + 1, 0xFF373737);
        g.fill(ax, ay, ax + aw, ay + VISIBLE * ROW_H, INSET);

        for (int i = 0; i < VISIBLE; i++) {
            int idx = scroll + i;
            if (idx >= rows.size()) break;
            drawRow(g, rows.get(idx), ay + i * ROW_H, i % 2);
        }

        if (rows.size() > VISIBLE) {
            int trackH = VISIBLE * ROW_H;
            int thumbH = Math.max(12, trackH * VISIBLE / rows.size());
            int thumbY = ay + (trackH - thumbH) * scroll / (rows.size() - VISIBLE);
            g.fill(ax + aw - 3, ay, ax + aw, ay + trackH, 0xFF101010);
            g.fill(ax + aw - 3, thumbY, ax + aw, thumbY + thumbH, 0xFF8B8B8B);
        }
        if (rows.isEmpty()) g.drawString(font, "Заявок на вступление нет.", ax + 6, ay + 5, 0x8F8F8F, false);

        if (!status.isEmpty()) {
            Panel.drawWrappedCentered(g, font, status, width / 2, top + H - 56, W - 24, statusColor, 1);
        } else {
            Panel.drawWrappedCentered(g, font, "Наведите на ник, чтобы узнать, где игрок жил раньше",
                    width / 2, top + H - 56, W - 24, 0x707070, 1);
        }

        super.render(g, mx, my, pt);

        int hover = rowIndexAt(mx, my);
        if (hover >= 0) {
            JoinListPacket.Entry e = rows.get(hover);
            if (mx >= tickX() && mx < tickX() + 16) {
                g.renderTooltip(font, Component.literal("Принять в страну"), mx, my);
            } else if (mx >= crossX() && mx < crossX() + 16) {
                g.renderTooltip(font, Component.literal("Отклонить"), mx, my);
            } else {
                g.renderTooltip(font, Component.literal(e.prevCountry.isEmpty()
                        ? "Нет истории жизни в странах" : "Жил в стране: " + e.prevCountry), mx, my);
            }
        }
    }

    private void drawRow(GuiGraphics g, JoinListPacket.Entry e, int y, int zebra) {
        int ax = ax(), aw = aw();
        g.fill(ax, y, ax + aw, y + ROW_H, zebra == 0 ? ZEBRA_A : ZEBRA_B);
        g.drawString(font, e.nick, ax + 8, y + 5, 0xFFFF55, false);

        // tick
        int tx = tickX(), ty = y + 3;
        g.fill(tx, ty, tx + 14, ty + 12, 0xFF1F6B1F);
        g.fill(tx, ty, tx + 14, ty + 1, 0xFF40B040);
        int[][] tick = {{3, 6}, {4, 7}, {5, 8}, {6, 7}, {7, 6}, {8, 5}, {9, 4}, {10, 3}};
        for (int[] p : tick) {
            g.fill(tx + p[0], ty + p[1], tx + p[0] + 1, ty + p[1] + 2, 0xFFFFFFFF);
        }

        // cross
        int cx = crossX(), cy = y + 3;
        g.fill(cx, cy, cx + 14, cy + 12, 0xFF6B1F1F);
        g.fill(cx, cy, cx + 14, cy + 1, 0xFFB04040);
        for (int i = 0; i < 6; i++) {
            g.fill(cx + 4 + i, cy + 3 + i, cx + 5 + i, cy + 4 + i, 0xFFFFFFFF);
            g.fill(cx + 9 - i, cy + 3 + i, cx + 10 - i, cy + 4 + i, 0xFFFFFFFF);
        }
    }
}
