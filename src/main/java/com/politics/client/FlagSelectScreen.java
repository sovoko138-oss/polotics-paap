package com.politics.client;

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
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class FlagSelectScreen extends Screen {
    private static final int COLS = 9, ROWS = 5, CELL = 20;

    private final Screen parent;
    private final Consumer<Item> onPick;
    private final Item current;

    private final List<Item> allItems = new ArrayList<>();
    private final List<String> allSearch = new ArrayList<>();
    private List<Item> filtered = new ArrayList<>();
    private int page = 0;
    private EditBox search;
    private int gridLeft, gridTop;

    public FlagSelectScreen(Screen parent, Item current, Consumer<Item> onPick) {
        super(Component.literal("Выбор флага"));
        this.parent = parent;
        this.current = current;
        this.onPick = onPick;

        for (Item item : ForgeRegistries.ITEMS) {
            if (item == Items.AIR) continue;
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null) continue;
            allItems.add(item);
        }
        allItems.sort((a, b) -> ForgeRegistries.ITEMS.getKey(a).toString()
                .compareTo(ForgeRegistries.ITEMS.getKey(b).toString()));
        for (Item item : allItems) {
            allSearch.add((ForgeRegistries.ITEMS.getKey(item).getPath() + " "
                    + new ItemStack(item).getHoverName().getString()).toLowerCase(Locale.ROOT));
        }
        filtered = new ArrayList<>(allItems);
    }

    @Override
    protected void init() {
        gridLeft = (width - COLS * CELL) / 2;
        gridTop = Math.max(60, height / 2 - (ROWS * CELL) / 2);

        search = new EditBox(font, gridLeft, gridTop - 30, COLS * CELL, 18, Component.literal("Поиск"));
        search.setMaxLength(50);
        search.setHint(Component.literal("Поиск предмета..."));
        search.setResponder(s -> applyFilter());
        addRenderableWidget(search);
        setInitialFocus(search);

        int by = gridTop + ROWS * CELL + 14;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1))
                .bounds(gridLeft, by, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1))
                .bounds(gridLeft + COLS * CELL - 30, by, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> minecraft.setScreen(parent))
                .bounds(width / 2 - 40, by, 80, 20).build());
    }

    private void applyFilter() {
        String q = search.getValue().toLowerCase(Locale.ROOT).trim();
        List<Item> res = new ArrayList<>();
        for (int i = 0; i < allItems.size(); i++) {
            if (q.isEmpty() || allSearch.get(i).contains(q)) res.add(allItems.get(i));
        }
        filtered = res;
        page = 0;
    }

    private int pageCount() {
        return Math.max(1, (filtered.size() + COLS * ROWS - 1) / (COLS * ROWS));
    }

    private void changePage(int d) {
        page = Math.max(0, Math.min(pageCount() - 1, page + d));
    }

    private int cellAt(double mx, double my) {
        int cx = (int) Math.floor((mx - gridLeft) / CELL);
        int cy = (int) Math.floor((my - gridTop) / CELL);
        if (mx < gridLeft || my < gridTop || cx < 0 || cx >= COLS || cy < 0 || cy >= ROWS) return -1;
        int idx = page * COLS * ROWS + cy * COLS + cx;
        return idx < filtered.size() ? idx : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int idx = cellAt(mx, my);
        if (idx >= 0) {
            onPick.accept(filtered.get(idx));
            minecraft.setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        changePage(delta > 0 ? -1 : 1);
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Panel.draw(g, gridLeft - 10, gridTop - 52, COLS * CELL + 20, ROWS * CELL + 96);
        g.drawString(font, title, (width - font.width(title)) / 2, gridTop - 44, 0x404040, false);

        int hover = cellAt(mx, my);
        for (int i = 0; i < COLS * ROWS; i++) {
            Panel.slot(g, gridLeft + (i % COLS) * CELL + 1, gridTop + (i / COLS) * CELL + 1);
        }
        int start = page * COLS * ROWS;
        for (int i = 0; i < COLS * ROWS && start + i < filtered.size(); i++) {
            Item item = filtered.get(start + i);
            int x = gridLeft + (i % COLS) * CELL;
            int y = gridTop + (i / COLS) * CELL;
            if (item == current) g.fill(x, y, x + CELL, y + CELL, 0x8055FF55);
            if (start + i == hover) g.fill(x, y, x + CELL, y + CELL, 0x80FFFFFF);
            g.renderItem(new ItemStack(item), x + 2, y + 2);
        }

        super.render(g, mx, my, pt);

        String pg = (page + 1) + " / " + pageCount();
        g.drawString(font, pg, (width - font.width(pg)) / 2, gridTop + ROWS * CELL + 3, 0x404040, false);

        if (hover >= 0) g.renderTooltip(font, new ItemStack(filtered.get(hover)), mx, my);
    }
}
