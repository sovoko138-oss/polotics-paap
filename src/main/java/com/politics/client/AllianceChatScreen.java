package com.politics.client;

import com.politics.AlliancePackets;
import com.politics.PoliticsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.ChatFormatting;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** The chat of an alliance: the presidents of its countries write to each other. */
public class AllianceChatScreen extends Screen {
    private static final int W = 288, H = 232;

    private UUID id;
    private String allianceName;
    private int allianceColor;
    private final List<AlliancePackets.ChatMsg.Line> lines = new ArrayList<>();
    private EditBox input;
    private String typed = "";
    private int scroll = 0, left, top;

    public AllianceChatScreen(AlliancePackets.ChatMsg m) {
        super(Component.literal("Чат альянса"));
        apply(m);
    }

    public UUID allianceId() { return id; }

    public void update(AlliancePackets.ChatMsg m) {
        if (input != null) typed = input.getValue();
        apply(m);
        scroll = 0;
    }

    private void apply(AlliancePackets.ChatMsg m) {
        id = m.id;
        allianceName = m.name;
        allianceColor = m.color;
        lines.clear();
        lines.addAll(m.lines);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, height / 2 - H / 2);

        input = new EditBox(font, left + 12, top + H - 54, W - 24 - 66, 20, Component.literal("Сообщение"));
        input.setMaxLength(200);
        input.setValue(typed);
        addRenderableWidget(input);
        addRenderableWidget(Button.builder(Component.literal("Отправить"), b -> submit())
                .bounds(left + W - 12 - 62, top + H - 54, 62, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"),
                b -> PoliticsNetwork.CHANNEL.sendToServer(AlliancePackets.Action.simple("info", id)))
                .bounds(left + W / 2 - 40, top + H - 28, 80, 20).build());
        setInitialFocus(input);
    }

    private void submit() {
        String text = input.getValue().trim();
        if (text.isEmpty()) return;
        PoliticsNetwork.CHANNEL.sendToServer(new AlliancePackets.Action("chat_send", id, new UUID(0, 0), text, "", "", "", 0));
        input.setValue("");
        typed = "";
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && input.isFocused()) { // Enter
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, scroll + (int) Math.signum(delta) * 2);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Panel.draw(g, left, top, W, H);
        String t = "Чат альянса «" + allianceName + "»";
        g.drawString(font, Component.literal(t).withStyle(ChatFormatting.BOLD), left + (W - font.width(t)) / 2, top + 7,
                0xFF000000 | allianceColor, false);

        int ax = left + 12, ay = top + 22, aw = W - 24, ah = H - 22 - 62;
        g.fill(ax - 1, ay - 1, ax + aw + 1, ay + ah + 1, 0xFF373737);
        g.fill(ax, ay, ax + aw, ay + ah, 0xFF1E1E1E);

        // wrap every message and show the newest at the bottom
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm");
        List<FormattedCharSequence> all = new ArrayList<>();
        for (AlliancePackets.ChatMsg.Line l : lines) {
            MutableComponent c = Component.literal("[" + fmt.format(new Date(l.time)) + "] ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(l.country + " ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(l.color & 0xFFFFFF))))
                    .append(Component.literal(l.sender + ": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(l.text).withStyle(ChatFormatting.WHITE));
            all.addAll(font.split(c, aw - 8));
        }
        int maxLines = ah / 10;
        int maxScroll = Math.max(0, all.size() - maxLines);
        scroll = Math.min(scroll, maxScroll);
        int end = all.size() - scroll;
        int start = Math.max(0, end - maxLines);
        int y = ay + ah - (end - start) * 10 - 2;
        for (int i = start; i < end; i++) {
            g.drawString(font, all.get(i), ax + 4, y, 0xFFFFFF, false);
            y += 10;
        }
        if (all.isEmpty()) g.drawString(font, "Сообщений пока нет.", ax + 4, ay + 4, 0x8F8F8F, false);
        super.render(g, mx, my, pt);
    }
}
