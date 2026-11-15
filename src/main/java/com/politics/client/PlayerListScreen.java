package com.politics.client;

import com.politics.KickPlayerPacket;
import com.politics.PlayerListPacket;
import com.politics.PoliticsNetwork;
import com.politics.RequestPlayerListPacket;
import com.politics.SetTitlePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Residents of a country grouped by city: online/offline, titles, and kick buttons for the leadership. */
public class PlayerListScreen extends Screen implements ResultScreen {
    private static final int W = 320, H = 248;
    private static final int VISIBLE = 11, ROW_H = 16;

    private static final int ZEBRA_A = 0xFF2B2B2B, ZEBRA_B = 0xFF3B3B3B, HEADER_BG = 0xFF161616, INSET = 0xFF1E1E1E;
    private static final int ONLINE = 0x55FF55, OFFLINE = 0x8F8F8F, TITLE = 0xFFFF55;

    private static class Row {
        boolean header;
        String text = "";
        int count;
        PlayerListPacket.Entry entry;
        int zebra;
    }

    private UUID countryId;
    private String countryName = "";
    private final List<Row> rows = new ArrayList<>();
    private int scroll = 0;
    private int ticks = 0;
    private String status = "";
    private int statusColor = 0x404040;
    private int left, top;

    public PlayerListScreen(PlayerListPacket m) {
        super(Component.literal("Жители"));
        apply(m);
    }

    public UUID countryId() { return countryId; }

    public void update(PlayerListPacket m) { apply(m); }

    private void apply(PlayerListPacket m) {
        countryId = m.countryId;
        countryName = m.countryName;
        rows.clear();
        for (PlayerListPacket.Group g : m.groups) {
            Row h = new Row();
            h.header = true;
            h.text = g.city;
            h.count = g.entries.size();
            rows.add(h);
            int z = 0;
            for (PlayerListPacket.Entry e : g.entries) {
                Row r = new Row();
                r.entry = e;
                r.zebra = z++ % 2;
                rows.add(r);
            }
        }
        clampScroll();
    }

    private void clampScroll() {
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
    public void tick() {
        if (++ticks % 100 == 0) { // refresh online status every 5 seconds
            PoliticsNetwork.CHANNEL.sendToServer(new RequestPlayerListPacket(countryId));
        }
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
    private int crossX() { return ax() + aw() - 22; }
    private int titleZoneX() { return ax() + 110; }

    private int rowIndexAt(double mx, double my) {
        if (mx < ax() || mx >= ax() + aw() || my < ay()) return -1;
        int vi = (int) ((my - ay()) / ROW_H);
        if (vi < 0 || vi >= VISIBLE) return -1;
        int idx = scroll + vi;
        return idx < rows.size() ? idx : -1;
    }

    // ---- input ----
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int idx = rowIndexAt(mx, my);
        if (idx < 0) return false;
        Row r = rows.get(idx);
        if (r.header || r.entry == null) return false;
        PlayerListPacket.Entry e = r.entry;

        if (e.canKick && mx >= crossX() && mx < crossX() + 16) {
            confirmKick(e.name);
            return true;
        }
        if (e.canTitle && mx >= titleZoneX() && mx < crossX()) {
            minecraft.setScreen(new TitleEditScreen(this, e.name, e.rawTitle, text ->
                    PoliticsNetwork.CHANNEL.sendToServer(new SetTitlePacket(countryId, e.name, text))));
            return true;
        }
        return false;
    }

    private void confirmKick(String name) {
        minecraft.setScreen(new ConfirmScreen(ok -> {
            minecraft.setScreen(this);
            if (ok) PoliticsNetwork.CHANNEL.sendToServer(new KickPlayerPacket(countryId, name));
        }, Component.literal("Исключить игрока из страны?"), Component.literal("Игрок: " + name),
                Component.literal("Подтвердить"), Component.literal("Нет")));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta);
        clampScroll();
        return true;
    }

