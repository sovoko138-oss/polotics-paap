package com.politics.client;

import com.politics.AlliancePackets;
import com.politics.PoliticsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** The window for creating an alliance: name, "capital", colour, invited countries (with hints) and flag. */
public class AllianceCreateScreen extends Screen implements ResultScreen {
    private static final int TEXT = 0x404040;
    private static final int W = 240;
    private static final int HINT_H = 12, MAX_HINTS = 5;

    private final List<String> countries;
    private EditBox nameBox, capitalBox, rBox, gBox, bBox, invitedBox;
    private Button createButton;
    private String nameText = "", capitalText = "", invitedText = "";
    private String rText, gText, bText;
    private Item flag = Items.WHITE_BANNER;
    private String status = "";
    private int statusColor = 0xAA0000;
    private int left, top;
    private List<String> hints = new ArrayList<>();

    public AllianceCreateScreen(List<String> countries) {
        super(Component.literal("Создание альянса"));
        this.countries = countries;
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
        top = Math.max(8, height / 2 - 125);

        nameBox = new EditBox(font, left, top + 31, W, 20, Component.literal("Название альянса"));
        nameBox.setMaxLength(32);
        nameBox.setValue(nameText);
        addRenderableWidget(nameBox);

        capitalBox = new EditBox(font, left, top + 67, W, 20, Component.literal("Столица альянса"));
        capitalBox.setMaxLength(32);
        capitalBox.setValue(capitalText);
        addRenderableWidget(capitalBox);

        rBox = colourBox(left, top + 103, "R", rText);
        gBox = colourBox(left + 56, top + 103, "G", gText);
        bBox = colourBox(left + 112, top + 103, "B", bText);

        invitedBox = new EditBox(font, left, top + 139, W, 20, Component.literal("Страны"));
        invitedBox.setMaxLength(400);
        invitedBox.setValue(invitedText);
        invitedBox.setResponder(s -> updateHints());
        addRenderableWidget(invitedBox);

        addRenderableWidget(Button.builder(Component.literal("Выбрать флаг"), b -> {
            saveTexts();
            minecraft.setScreen(new FlagSelectScreen(this, flag, item -> flag = item));
        }).bounds(left, top + 172, 100, 20).build());

        createButton = Button.builder(Component.literal("Создать альянс"), b -> submit())
                .bounds(left, top + 200, W, 20).build();
        addRenderableWidget(createButton);

        setInitialFocus(nameBox);
        updateHints();
    }

    private void saveTexts() {
        nameText = nameBox.getValue();
        capitalText = capitalBox.getValue();
        invitedText = invitedBox.getValue();
        rText = rBox.getValue();
        gText = gBox.getValue();
        bText = bBox.getValue();
    }

    /** Hints for the country that is being typed, like the suggestions of a command. */
    private void updateHints() {
        String text = invitedBox.getValue();
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
        String text = invitedBox.getValue();
        int cut = text.lastIndexOf(',');
        String head = cut >= 0 ? text.substring(0, cut + 1) + " " : "";
        invitedBox.setValue(head + name + ", ");
        invitedBox.moveCursorToEnd();
        updateHints();
    }

    private boolean hintsVisible() { return invitedBox != null && invitedBox.isFocused() && !hints.isEmpty(); }

    private int hintAt(double mx, double my) {
        if (!hintsVisible()) return -1;
        int y0 = top + 161;
        if (mx < left || mx >= left + W || my < y0 || my >= y0 + hints.size() * HINT_H) return -1;
        return (int) ((my - y0) / HINT_H);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int h = hintAt(mx, my);
        if (h >= 0) {
            complete(hints.get(h));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258 && hintsVisible()) { // Tab takes the first hint
            complete(hints.get(0));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submit() {
        saveTexts();
        if (nameText.trim().isEmpty()) { status = "Введите название альянса."; statusColor = 0xAA0000; return; }
        if (capitalText.trim().isEmpty()) { status = "Введите название «столицы» альянса."; statusColor = 0xAA0000; return; }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(flag);
        UUID none = new UUID(0, 0);
        PoliticsNetwork.CHANNEL.sendToServer(new AlliancePackets.Action("create", none, none, nameText, capitalText, invitedText,
                key == null ? "minecraft:white_banner" : key.toString(), currentColor()));
        createButton.active = false;
        status = "Создание...";
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
        Panel.draw(g, left - 12, top - 8, W + 24, 262);
        g.drawString(font, title, (width - font.width(title)) / 2, top + 4, TEXT, false);
        g.drawString(font, "Название альянса", left, top + 20, TEXT, false);
        g.drawString(font, "«Столица» альянса", left, top + 56, TEXT, false);
        g.drawString(font, "Цвет альянса (R, G, B от 1 до 255)", left, top + 92, TEXT, false);
        g.drawString(font, "Страны, приглашённые в альянс (через запятую)", left, top + 128, TEXT, false);

        int cx0 = left + 172;
        g.fill(cx0 - 1, top + 102, cx0 + 61, top + 124, 0xFF000000);
        g.fill(cx0, top + 103, cx0 + 60, top + 123, 0xFF000000 | currentColor());

        g.renderItem(new ItemStack(flag), left + 108, top + 174);
        Panel.drawWrappedMiddle(g, font, new ItemStack(flag).getHoverName(), left + 128, top + 182, W - 128, TEXT, 3);

        if (!status.isEmpty()) Panel.drawWrappedCentered(g, font, status, width / 2, top + 228, W, statusColor, 2);
        super.render(g, mx, my, pt);

        if (hintsVisible()) {
            int y0 = top + 161;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300); // above the buttons
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
