package com.politics.client;

import com.politics.ModBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Scrollable parchment-style server guide. */
public class HelpPolitScreen extends Screen {
    private final List<String> paragraphs = new ArrayList<>();
    private int scroll;
    private int contentHeight;
    private int panelLeft, panelTop, panelRight, panelBottom;
    private int closeY;

    public HelpPolitScreen() {
        super(Component.literal("Путеводитель Politics"));
        paragraphs.add("Добро пожаловать в Politics! Это окно — путеводитель по серверу и помощь. Если что-то непонятно, пишите /adminq (сообщение), чтобы задать вопрос админу.");
        paragraphs.add("Начнем с базы");
        paragraphs.add("1. Создание страны\nЧтобы создать страну, вам нужен блок столицы. Его крафт:");
        paragraphs.add("Поставьте блок столицы там, где хотите начать строить свою страну, и позовите 3 друзей, с которыми вы создадите страну. Вам нужно вчетвером встать вокруг блока столицы. Тот, кто поставил блок, становится президентом.");
        paragraphs.add("1.1 Захват территорий\nПрезидент берет в руку деревянную мотыгу, идет на свободный чанк, нажимает ПКМ и забирает новую территорию! Взяв деревянную мотыгу, вы также можете увидеть границы своей страны, или же на карте по команде /map.");
        paragraphs.add("2. Альянсы и войны\nХотите вместе со своими друзьями из другой страны выживать и обороняться против врагов? Альянсы — ваш выбор! Напишите /alliance, чтобы создать свой альянс. Приглашение в чужой альянс может выдать только глава альянса.");
        paragraphs.add("Если вы доверяете только определенному кругу лиц, а не всей стране, президент может использовать /trust, чтобы добавить определенного человека в список доверенных игроков.");
        paragraphs.add("А теперь вы что-то не поделили с другой страной? Или наоборот, вам чем-то испоганили выживание? Объявите им войну через /war (страна)! На следующий день начнутся боевые действия, и страны смогут бороться за территории.");
        paragraphs.add("3. Техника и оружие\nНа сервере установлен мод Superb Warfare, добавляющий много разных техник и оружий — будет чем отбиваться от захватчиков!");
        paragraphs.add("4. Дополнительно\nДля президента страны:\n/capital (change/info) — управление столицей страны\n/join accept — принять игрока в страну\n/war — объявление войны\n/peace — предложение мира\n/trust — добавить игрока в список доверенных и настроить его права\n/country delete — удалить страну (команда администратора)\n\nДля игроков:\n/join (страна) — подать заявку на вступление\n/countries — список стран\n/country list — упрощенный список стран\n/country playerlist — список жителей страны");
    }

