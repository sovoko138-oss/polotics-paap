package com.politics.client;

import com.politics.AlliancePackets;
import com.politics.PoliticsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** /alliance: the list of all alliances. */
public class AllianceListScreen extends Screen {
    private static final int W = 260, H = 240, ROWS = 7, ROW_H = 20;
    private static final int ZEBRA_A = 0xFF2B2B2B, ZEBRA_B = 0xFF3F3F3F, INSET = 0xFF1E1E1E;

    private final AlliancePackets.ListMsg msg;
    private int page = 0, left, top;

    public AllianceListScreen(AlliancePackets.ListMsg msg) {
        super(Component.literal(msg.ownOnly ? "Ваши альянсы" : "Альянсы"));
        this.msg = msg;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, height / 2 - H / 2);
        UUID none = new UUID(0, 0);
        boolean showOwn = msg.ownCount > 0 && !msg.ownOnly;
        int n = (msg.canCreate ? 1 : 0) + (showOwn ? 1 : 0);
        int bw = n == 2 ? (W - 28) / 2 : W - 24;
        int bx = left + 12;
        if (msg.canCreate) {
            addRenderableWidget(Button.builder(Component.literal("Создать альянс"),
                    b -> PoliticsNetwork.CHANNEL.sendToServer(AlliancePackets.Action.simple("create_open", none)))
                    .bounds(bx, top + 20, bw, 20).build());
            bx += bw + 4;
        }
        if (showOwn) {
            addRenderableWidget(Button.builder(Component.literal(msg.ownCount == 1 ? "Ваш альянс" : "Ваши альянсы"),
                    b -> PoliticsNetwork.CHANNEL.sendToServer(AlliancePackets.Action.simple("own", none)))
                    .bounds(bx, top + 20, bw, 20).build());
        }
        int by = top + H - 50;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1)).bounds(left + 12, by, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1)).bounds(left + W - 42, by, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(left + W / 2 - 40, top + H - 26, 80, 20).build());
    }

    private int pageCount() { return Math.max(1, (msg.entries.size() + ROWS - 1) / ROWS); }

    private void changePage(int d) { page = Math.max(0, Math.min(pageCount() - 1, page + d)); }

    @Override
    public boolean isPauseScreen() { return false; }

    private int ax() { return left + 12; }
    private int ay() { return top + 46; }
    private int aw() { return W - 24; }

    private int rowAt(double mx, double my) {
        if (mx < ax() || mx >= ax() + aw() || my < ay() || my >= ay() + ROWS * ROW_H) return -1;
        int idx = page * ROWS + (int) ((my - ay()) / ROW_H);
        return idx < msg.entries.size() ? idx : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int idx = rowAt(mx, my);
        if (idx < 0) return false;
        PoliticsNetwork.CHANNEL.sendToServer(AlliancePackets.Action.simple("info", msg.entries.get(idx).id));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        changePage(delta > 0 ? -1 : 1);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Panel.draw(g, left, top, W, H);
        g.drawString(font, title, left + (W - font.width(title)) / 2, top + 6, 0x404040, false);

        int ax = ax(), ay = ay(), aw = aw();
        g.fill(ax - 1, ay - 1, ax + aw + 1, ay + ROWS * ROW_H + 1, 0xFF373737);
        g.fill(ax, ay, ax + aw, ay + ROWS * ROW_H, INSET);

        int hover = rowAt(mx, my);
        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= msg.entries.size()) break;
            AlliancePackets.ListMsg.Entry e = msg.entries.get(idx);
            int y = ay + i * ROW_H;
            g.fill(ax, y, ax + aw, y + ROW_H, (i % 2 == 0) ? ZEBRA_A : ZEBRA_B); // every second row has another colour
            if (idx == hover) g.fill(ax, y, ax + aw, y + ROW_H, 0x30FFFFFF);
            g.renderItem(new ItemStack(Flags.item(e.flag)), ax + 3, y + 2);
            g.drawString(font, e.name, ax + 24, y + 6, 0xFF000000 | e.color, false);
            String right = "стран: " + e.members;
            g.drawString(font, right, ax + aw - 6 - font.width(right), y + 6, 0xFF9A9A9A, false);
        }
        if (msg.entries.isEmpty()) g.drawString(font, "Альянсов пока нет.", ax + 6, ay + 6, 0x8F8F8F, false);

        String pg = (page + 1) + " / " + pageCount();
        g.drawString(font, pg, left + (W - font.width(pg)) / 2, top + H - 44, 0x404040, false);
        super.render(g, mx, my, pt);
    }
}
