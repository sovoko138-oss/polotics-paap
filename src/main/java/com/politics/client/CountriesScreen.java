package com.politics.client;

import com.politics.CountriesPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/** A chest-like window listing all countries; clicking a name shows its details. */
public class CountriesScreen extends Screen {
    private static final int W = 248, H = 222;
    private static final int ROWS = 8, ROW_H = 18;
    private static final int GRAY = 0xAAAAAA;

    private final List<CountriesPacket.Info> list;
    private int page = 0;
    private int selected = -1;
    private int left, top;

    /** The screen to go back to (the alliance window), or null. */
    private final Screen parent;

    public CountriesScreen(List<CountriesPacket.Info> list) {
        this(list, "", null);
    }

    public CountriesScreen(List<CountriesPacket.Info> list, String selectedName, Screen parent) {
        super(Component.literal("Страны"));
        this.list = list;
        this.parent = parent;
        for (int i = 0; i < list.size(); i++) if (list.get(i).name.equals(selectedName)) selected = i;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(8, height / 2 - H / 2);
        int by = top + H - 30;
        if (selected < 0) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1))
                    .bounds(left + 10, by, 30, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1))
                    .bounds(left + W - 40, by, 30, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                    .bounds(left + W / 2 - 40, by, 80, 20).build());
        } else {
            if (parent != null) {
                addRenderableWidget(Button.builder(Component.literal("Назад"), b -> minecraft.setScreen(parent))
                        .bounds(left + W / 2 - 60, by, 120, 20).build());
            } else {
                addRenderableWidget(Button.builder(Component.literal("Назад к списку"), b -> {
                    selected = -1;
                    rebuildWidgets();
                }).bounds(left + W / 2 - 60, by, 120, 20).build());
            }
        }
    }

    private int pageCount() {
        return Math.max(1, (list.size() + ROWS - 1) / ROWS);
    }

    private void changePage(int d) {
        page = Math.max(0, Math.min(pageCount() - 1, page + d));
    }

    private int rowAt(double mx, double my) {
        int ax = left + 12, ay = top + 24;
        if (mx < ax || mx >= ax + W - 24 || my < ay || my >= ay + ROWS * ROW_H) return -1;
        int idx = page * ROWS + (int) ((my - ay) / ROW_H);
        return idx < list.size() ? idx : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (selected < 0) {
            int idx = rowAt(mx, my);
            if (idx >= 0) {
                selected = idx;
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (selected < 0) changePage(delta > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static Item itemOf(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? Items.AIR : ForgeRegistries.ITEMS.getValue(rl);
        return item == null || item == Items.AIR ? Items.WHITE_BANNER : item;
    }

    private static String plural(int n, String one, String few, String many) {
        int a = n % 10, b = n % 100;
        if (a == 1 && b != 11) return one;
        if (a >= 2 && a <= 4 && (b < 12 || b > 14)) return few;
        return many;
    }

    private static MutableComponent label(String label, String value) {
        return Component.literal(label + ": ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(GRAY)))
                .append(Component.literal(value).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Panel.draw(g, left, top, W, H);
        g.drawString(font, title, (width - font.width(title)) / 2, top + 8, 0x404040, false);

        // dark inset area for the content
        int ax = left + 12, ay = top + 24, aw = W - 24, ah = ROWS * ROW_H;
        g.fill(ax - 1, ay - 1, ax + aw + 1, ay + ah + 1, 0xFF373737);
        g.fill(ax, ay, ax + aw, ay + ah, 0xFF1E1E1E);

        if (selected < 0) renderList(g, mx, my, ax, ay, aw);
        else renderDetails(g, ax, ay, aw);

        super.render(g, mx, my, pt);

        if (selected < 0) {
            String pg = (page + 1) + " / " + pageCount();
            g.drawString(font, pg, (width - font.width(pg)) / 2, top + H - 44, 0x404040, false);
        }
    }

    private void renderList(GuiGraphics g, int mx, int my, int ax, int ay, int aw) {
        if (list.isEmpty()) {
            g.drawString(font, "Стран пока нет.", ax + 6, ay + 6, GRAY, false);
            return;
        }
        int hover = rowAt(mx, my);
        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= list.size()) break;
            CountriesPacket.Info info = list.get(idx);
            int y = ay + i * ROW_H;
            if (idx == hover) g.fill(ax, y, ax + aw, y + ROW_H, 0xFF3A3A3A);
            g.renderItem(new ItemStack(itemOf(info.flag)), ax + 3, y + 1);
            g.drawString(font, info.name, ax + 26, y + 5, info.color | 0xFF000000, true);
            String size = info.chunks + " " + plural(info.chunks, "чанк", "чанка", "чанков");
            g.drawString(font, size, ax + aw - 6 - font.width(size), y + 5, GRAY, false);
        }
    }

    private void renderDetails(GuiGraphics g, int ax, int ay, int aw) {
        CountriesPacket.Info info = list.get(selected);
        int x = ax + 6, y = ay + 6, maxW = aw - 12;

        Item flag = itemOf(info.flag);
        g.renderItem(new ItemStack(flag), ax + aw - 22, ay + 4);

        MutableComponent name = Component.literal("Название: ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(GRAY)))
                .append(Component.literal(info.name).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(info.color)).withBold(true)));
        y = lines(g, name, x, y, maxW - 24);
        y = lines(g, label("Президент", info.president), x, y, maxW);
        MutableComponent al = Component.literal("Альянс: ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(GRAY)))
                .append(info.alliance.isEmpty()
                        ? Component.literal("нет").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)))
                        : Component.literal(info.alliance).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(info.allianceColor & 0xFFFFFF)).withBold(true)));
        y = lines(g, al, x, y, maxW);
        y = lines(g, label("Заместители", info.deputies.isEmpty() ? "нет" : String.join(", ", info.deputies)), x, y, maxW);
        y = lines(g, label("Флаг", new ItemStack(flag).getHoverName().getString()), x, y, maxW);
        y = lines(g, Component.literal("Города:").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(GRAY))), x, y, maxW);
        for (String city : info.cities) {
            y = lines(g, Component.literal("  • " + city).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))), x, y, maxW);
        }
        String size = info.chunks + " " + plural(info.chunks, "чанк", "чанка", "чанков");
        lines(g, label("Размер", size), x, y, maxW);
    }

    private int lines(GuiGraphics g, Component c, int x, int y, int maxW) {
        for (FormattedCharSequence seq : font.split(c, maxW)) {
            g.drawString(font, seq, x, y, 0xFFFFFF, false);
            y += 11;
        }
        return y + 3;
    }
}
