package com.politics.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.politics.CountryData;
import com.politics.OpenMapPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** /map: the world as far as the player has seen it, with the countries on top (can be switched off). */
public class WorldMapScreen extends Screen {
    private static final int REGION = 32; // chunks per texture side
    private static final int W_MAX = 640, H_MAX = 420;

    private final List<OpenMapPacket.Entry> countries = new ArrayList<>();
    private final Map<Long, Integer> owner = new HashMap<>(); // seen chunk -> index of its country
    private final Map<Long, ResourceLocation> textures = new HashMap<>();
    private boolean built = false;
    private boolean showCountries = true;
    private double cx, cz, zoom = 1.0;
    private int left, top, w, h, vx, vy, vw, vh;
    private Button toggle;

    public WorldMapScreen(OpenMapPacket m) {
        super(Component.literal("Карта мира"));
        countries.addAll(m.countries);
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        w = Math.min(width - 20, W_MAX);
        h = Math.min(height - 20, H_MAX);
        left = (width - w) / 2;
        top = (height - h) / 2;
        vx = left + 10; vy = top + 22; vw = w - 20; vh = h - 22 - 38;

        if (!built) {
            built = true;
            cx = mc.player.getX();
            cz = mc.player.getZ();
            buildTextures(mc);
        }

        int by = top + h - 28;
        toggle = Button.builder(Component.literal(""), b -> {
            showCountries = !showCountries;
            updateToggle();
        }).bounds(left + 10, by, 130, 20).build();
        addRenderableWidget(toggle);
        updateToggle();
        addRenderableWidget(Button.builder(Component.literal("К игроку"), b -> {
            cx = mc.player.getX();
            cz = mc.player.getZ();
        }).bounds(left + w / 2 - 50, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(left + w - 110, by, 100, 20).build());
    }

    private void updateToggle() {
        toggle.setMessage(Component.literal("Страны: " + (showCountries ? "вкл" : "выкл")));
    }

    private void buildTextures(Minecraft mc) {
        String dim = mc.level.dimension().location().toString();
        Map<Long, MapCache.Tile> tiles = MapCache.tiles(dim);

        Map<Long, NativeImage> images = new HashMap<>();
        for (Map.Entry<Long, MapCache.Tile> en : tiles.entrySet()) {
            int tcx = CountryData.keyX(en.getKey()), tcz = CountryData.keyZ(en.getKey());
            int rx = Math.floorDiv(tcx, REGION), rz = Math.floorDiv(tcz, REGION);
            long rk = CountryData.chunkKey(rx, rz);
            NativeImage img = images.computeIfAbsent(rk, k -> new NativeImage(NativeImage.Format.RGBA, REGION * 16, REGION * 16, true));
            int ox = (tcx - rx * REGION) * 16, oz = (tcz - rz * REGION) * 16;
            byte[] px = en.getValue().px;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int id = px[x + z * 16] & 0xFF;
                    if (id == 0) continue;
                    img.setPixelRGBA(ox + x, oz + z, MapColor.getColorFromPackedId(id));
                }
            }
        }
        for (Map.Entry<Long, NativeImage> en : images.entrySet()) {
            DynamicTexture tex = new DynamicTexture(en.getValue());
            textures.put(en.getKey(), mc.getTextureManager().register("politics_map_region", tex));
        }

