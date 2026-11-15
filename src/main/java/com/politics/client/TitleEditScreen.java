package com.politics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** A small window for typing a title (звание) for a resident. An empty title removes it. */
public class TitleEditScreen extends Screen {
    private static final int W = 220;

    private final Screen parent;
    private final String playerName;
    private final String initial;
    private final Consumer<String> onSave;
    private EditBox box;
    private int left, top;

    public TitleEditScreen(Screen parent, String playerName, String initial, Consumer<String> onSave) {
        super(Component.literal("Звание игрока " + playerName));
        this.parent = parent;
        this.playerName = playerName;
        this.initial = initial;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(8, height / 2 - 50);

        box = new EditBox(font, left, top + 36, W, 20, Component.literal("Звание"));
        box.setMaxLength(24);
        box.setValue(initial);
        box.setHint(Component.literal("Пусто - убрать звание"));
        addRenderableWidget(box);
        setInitialFocus(box);

        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> {
            onSave.accept(box.getValue());
            minecraft.setScreen(parent);
        }).bounds(left, top + 64, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> minecraft.setScreen(parent))
                .bounds(left + 114, top + 64, 106, 20).build());
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Panel.draw(g, left - 12, top - 8, W + 24, 104);
        Panel.drawWrappedCentered(g, font, title.getString(), width / 2, top + 4, W, 0x404040, 2);
        g.drawString(font, "Звание (до 24 символов)", left, top + 25, 0x404040, false);
        super.render(g, mx, my, pt);
    }
}
