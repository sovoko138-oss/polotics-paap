package com.politics.client;

import com.politics.AlliancePackets;
import com.politics.PoliticsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** The "Пригласить" window of an alliance: countries to invite, with hints while typing. */
public class AllianceInviteScreen extends Screen implements ResultScreen {
    private static final int TEXT = 0x404040;
    private static final int W = 240, HINT_H = 12, MAX_HINTS = 5;

    private final UUID allianceId;
    private final String allianceName;
    private final List<String> countries;
    private EditBox box;
    private Button sendButton;
    private String typed = "", status = "";
    private int statusColor = 0xAA0000;
    private int left, top;
    private List<String> hints = new ArrayList<>();

    public AllianceInviteScreen(UUID allianceId, String allianceName, List<String> countries) {
        super(Component.literal("Приглашение в альянс"));
        this.allianceId = allianceId;
        this.allianceName = allianceName;
        this.countries = countries;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(8, height / 2 - 70);

        box = new EditBox(font, left, top + 44, W, 20, Component.literal("Страны"));
        box.setMaxLength(400);
        box.setValue(typed);
        box.setResponder(s -> { typed = s; updateHints(); });
        addRenderableWidget(box);

        sendButton = Button.builder(Component.literal("Пригласить"), b -> submit())
                .bounds(left, top + 92, W / 2 - 2, 20).build();
        addRenderableWidget(sendButton);
        addRenderableWidget(Button.builder(Component.literal("Назад"),
                b -> PoliticsNetwork.CHANNEL.sendToServer(AlliancePackets.Action.simple("info", allianceId)))
                .bounds(left + W / 2 + 2, top + 92, W / 2 - 2, 20).build());
        setInitialFocus(box);
        updateHints();
    }

    private void updateHints() {
        String text = box.getValue();
        int cut = text.lastIndexOf(',');
        String token = text.substring(cut + 1).trim().toLowerCase(Locale.ROOT);
        Set<String> already = new HashSet<>();
        if (cut >= 0) for (String part : text.substring(0, cut).split(",")) already.add(part.trim().toLowerCase(Locale.ROOT));
        List<String> starts = new ArrayList<>(), contains = new ArrayList<>();
        for (String n : countries) {
            String l = n.toLowerCase(Locale.ROOT);
            if (already.contains(l)) continue;
            if (token.isEmpty() || l.startsWith(token)) starts.add(n);
            else if (l.contains(token)) contains.add(n);
        }
        starts.addAll(contains);
        hints = starts.size() > MAX_HINTS ? new ArrayList<>(starts.subList(0, MAX_HINTS)) : starts;
    }

    private void complete(String name) {
        String text = box.getValue();
        int cut = text.lastIndexOf(',');
        String head = cut >= 0 ? text.substring(0, cut + 1) + " " : "";
        box.setValue(head + name + ", ");
        box.moveCursorToEnd();
        updateHints();
    }

    private boolean hintsVisible() { return box != null && box.isFocused() && !hints.isEmpty(); }

    private int hintAt(double mx, double my) {
        if (!hintsVisible()) return -1;
        int y0 = top + 66;
        if (mx < left || mx >= left + W || my < y0 || my >= y0 + hints.size() * HINT_H) return -1;
        return (int) ((my - y0) / HINT_H);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int h = hintAt(mx, my);
        if (h >= 0) { complete(hints.get(h)); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258 && hintsVisible()) { complete(hints.get(0)); return true; } // Tab
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submit() {
        if (box.getValue().trim().isEmpty()) { status = "Укажите хотя бы одну страну."; statusColor = 0xAA0000; return; }
        UUID none = new UUID(0, 0);
        PoliticsNetwork.CHANNEL.sendToServer(new AlliancePackets.Action("invite", allianceId, none, "", "", box.getValue(), "", 0));
        sendButton.active = false;
        status = "Отправка...";
        statusColor = 0x555555;
    }

    @Override
    public void onResult(boolean ok, String message) {
        if (ok) {
            onClose();
        } else {
            status = message;
            statusColor = 0xAA0000;
            sendButton.active = true;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Panel.draw(g, left - 12, top - 8, W + 24, 150);
        g.drawString(font, title, (width - font.width(title)) / 2, top + 4, TEXT, false);
        g.drawString(font, "Альянс «" + allianceName + "»", left, top + 20, TEXT, false);
        g.drawString(font, "Страны через запятую", left, top + 32, TEXT, false);
        if (!status.isEmpty()) Panel.drawWrappedCentered(g, font, status, width / 2, top + 120, W, statusColor, 2);
        super.render(g, mx, my, pt);

        if (hintsVisible()) {
            int y0 = top + 66;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(left - 1, y0 - 1, left + W + 1, y0 + hints.size() * HINT_H + 1, 0xFF000000);
            int hover = hintAt(mx, my);
            for (int i = 0; i < hints.size(); i++) {
                int y = y0 + i * HINT_H;
                g.fill(left, y, left + W, y + HINT_H, i == hover ? 0xFF4A4A6A : 0xFF202020);
                g.drawString(font, hints.get(i), left + 4, y + 2, 0xFFFFFFFF, false);
            }
            g.pose().popPose();
        }
    }
}
