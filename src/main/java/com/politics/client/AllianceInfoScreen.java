package com.politics.client;

import com.politics.AlliancePackets;
import com.politics.PoliticsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/** One alliance: name and flag, founder, date, chunks and the countries in it. */
public class AllianceInfoScreen extends Screen implements ResultScreen {
    private static final int W = 268, H = 262, ROWS = 5, ROW_H = 18;
    private static final int ZEBRA_A = 0xFF2B2B2B, ZEBRA_B = 0xFF3F3F3F, INSET = 0xFF1E1E1E;
    private static final int GRAY = 0xAAAAAA;

    private final AlliancePackets.InfoMsg m;
    private int scroll = 0, left, top;
    private String status = "";

    public AllianceInfoScreen(AlliancePackets.InfoMsg m) {
        super(Component.literal("Альянс"));
        this.m = m;
    }

    public UUID allianceId() { return m.id; }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, height / 2 - H / 2);
        UUID none = new UUID(0, 0);

        int row1 = top + H - 52, row2 = top + H - 28;
        boolean chat = m.canChat, leave = m.canLeave;
        if (chat && leave) {
            addRenderableWidget(Button.builder(Component.literal("Чат альянса"),
                    b -> PoliticsNetwork.CHANNEL.sendToServer(AlliancePackets.Action.simple("chat_open", m.id)))
                    .bounds(left + 12, row1, (W - 28) / 2, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Выйти из альянса"),
                    b -> PoliticsNetwork.CHANNEL.sendToServer(AlliancePackets.Action.simple("leave", m.id)))
                    .bounds(left + 16 + (W - 28) / 2, row1, (W - 28) / 2, 20).build());
        } else if (chat) {
            addRenderableWidget(Button.builder(Component.literal("Чат альянса"),
                    b -> PoliticsNetwork.CHANNEL.sendToServer(AlliancePackets.Action.simple("chat_open", m.id)))
                    .bounds(left + 12, row1, W - 24, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("К списку"),
                b -> PoliticsNetwork.CHANNEL.sendToServer(AlliancePackets.Action.simple("list", none)))
                .bounds(left + 12, row2, (W - 28) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(left + 16 + (W - 28) / 2, row2, (W - 28) / 2, 20).build());
    }

    @Override
    public void onResult(boolean ok, String message) { status = message; }

    @Override
    public boolean isPauseScreen() { return false; }

    private int ax() { return left + 12; }
    private int ay() { return top + 112; }
    private int aw() { return W - 24; }

    private int rowAt(double mx, double my) {
        if (mx < ax() || mx >= ax() + aw() || my < ay() || my >= ay() + ROWS * ROW_H) return -1;
        int idx = scroll + (int) ((my - ay()) / ROW_H);
        return idx < m.members.size() ? idx : -1;
    }

    private boolean onKick(double mx) { return mx >= ax() + aw() - 22; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int idx = rowAt(mx, my);
        if (idx < 0) return false;
        AlliancePackets.InfoMsg.Member member = m.members.get(idx);
        UUID none = new UUID(0, 0);
        if (m.founderView && !member.founder && onKick(mx)) {
            PoliticsNetwork.CHANNEL.sendToServer(new AlliancePackets.Action("kick", m.id, member.id, "", "", "", "", 0));
        } else {
            // the ordinary country window, now with the alliance of the country in it
            PoliticsNetwork.CHANNEL.sendToServer(new AlliancePackets.Action("country", none, member.id, "", "", "", "", 0));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, m.members.size() - ROWS), scroll - (int) Math.signum(delta)));
        return true;
    }

    private MutableComponent label(String k, String v) {
        return Component.literal(k + ": ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(GRAY)))
                .append(Component.literal(v).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
    }

    private int lines(GuiGraphics g, Component c, int x, int y, int maxW) {
        for (FormattedCharSequence seq : font.split(c, maxW)) {
            g.drawString(font, seq, x, y, 0xFFFFFF, false);
            y += 10;
        }
        return y + 1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Panel.draw(g, left, top, W, H);

        // in the middle: the name in the colour of the alliance, and the flag
        int cx = left + W / 2;
        int nameW = font.width(m.name);
        g.renderItem(new ItemStack(Flags.item(m.flag)), cx - nameW / 2 - 22, top + 6);
        g.drawString(font, Component.literal(m.name).withStyle(ChatFormatting.BOLD), cx - nameW / 2 + 2, top + 11,
                0xFF000000 | m.color, false);

        int x = left + 14, y = top + 28, maxW = W - 28;
        y = lines(g, label("Основатель", m.founderCountry + " / " + m.founderPresident), x, y, maxW);
        y = lines(g, label("Создан", new SimpleDateFormat("dd.MM.yyyy").format(new Date(m.created))), x, y, maxW);
        y = lines(g, label("Чанков в сумме", String.valueOf(m.totalChunks)), x, y, maxW);
        lines(g, label("Столица альянса", m.capital), x, y, maxW);

        g.drawString(font, "Страны альянса:", ax(), top + 100, 0x404040, false);

        int ax = ax(), ay = ay(), aw = aw();
        g.fill(ax - 1, ay - 1, ax + aw + 1, ay + ROWS * ROW_H + 1, 0xFF373737);
        g.fill(ax, ay, ax + aw, ay + ROWS * ROW_H, INSET);
        int hover = rowAt(mx, my);
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            if (idx >= m.members.size()) break;
            AlliancePackets.InfoMsg.Member member = m.members.get(idx);
            int ry = ay + i * ROW_H;
            g.fill(ax, ry, ax + aw, ry + ROW_H, (i % 2 == 0) ? ZEBRA_A : ZEBRA_B);
            if (idx == hover) g.fill(ax, ry, ax + aw, ry + ROW_H, 0x30FFFFFF);
            g.renderItem(new ItemStack(Flags.item(member.flag)), ax + 2, ry + 1);
            String nm = member.name + (member.founder ? " ★" : "");
            g.drawString(font, nm, ax + 22, ry + 5, 0xFF000000 | member.color, false);
            String pr = member.president;
            int right = ax + aw - (m.founderView && !member.founder ? 26 : 6);
            g.drawString(font, pr, right - font.width(pr), ry + 5, 0xFF9A9A9A, false);
            if (m.founderView && !member.founder) {
                int kx = ax + aw - 20, ky = ry + 3;
                g.fill(kx, ky, kx + 14, ky + 12, 0xFF6B1F1F);
                g.fill(kx, ky, kx + 14, ky + 1, 0xFFB04040);
                for (int k = 0; k < 6; k++) {
                    g.fill(kx + 4 + k, ky + 3 + k, kx + 5 + k, ky + 4 + k, 0xFFFFFFFF);
                    g.fill(kx + 9 - k, ky + 3 + k, kx + 10 - k, ky + 4 + k, 0xFFFFFFFF);
                }
            }
        }
        if (!status.isEmpty()) {
            Panel.drawWrappedCentered(g, font, status, cx, top + H - 66, W - 24, 0xAA0000, 1);
        }
        super.render(g, mx, my, pt);

        if (hover >= 0 && m.founderView && !m.members.get(hover).founder && onKick(mx)) {
            g.renderTooltip(font, Component.literal("Исключить страну из альянса"), mx, my);
        } else if (hover >= 0) {
            g.renderTooltip(font, Component.literal("Открыть информацию о стране"), mx, my);
        }
    }
}