        // countries: only on chunks the player has seen
        for (int i = 0; i < countries.size(); i++) {
            OpenMapPacket.Entry c = countries.get(i);
            if (!c.dimension.equals(dim)) continue;
            for (long k : c.chunks) if (tiles.containsKey(k)) owner.put(k, i);
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation rl : textures.values()) mc.getTextureManager().release(rl);
        textures.clear();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---- view ----
    private double worldX(double mx) { return cx + (mx - (vx + vw / 2.0)) / zoom; }
    private double worldZ(double my) { return cz + (my - (vy + vh / 2.0)) / zoom; }
    private boolean inView(double mx, double my) { return mx >= vx && mx < vx + vw && my >= vy && my < vy + vh; }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && inView(mx, my)) {
            cx -= dx / zoom;
            cz -= dy / zoom;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!inView(mx, my)) return false;
        double bx = worldX(mx), bz = worldZ(my);
        zoom = Math.max(0.125, Math.min(16.0, zoom * (delta > 0 ? 1.25 : 0.8)));
        cx += bx - worldX(mx);
        cz += bz - worldZ(my);
        return true;
    }

    private static int lighten(int rgb, float f) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        r += (int) ((255 - r) * f); g += (int) ((255 - g) * f); b += (int) ((255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    // ---- drawing ----
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Panel.draw(g, left, top, w, h);
        g.drawString(font, title, left + (w - font.width(title)) / 2, top + 7, 0x404040, false);

        int hover = -1;
        if (showCountries && inView(mx, my)) {
            int ccx = (int) Math.floor(worldX(mx) / 16.0), ccz = (int) Math.floor(worldZ(my) / 16.0);
            Integer o = owner.get(CountryData.chunkKey(ccx, ccz));
            if (o != null) hover = o;
        }

        g.fill(vx - 1, vy - 1, vx + vw + 1, vy + vh + 1, 0xFF373737);
        g.fill(vx, vy, vx + vw, vy + vh, 0xFF0E0E0E);
        g.enableScissor(vx, vy, vx + vw, vy + vh);

        g.pose().pushPose();
        g.pose().translate(vx + vw / 2.0, vy + vh / 2.0, 0);
        g.pose().scale((float) zoom, (float) zoom, 1f);
        g.pose().translate(-cx, -cz, 0);

        double minX = worldX(vx), maxX = worldX(vx + vw), minZ = worldZ(vy), maxZ = worldZ(vy + vh);
        int size = REGION * 16;
        for (Map.Entry<Long, ResourceLocation> en : textures.entrySet()) {
            int rx = CountryData.keyX(en.getKey()), rz = CountryData.keyZ(en.getKey());
            if ((rx + 1) * size < minX || rx * size > maxX || (rz + 1) * size < minZ || rz * size > maxZ) continue;
            g.blit(en.getValue(), rx * size, rz * size, size, size, 0, 0, size, size, size, size);
        }

        if (showCountries) {
            int edge = Math.max(1, (int) Math.round(1.5 / zoom));
            for (Map.Entry<Long, Integer> en : owner.entrySet()) {
                long k = en.getKey();
                int x0 = CountryData.keyX(k) * 16, z0 = CountryData.keyZ(k) * 16;
                if (x0 + 16 < minX || x0 > maxX || z0 + 16 < minZ || z0 > maxZ) continue;
                int idx = en.getValue();
                boolean hot = idx == hover;
                int rgb = countries.get(idx).color & 0xFFFFFF;
                if (hot) rgb = lighten(rgb, 0.35f);
                g.fill(x0, z0, x0 + 16, z0 + 16, ((hot ? 0xA0 : 0x55) << 24) | rgb);

                int line = 0xE0000000 | rgb;
                int cxk = CountryData.keyX(k), czk = CountryData.keyZ(k);
                if (!sameOwner(cxk, czk - 1, idx)) g.fill(x0, z0, x0 + 16, z0 + edge, line);
                if (!sameOwner(cxk, czk + 1, idx)) g.fill(x0, z0 + 16 - edge, x0 + 16, z0 + 16, line);
                if (!sameOwner(cxk - 1, czk, idx)) g.fill(x0, z0, x0 + edge, z0 + 16, line);
                if (!sameOwner(cxk + 1, czk, idx)) g.fill(x0 + 16 - edge, z0, x0 + 16, z0 + 16, line);
            }
        }

        // the player
        Minecraft mc = Minecraft.getInstance();
        int ps = Math.max(3, (int) Math.round(5 / zoom));
        int px = (int) Math.round(mc.player.getX()), pz = (int) Math.round(mc.player.getZ());
        g.fill(px - ps / 2 - 1, pz - ps / 2 - 1, px + ps / 2 + 2, pz + ps / 2 + 2, 0xFF000000);
        g.fill(px - ps / 2, pz - ps / 2, px + ps / 2 + 1, pz + ps / 2 + 1, 0xFFFFFFFF);

        g.pose().popPose();
        g.disableScissor();

        String hint = "Колёсико: масштаб   Перетаскивание: сдвиг";
        g.drawString(font, hint, left + w - 10 - font.width(hint), top + 7, 0x707070, false);

        super.render(g, mx, my, pt);

        if (hover >= 0) {
            OpenMapPacket.Entry c = countries.get(hover);
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(c.name).withStyle(s -> s.withColor(c.color & 0xFFFFFF).withBold(true)));
            tip.add(Component.literal("Президент: " + c.president).withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    private boolean sameOwner(int cx, int cz, int idx) {
        Integer o = owner.get(CountryData.chunkKey(cx, cz));
        return o != null && o == idx;
    }
}
