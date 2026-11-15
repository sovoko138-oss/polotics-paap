package com.politics.client;

import com.politics.CreateCountryPacket;
import com.politics.PoliticsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public class CreateCountryScreen extends Screen {
    private final BlockPos pos;
    private EditBox nameBox, capitalBox, deputiesBox;
    private Button createButton;
    private Item flag = Items.WHITE_BANNER;
    private String nameText = "", capitalText = "", deputiesText = "";
    private String status = "";
    private int statusColor = 0xFF5555;

    private int left, top;
    private static final int W = 240;

    public CreateCountryScreen(BlockPos pos) {
        super(Component.literal("Создание страны"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(8, height / 2 - 112);

        nameBox = new EditBox(font, left, top + 34, W, 20, Component.literal("Название страны"));
        nameBox.setMaxLength(32);
        nameBox.setValue(nameText);
        addRenderableWidget(nameBox);

        capitalBox = new EditBox(font, left, top + 74, W, 20, Component.literal("Название столицы"));
        capitalBox.setMaxLength(32);
        capitalBox.setValue(capitalText);
        addRenderableWidget(capitalBox);

        deputiesBox = new EditBox(font, left, top + 154, W, 20, Component.literal("Заместители"));
        deputiesBox.setMaxLength(200);
        deputiesBox.setValue(deputiesText);
        addRenderableWidget(deputiesBox);

        addRenderableWidget(Button.builder(Component.literal("Выбрать флаг"), b -> {
            saveTexts();
            minecraft.setScreen(new FlagSelectScreen(this, flag, item -> flag = item));
        }).bounds(left, top + 184, 150, 20).build());

        createButton = Button.builder(Component.literal("Создать страну"), b -> submit())
                .bounds(left, top + 212, W, 20).build();
        addRenderableWidget(createButton);

        setInitialFocus(nameBox);
    }

    private void saveTexts() {
        nameText = nameBox.getValue();
        capitalText = capitalBox.getValue();
        deputiesText = deputiesBox.getValue();
    }

    private void submit() {
        saveTexts();
        if (nameText.trim().isEmpty() || capitalText.trim().isEmpty()) {
            status = "Заполните название страны и столицы.";
            statusColor = 0xFF5555;
            return;
        }
        var key = ForgeRegistries.ITEMS.getKey(flag);
        PoliticsNetwork.CHANNEL.sendToServer(new CreateCountryPacket(pos, nameText, capitalText,
                deputiesText, key == null ? "minecraft:white_banner" : key.toString()));
        createButton.active = false;
        status = "Отправка...";
        statusColor = 0xAAAAAA;
    }

    public void onResult(boolean ok, String message) {
        if (ok) {
            onClose();
        } else {
            status = message;
            statusColor = 0xFF5555;
            createButton.active = true;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 12, top - 8, left + W + 12, top + 240, 0xD0101010);
        g.drawCenteredString(font, title, width / 2, top + 4, 0xFFFFFF);

        g.drawString(font, "Название страны", left, top + 22, 0xAAAAAA);
        g.drawString(font, "Название столицы", left, top + 62, 0xAAAAAA);
        g.drawString(font, "Президент", left, top + 102, 0xAAAAAA);
        String president = minecraft.player == null ? "" : minecraft.player.getGameProfile().getName();
        g.drawString(font, president, left, top + 114, 0xFFFFFF);
        g.drawString(font, "Заместители (ники через запятую)", left, top + 142, 0xAAAAAA);

        super.render(g, mx, my, pt);

        g.renderItem(new ItemStack(flag), left + 158, top + 186);
        g.drawString(font, new ItemStack(flag).getHoverName(), left + 178, top + 190, 0xFFFFFF, false);

        if (!status.isEmpty()) {
            g.drawCenteredString(font, status, width / 2, top + 236, statusColor);
        }
    }
}
