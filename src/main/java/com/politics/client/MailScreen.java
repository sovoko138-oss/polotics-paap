package com.politics.client;

import com.politics.MailService;
import com.politics.PoliticsNetwork;
import com.politics.SendMailPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** The window for writing a letter to the president of another country (/mail). */
public class MailScreen extends Screen implements ResultScreen {
    private static final int TEXT = 0x404040;
    private static final int W = 240;
    private static final int BOX_H = 90;

    private final UUID countryId;
    private final String countryName, presidentName;
    private final int color;
    private MultiLineEditBox box;
    private Button sendButton;
    private String saved = "";
    private String status = "";
    private int statusColor = 0xAA0000;
    private int left, top;

    public MailScreen(UUID countryId, String countryName, String presidentName, int color) {
        super(Component.literal("Письмо"));
        this.countryId = countryId;
        this.countryName = countryName;
        this.presidentName = presidentName;
        this.color = color & 0xFFFFFF;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(8, height / 2 - 95);

        box = new MultiLineEditBox(font, left, top + 50, W, BOX_H,
                Component.literal("Текст письма..."), Component.literal("Письмо"));
        box.setCharacterLimit(MailService.MAX_TEXT);
        box.setValue(saved);
        addRenderableWidget(box);

        sendButton = Button.builder(Component.literal("Отправить"), b -> submit())
                .bounds(left, top + 50 + BOX_H + 8, W / 2 - 2, 20).build();
        addRenderableWidget(sendButton);
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> onClose())
                .bounds(left + W / 2 + 2, top + 50 + BOX_H + 8, W / 2 - 2, 20).build());

        setInitialFocus(box);
        box.setFocused(true);
    }

    private void submit() {
        saved = box.getValue();
        if (saved.trim().isEmpty()) {
            status = "Напишите текст письма.";
            statusColor = 0xAA0000;
            return;
        }
        PoliticsNetwork.CHANNEL.sendToServer(new SendMailPacket(countryId, saved));
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
        Panel.draw(g, left - 12, top - 8, W + 24, 50 + BOX_H + 28 + 38);
        g.drawString(font, title, (width - font.width(title)) / 2, top + 4, TEXT, false);
        g.drawString(font, "Кому: президенту страны", left, top + 22, TEXT, false);
        g.drawString(font, countryName + " (" + presidentName + ")", left, top + 34, color == 0xFFFFFF ? TEXT : darken(color), false);

        super.render(g, mx, my, pt);

        if (!status.isEmpty()) {
            Panel.drawWrappedCentered(g, font, status, width / 2, top + 50 + BOX_H + 34, W, statusColor, 2);
        }
    }

    /** Country colours are bright; make them readable on the grey panel. */
    private static int darken(int rgb) {
        int r = (rgb >> 16 & 0xFF) * 3 / 5, gr = (rgb >> 8 & 0xFF) * 3 / 5, b = (rgb & 0xFF) * 3 / 5;
        return (r << 16) | (gr << 8) | b;
    }
}
