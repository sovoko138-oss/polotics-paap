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

public class CreateCountryScreen extends Screen implements ResultScreen {
    private static final int TEXT = 0x404040;

    private final BlockPos pos;
    private EditBox nameBox, capitalBox, deputiesBox, rBox, gBox, bBox;
    private Button createButton;
    private Item flag = Items.WHITE_BANNER;
    private String nameText = "", capitalText = "", deputiesText = "";
    private String rText, gText, bText;
    private String status = "";
    private int statusColor = 0xAA0000;

    private int left, top;
    private static final int W = 240;

    public CreateCountryScreen(BlockPos pos) {
        super(Component.literal("Создание страны"));
        this.pos = pos;
        int rgb = net.minecraft.util.Mth.hsvToRgb((float) Math.random(), 0.85f, 1f);
        rText = String.valueOf(Math.max(1, rgb >> 16 & 0xFF));
        gText = String.valueOf(Math.max(1, rgb >> 8 & 0xFF));
        bText = String.valueOf(Math.max(1, rgb & 0xFF));
    }

    private static int channel(EditBox box) {
        try {
            return Math.max(1, Math.min(255, Integer.parseInt(box.getValue().trim())));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private int currentColor() { return (channel(rBox) << 16) | (channel(gBox) << 8) | channel(bBox); }

    private EditBox colourBox(int x, int y, String name, String value) {
        EditBox box = new EditBox(font, x, y, 46, 20, Component.literal(name));
        box.setMaxLength(3);
        box.setFilter(s -> s.matches("\\d{0,3}"));
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(8, height / 2 - 130);

        nameBox = new EditBox(font, left, top + 34, W, 20, Component.literal("Название страны"));
        nameBox.setMaxLength(32);
        nameBox.setValue(nameText);
        addRenderableWidget(nameBox);

        capitalBox = new EditBox(font, left, top + 74, W, 20, Component.literal("Название столицы"));
        capitalBox.setMaxLength(32);
        capitalBox.setValue(capitalText);
        addRenderableWidget(capitalBox);

        deputiesBox = new EditBox(font, left, top + 190, W, 20, Component.literal("Заместители"));
        deputiesBox.setMaxLength(200);
        deputiesBox.setValue(deputiesText);
        addRenderableWidget(deputiesBox);

        rBox = colourBox(left, top + 142, "R", rText);
        gBox = colourBox(left + 56, top + 142, "G", gText);
        bBox = colourBox(left + 112, top + 142, "B", bText);

        addRenderableWidget(Button.builder(Component.literal("Выбрать флаг"), b -> {
            saveTexts();
            minecraft.setScreen(new FlagSelectScreen(this, flag, item -> flag = item));
        }).bounds(left, top + 220, 100, 20).build());

        createButton = Button.builder(Component.literal("Создать страну"), b -> submit())
                .bounds(left, top + 248, W, 20).build();
        addRenderableWidget(createButton);

        setInitialFocus(nameBox);
    }

    private void saveTexts() {
        nameText = nameBox.getValue();
        capitalText = capitalBox.getValue();
        deputiesText = deputiesBox.getValue();
        rText = rBox.getValue();
        gText = gBox.getValue();
        bText = bBox.getValue();
    }

    private void submit() {
        saveTexts();
        if (nameText.trim().isEmpty() || capitalText.trim().isEmpty()) {
            status = "Заполните название страны и столицы.";
            statusColor = 0xAA0000;
            return;
        }
        var key = ForgeRegistries.ITEMS.getKey(flag);
        PoliticsNetwork.CHANNEL.sendToServer(new CreateCountryPacket(pos, nameText, capitalText,
                deputiesText, key == null ? "minecraft:white_banner" : key.toString(), currentColor()));
        createButton.active = false;
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
            createButton.active = true;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Panel.draw(g, left - 12, top - 8, W + 24, 308);
        g.drawString(font, title, (width - font.width(title)) / 2, top + 4, TEXT, false);

        g.drawString(font, "Название страны", left, top + 22, TEXT, false);
        g.drawString(font, "Название столицы", left, top + 62, TEXT, false);
        g.drawString(font, "Президент", left, top + 102, TEXT, false);
        String president = minecraft.player == null ? "" : minecraft.player.getGameProfile().getName();
        g.drawString(font, president, left, top + 114, 0x202020, false);
        g.drawString(font, "Цвет страны (R, G, B от 1 до 255)", left, top + 130, TEXT, false);
        g.drawString(font, "Заместители (ники через запятую)", left, top + 178, TEXT, false);

        super.render(g, mx, my, pt);

        // the resulting colour of the country
        int cx0 = left + 172;
        g.fill(cx0 - 1, top + 141, cx0 + 61, top + 163, 0xFF000000);
        g.fill(cx0, top + 142, cx0 + 60, top + 162, 0xFF000000 | currentColor());

        g.renderItem(new ItemStack(flag), left + 108, top + 222);
        Panel.drawWrappedMiddle(g, font, new ItemStack(flag).getHoverName(), left + 128, top + 230, W - 128, TEXT, 3);

        if (!status.isEmpty()) {
            Panel.drawWrappedCentered(g, font, status, width / 2, top + 274, W, statusColor, 2);
        }
    }
}
