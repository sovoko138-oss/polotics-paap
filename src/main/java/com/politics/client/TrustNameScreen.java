package com.politics.client;

import com.politics.PoliticsNetwork;
import com.politics.TrustPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** First /country trust window: type a nickname (or pick an already trusted player). */
public class TrustNameScreen extends Screen implements ResultScreen {
    private static final int TEXT = 0x404040;
    private static final int W = 220, MAX_LIST = 6, LIST_ROW = 12;

    private final UUID countryId;
    private final String countryName;
    private final List<String> trusted;
    private EditBox box;
    private String status = "";
    private int statusColor = 0xAA0000;
    private int left, top;

    public TrustNameScreen(UUID countryId, String countryName, List<String> trusted) {
        super(Component.literal("Доверенные игроки"));
        this.countryId = countryId;
        this.countryName = countryName;
        this.trusted = trusted;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(6, height / 2 - 100);

        box = new EditBox(font, left, top + 36, W, 20, Component.literal("Ник игрока"));
        box.setMaxLength(16);
        box.setHint(Component.literal("Ник игрока"));
        addRenderableWidget(box);
        setInitialFocus(box);

        addRenderableWidget(Button.builder(Component.literal("Далее"), b -> submit(box.getValue()))
                .bounds(left, top + 62, W, 20).build());
    }

    private void submit(String nick) {
        nick = nick.trim();
        if (nick.isEmpty()) {
            status = "Введите ник игрока.";
            statusColor = 0xAA0000;
            return;
        }
        PoliticsNetwork.CHANNEL.sendToServer(new TrustPackets.Request(countryId, nick));
        status = "";
    }

    @Override
    public void onResult(boolean ok, String message) {
        status = message;
        statusColor = ok ? 0x1E7B1E : 0xAA0000;
    }

    private int listY() { return top + 112; }

    private int rowAt(double mx, double my) {
        if (mx < left || mx >= left + W || my < listY()) return -1;
        int i = (int) ((my - listY()) / LIST_ROW);
        return i >= 0 && i < Math.min(MAX_LIST, trusted.size()) ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int i = rowAt(mx, my);
        if (i >= 0) {
            submit(trusted.get(i));
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Panel.draw(g, left - 12, top - 8, W + 24, 206);
        g.drawString(font, title, (width - font.width(title)) / 2, top + 4, TEXT, false);
        g.drawString(font, "Страна: " + countryName, left, top + 16, 0x606060, false);
        g.drawString(font, "Ник игрока", left, top + 26, TEXT, false);

        if (!status.isEmpty()) {
            Panel.drawWrappedCentered(g, font, status, width / 2, top + 88, W, statusColor, 2);
        }

        g.drawString(font, trusted.isEmpty() ? "Доверенных игроков пока нет." : "Уже доверенные (нажмите, чтобы изменить):",
                left, top + 100, TEXT, false);
        int hover = rowAt(mx, my);
        for (int i = 0; i < Math.min(MAX_LIST, trusted.size()); i++) {
            int y = listY() + i * LIST_ROW;
            if (i == hover) g.fill(left, y, left + W, y + LIST_ROW, 0x40FFFFFF);
            g.drawString(font, trusted.get(i), left + 4, y + 2, 0x202020, false);
        }
        if (trusted.size() > MAX_LIST) {
            g.drawString(font, "... и ещё " + (trusted.size() - MAX_LIST) + " (введите ник)", left + 4,
                    listY() + MAX_LIST * LIST_ROW + 2, 0x606060, false);
        }
        super.render(g, mx, my, pt);
    }
}