    @Override
    protected void init() {
        panelLeft = Math.max(18, (width - 330) / 2);
        panelRight = Math.min(width - 18, panelLeft + 330);
        panelTop = 14;
        panelBottom = height - 42;
        closeY = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Выход"), b -> onClose())
                .bounds(width / 2 - 45, closeY, 90, 20).build());
        calculateHeight();
        scroll = Math.min(scroll, maxScroll());
    }

    private void calculateHeight() {
        contentHeight = 12;
        int innerWidth = panelRight - panelLeft - 28;
        for (String paragraph : paragraphs) {
            String[] parts = paragraph.split("\\n", -1);
            for (String part : parts) contentHeight += Math.max(1, font.split(Component.literal(part), innerWidth).size()) * 10;
            contentHeight += 8;
        }
        // Crafting sketch takes 70 pixels.
        contentHeight += 76;
    }

    private int maxScroll() { return Math.max(0, contentHeight - (panelBottom - panelTop - 22)); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xC8000000);
        // Parchment/map-like background, with layered edge and subtle paper lines.
        g.fill(panelLeft - 4, panelTop - 4, panelRight + 4, panelBottom + 4, 0xFF5A4025);
        g.fill(panelLeft, panelTop, panelRight, panelBottom, 0xFFE1C58C);
        g.fill(panelLeft + 3, panelTop + 3, panelRight - 3, panelBottom - 3, 0xFFEAD8A8);
        g.fill(panelLeft + 7, panelTop + 7, panelRight - 7, panelBottom - 7, 0xFFDDC795);
        g.drawCenteredString(font, "ПУТЕВОДИТЕЛЬ POLITICS", width / 2, panelTop + 10, 0x49351D);
        g.fill(panelLeft + 14, panelTop + 28, panelRight - 14, panelTop + 29, 0x775A3C);

        int clipTop = panelTop + 34;
        int clipBottom = panelBottom - 8;
        g.enableScissor(panelLeft + 12, clipTop, panelRight - 12, clipBottom);
        int y = clipTop + 5 - scroll;
        int innerWidth = panelRight - panelLeft - 28;
        for (int i = 0; i < paragraphs.size(); i++) {
            String paragraph = paragraphs.get(i);
            boolean heading = paragraph.startsWith("Начнем") || paragraph.startsWith("1.") || paragraph.startsWith("2.") || paragraph.startsWith("3.") || paragraph.startsWith("4.");
            for (String part : paragraph.split("\\n", -1)) {
                List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(part), innerWidth);
                for (net.minecraft.util.FormattedCharSequence line : lines) {
                    if (y >= clipTop - 10 && y < clipBottom) g.drawString(font, line, panelLeft + 14, y, heading ? 0x55391B : 0x302417, false);
                    y += 10;
                }
            }
            y += 7;
            if (i == 2) {
                drawRecipe(g, panelLeft + 14, y);
                y += 76;
            }
        }
        g.disableScissor();
        // Scroll indicator
        if (maxScroll() > 0) {
            int trackTop = clipTop + 2, trackBottom = clipBottom - 2;
            int trackHeight = trackBottom - trackTop;
            int thumbHeight = Math.max(18, trackHeight * trackHeight / contentHeight);
            int thumbY = trackTop + (trackHeight - thumbHeight) * scroll / maxScroll();
            g.fill(panelRight - 8, trackTop, panelRight - 5, trackBottom, 0x55391B);
            g.fill(panelRight - 8, thumbY, panelRight - 5, thumbY + thumbHeight, 0xFF76532E);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawRecipe(GuiGraphics g, int x, int y) {
        g.drawString(font, "Крафт блока столицы", x, y, 0x55391B, false);
        int cell = 20;
        int startX = x + 4, startY = y + 14;
        ItemStack[][] recipe = {
                {new ItemStack(Items.STICK), new ItemStack(Items.WHITE_WOOL), new ItemStack(Items.WHITE_WOOL)},
                {new ItemStack(Items.STICK), new ItemStack(Items.WHITE_WOOL), new ItemStack(Items.WHITE_WOOL)},
                {new ItemStack(Items.STICK), ItemStack.EMPTY, ItemStack.EMPTY}
        };
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) {
            int px = startX + c * cell, py = startY + r * cell;
            g.fill(px, py, px + 18, py + 18, 0xFFB79A67);
            g.fill(px + 1, py + 1, px + 17, py + 17, 0xFFDFC99D);
            if (!recipe[r][c].isEmpty()) g.renderItem(recipe[r][c], px + 1, py + 1);
        }
        g.drawString(font, "→", startX + 66, startY + 20, 0x55391B, false);
        g.renderItem(new ItemStack(ModBlocks.CAPITAL_BLOCK_ITEM), startX + 88, startY + 19);
        g.renderItemDecorations(font, new ItemStack(ModBlocks.CAPITAL_BLOCK_ITEM), startX + 88, startY + 19);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = MthClamp(scroll - (int)(delta * 18), 0, maxScroll());
        return true;
    }

    private int MthClamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(null); }
    @Override public boolean isPauseScreen() { return false; }
}