    // ---- drawing ----
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Panel.draw(g, left, top, W, H);
        String title = "Жители страны «" + countryName + "»";
        g.drawString(font, title, (width - font.width(title)) / 2, top + 8, 0x404040, false);

        int ax = ax(), ay = ay(), aw = aw();
        g.fill(ax - 1, ay - 1, ax + aw + 1, ay + VISIBLE * ROW_H + 1, 0xFF373737);
        g.fill(ax, ay, ax + aw, ay + VISIBLE * ROW_H, INSET);

        int hover = rowIndexAt(mx, my);
        for (int i = 0; i < VISIBLE; i++) {
            int idx = scroll + i;
            if (idx >= rows.size()) break;
            drawRow(g, rows.get(idx), ay + i * ROW_H);
        }

        // scrollbar
        if (rows.size() > VISIBLE) {
            int trackH = VISIBLE * ROW_H;
            int thumbH = Math.max(12, trackH * VISIBLE / rows.size());
            int thumbY = ay + (trackH - thumbH) * scroll / (rows.size() - VISIBLE);
            g.fill(ax + aw - 3, ay, ax + aw, ay + trackH, 0xFF101010);
            g.fill(ax + aw - 3, thumbY, ax + aw, thumbY + thumbH, 0xFF8B8B8B);
        }

        if (rows.isEmpty()) g.drawString(font, "Жителей пока нет.", ax + 6, ay + 5, OFFLINE, false);

        if (!status.isEmpty()) {
            Panel.drawWrappedCentered(g, font, status, width / 2, top + H - 44, W - 24, statusColor, 1);
        }

        super.render(g, mx, my, pt);

        // tooltips
        if (hover >= 0 && !rows.get(hover).header && rows.get(hover).entry != null) {
            PlayerListPacket.Entry e = rows.get(hover).entry;
            if (e.canKick && mx >= crossX() && mx < crossX() + 16) {
                g.renderTooltip(font, Component.literal("Исключить из страны"), mx, my);
            } else if (e.canTitle && mx >= titleZoneX() && mx < crossX()) {
                g.renderTooltip(font, Component.literal("Нажмите, чтобы изменить звание"), mx, my);
            }
        }
    }

    private void drawRow(GuiGraphics g, Row r, int y) {
        int ax = ax(), aw = aw();
        if (r.header) {
            g.fill(ax, y, ax + aw, y + ROW_H, HEADER_BG);
            g.fill(ax, y + ROW_H - 1, ax + aw, y + ROW_H, 0xFF555555);
            String count = " (" + r.count + ")";
            g.drawString(font, Component.literal(r.text).withStyle(net.minecraft.ChatFormatting.BOLD), ax + 6, y + 4, 0xFFFFFF, false);
            g.drawString(font, count, ax + 6 + font.width(Component.literal(r.text).withStyle(net.minecraft.ChatFormatting.BOLD)), y + 4, OFFLINE, false);
            return;
        }
        PlayerListPacket.Entry e = r.entry;
        g.fill(ax, y, ax + aw, y + ROW_H, r.zebra == 0 ? ZEBRA_A : ZEBRA_B);

        g.drawString(font, e.name, ax + 8, y + 4, e.online ? ONLINE : OFFLINE, false);

        if (!e.title.isEmpty()) {
            int maxW = crossX() - 6 - titleZoneX();
            String t = font.plainSubstrByWidth(e.title, maxW);
            g.drawString(font, t, crossX() - 6 - font.width(t), y + 4, TITLE, false);
        }

        if (e.canKick) {
            int cx = crossX(), cy = y + 2;
            boolean hot = false;
            g.fill(cx, cy, cx + 14, cy + 12, 0xFF6B1F1F);
            g.fill(cx, cy, cx + 14, cy + 1, 0xFFB04040);
            for (int i = 0; i < 6; i++) {
                g.fill(cx + 4 + i, cy + 3 + i, cx + 5 + i, cy + 4 + i, 0xFFFFFFFF);
                g.fill(cx + 9 - i, cy + 3 + i, cx + 10 - i, cy + 4 + i, 0xFFFFFFFF);
            }
        }
    }
}
