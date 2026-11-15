package com.politics.client;

import com.politics.ChangeCityPacket;
import com.politics.PoliticsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

public class ChangeCityScreen extends Screen implements ResultScreen {
    private static final int TEXT = 0x404040;
    private static final int W = 220;

    private final UUID countryId;
    private EditBox nameBox, mayorBox;
    private Button saveButton;
    private Item flag;
    private String nameText, mayorText;
    private String status = "";
    private int statusColor = 0xAA0000;
    private int left, top;

    public ChangeCityScreen(UUID countryId, String cityName, String mayor, Item flag) {
        super(Component.literal("Изменить город"));
        this.countryId = countryId;
        this.nameText = cityName;
        this.mayorText = mayor;
        this.flag = flag == null || flag == Items.AIR ? Items.WHITE_BANNER : flag;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(8, height / 2 - 90);

        nameBox = new EditBox(font, left, top + 34, W, 20, Component.literal("Название города"));
        nameBox.setMaxLength(32);
        nameBox.setValue(nameText);
        addRenderableWidget(nameBox);

        mayorBox = new EditBox(font, left, top + 74, W, 20, Component.literal("Мэр"));
        mayorBox.setMaxLength(16);
        mayorBox.setValue(mayorText);
        addRenderableWidget(mayorBox);

        addRenderableWidget(Button.builder(Component.literal("Выбрать флаг"), b -> {
            saveTexts();
            minecraft.setScreen(new FlagSelectScreen(this, flag, item -> flag = item));
        }).bounds(left, top + 104, 90, 20).build());

        saveButton = Button.builder(Component.literal("Сохранить"), b -> submit())
                .bounds(left, top + 134, W, 20).build();
        addRenderableWidget(saveButton);

        setInitialFocus(nameBox);
    }

    private void saveTexts() {
        nameText = nameBox.getValue();
        mayorText = mayorBox.getValue();
    }

    private void submit() {
        saveTexts();
        if (nameText.trim().isEmpty() || mayorText.trim().isEmpty()) {
            status = "Заполните название города и мэра.";
            statusColor = 0xAA0000;
            return;
        }
        var key = ForgeRegistries.ITEMS.getKey(flag);
        PoliticsNetwork.CHANNEL.sendToServer(new ChangeCityPacket(countryId, nameText, mayorText,
                key == null ? "minecraft:white_banner" : key.toString()));
        saveButton.active = false;
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
            saveButton.active = true;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Panel.draw(g, left - 12, top - 8, W + 24, 196);
        g.drawString(font, title, (width - font.width(title)) / 2, top + 4, TEXT, false);
        g.drawString(font, "Название города", left, top + 22, TEXT, false);
        g.drawString(font, "Мэр (ник)", left, top + 62, TEXT, false);

        super.render(g, mx, my, pt);

        g.renderItem(new ItemStack(flag), left + 98, top + 106);
        Panel.drawWrappedMiddle(g, font, new ItemStack(flag).getHoverName(), left + 118, top + 114, W - 118, TEXT, 3);

        if (!status.isEmpty()) {
            Panel.drawWrappedCentered(g, font, status, width / 2, top + 160, W, statusColor, 2);
        }
    }
}
