package com.politics.client;

import com.politics.GiveChunkService;
import com.politics.PoliticsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /givechunk: "are you sure?" and the country the chunk goes to, with hints while typing. */
public class GiveChunkScreen extends Screen implements ResultScreen {
    private static final int TEXT = 0x404040;
    private static final int W = 250, HINT_H = 12, MAX_HINTS = 5;

    private final int cx, cz;
    private final List<String> countries;
    private EditBox box;
    private Button giveButton;
    private String typed = "", status = "";
    private int statusColor = 0xAA0000;
    private int left, top;
    private List<String> hints = new ArrayList<>();

    public GiveChunkScreen(int cx, int cz, List<String> countries) {
        super(Component.literal("Передача чанка"));
        this.cx = cx;
        this.cz = cz;
        this.countries = countries;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(8, height / 2 - 85);

        box = new EditBox(font, left, top + 82, W, 20, Component.literal("Страна"));
        box.setMaxLength(64);
        box.setValue(typed);
        box.setResponder(s -> { typed = s; updateHints(); });
        addRenderableWidget(box);

        giveButton = Button.builder(Component.literal("Передать чанк"), b -> submit())
                .bounds(left, top + 140, W / 2 - 2, 20).build();
        addRenderableWidget(giveButton);
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> onClose())
                .bounds(left + W / 2 + 2, top + 140, W / 2 - 2, 20).build());
        setInitialFocus(box);
        updateHints();
    }

    private void updateHints() {
        String token = box.getValue().trim().toLowerCase(Locale.ROOT);
        List<String> starts = new ArrayList<>(), contains = new ArrayList<>();
        for (String n : countries) {
            String l = n.toLowerCase(Locale.ROOT);
            if (l.equals(token)) continue; // already typed in full
            if (token.isEmpty() || l.startsWith(token)) starts.add(n);
            else if (l.contains(token)) contains.add(n);
        }
        starts.addAll(contains);
        hints = starts.size() > MAX_HINTS ? new ArrayList<>(starts.subList(0, MAX_HINTS)) : starts;
    }

    private boolean hintsVisible() { return box != null && box.isFocused() && !hints.isEmpty(); }

    private int hintAt(double mx, double my) {
        if (!hintsVisible()) return -1;
        int y0 = top + 104;
        if (mx < left || mx >= left + W || my < y0 || my >= y0 + hints.size() * HINT_H) return -1;
        return (int) ((my - y0) / HINT_H);
    }

    private void pick(String name) {
        box.setValue(name);
        box.moveCursorToEnd();
        updateHints();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int h = hintAt(mx, my);
        if (h >= 0) { pick(hints.get(h)); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258 && hintsVisible()) { pick(hints.get(0)); return true; } // Tab
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submit() {
        String name = box.getValue().trim();
        if (name.isEmpty()) { status = "Выберите страну."; statusColor = 0xAA0000; return; }
        PoliticsNetwork.CHANNEL.sendToServer(new GiveChunkService.Confirm(cx, cz, name));
        giveButton.active = false;
        status = "Передача...";
        statusColor = 0x555555;
    }

    @Override
    public void onResult(boolean ok, String message) {
        if (ok) {
            onClose();
        } else {
            status = message;
            statusColor = 0xAA0000;
            giveButton.active = true;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Panel.draw(g, left - 12, top - 8, W + 24, 186);
        g.drawString(font, title, (width - font.width(title)) / 2, top + 2, TEXT, false);

        int y = top + 16;
        for (FormattedCharSequence line : font.split(Component.literal(
                "Вы уверены, что хотите отдать именно этот чанк? Выберите страну, которой хотите передать чанк"), W)) {
            g.drawString(font, line, left, y, TEXT, false);
            y += 10;
        }
        g.drawString(font, "Чанк: " + cx + ", " + cz + "  (блоки " + (cx * 16) + ", " + (cz * 16) + ")", left, top + 56, 0x707070, false);
        g.drawString(font, "Страна, которой передать чанк:", left, top + 70, TEXT, false);
        if (!status.isEmpty()) Panel.drawWrappedCentered(g, font, status, width / 2, top + 166, W, statusColor, 2);
        super.render(g, mx, my, pt);

        if (hintsVisible()) {
            int y0 = top + 104;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(left - 1, y0 - 1, left + W + 1, y0 + hints.size() * HINT_H + 1, 0xFF000000);
            int hover = hintAt(mx, my);
            for (int i = 0; i < hints.size(); i++) {
                int hy = y0 + i * HINT_H;
                g.fill(left, hy, left + W, hy + HINT_H, i == hover ? 0xFF4A4A6A : 0xFF202020);
                g.drawString(font, hints.get(i), left + 4, hy + 2, 0xFFFFFFFF, false);
            }
            g.pose().popPose();
        }
    }
}
