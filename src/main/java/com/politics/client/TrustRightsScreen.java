package com.politics.client;

import com.politics.PoliticsNetwork;
import com.politics.Rights;
import com.politics.TrustPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Second /country trust window: what the trusted player may do inside the country. */
public class TrustRightsScreen extends Screen implements ResultScreen {
    private static final int TEXT = 0x404040;
    private static final int W = 220;

    private static final int[] BITS = {Rights.BREAK, Rights.PLACE, Rights.DOORS, Rights.BUTTONS, Rights.SUMMON};
    private static final String[] NAMES = {
            "Ломать блоки", "Ставить блоки", "Открывать двери и люки",
            "Нажимать кнопки и плиты", "Призывать сущностей"};

    private final UUID countryId;
    private final String nick;
    private int rights;
    private final Button[] toggles = new Button[BITS.length];
    private Button fullButton, saveButton;
    private String status = "";
    private int statusColor = 0xAA0000;
    private int left, top;

    public TrustRightsScreen(UUID countryId, String nick, int rights) {
        super(Component.literal("Права игрока " + nick));
        this.countryId = countryId;
        this.nick = nick;
        this.rights = rights & Rights.FULL;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(6, height / 2 - 115);

        for (int i = 0; i < BITS.length; i++) {
            final int bit = BITS[i];
            toggles[i] = Button.builder(Component.empty(), b -> {
                rights ^= bit;
                refresh();
            }).bounds(left, top + 28 + i * 24, W, 20).build();
            addRenderableWidget(toggles[i]);
        }
        fullButton = Button.builder(Component.empty(), b -> {
            rights = rights == Rights.FULL ? 0 : Rights.FULL;
            refresh();
        }).bounds(left, top + 28 + BITS.length * 24 + 4, W, 20).build();
        addRenderableWidget(fullButton);

        int by = top + 28 + BITS.length * 24 + 34;
        saveButton = Button.builder(Component.literal("Сохранить"), b -> {
            PoliticsNetwork.CHANNEL.sendToServer(new TrustPackets.Set(countryId, nick, rights));
            saveButton.active = false;
            status = "";
        }).bounds(left, by, 106, 20).build();
        addRenderableWidget(saveButton);
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> onClose())
                .bounds(left + 114, by, 106, 20).build());
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < BITS.length; i++) {
            toggles[i].setMessage(label(NAMES[i], (rights & BITS[i]) != 0));
        }
        fullButton.setMessage(label("Полный доступ", rights == Rights.FULL));
    }

    private static Component label(String text, boolean on) {
        return on ? Component.literal("[x] " + text).withStyle(ChatFormatting.GREEN)
                  : Component.literal("[ ] " + text);
    }

    @Override
    public void onResult(boolean ok, String message) {
        if (ok) {
            onClose();
        } else {
            status = message;
            statusColor = 0xAA0000;
            saveButton.active = true;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int h = 28 + BITS.length * 24 + 34 + 20 + 28;
        Panel.draw(g, left - 12, top - 8, W + 24, h);
        Panel.drawWrappedCentered(g, font, title.getString(), width / 2, top + 4, W, TEXT, 1);
        g.drawString(font, "Что разрешено на территории страны", left, top + 17, 0x606060, false);
        super.render(g, mx, my, pt);
        if (!status.isEmpty()) {
            Panel.drawWrappedCentered(g, font, status, width / 2, top + h - 40, W, statusColor, 2);
        }
    }
}
