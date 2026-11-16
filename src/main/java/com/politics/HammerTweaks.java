package com.politics;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Меняет прочность молота из Superb Warfare (superbwarfare:hammer). */
public final class HammerTweaks {
    private static final Logger LOG = LoggerFactory.getLogger("politics");
    public static final int HAMMER_DURABILITY = 170;

    private HammerTweaks() {}

    public static void apply() {
        ResourceLocation id = new ResourceLocation("superbwarfare", "hammer");
        if (!ForgeRegistries.ITEMS.containsKey(id)) return;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) return;
        int old = item.getMaxDamage();
        if (old == HAMMER_DURABILITY) return;
        try {
            Field target = null;
            for (Field f : Item.class.getDeclaredFields()) {
                if (f.getType() != int.class || Modifier.isStatic(f.getModifiers())) continue;
                String n = f.getName();
                if (n.equals("maxDamage") || n.equals("f_41371_")) { target = f; break; }
            }
            if (target == null) {
                // запасной вариант: поле int, значение которого равно текущей прочности
                for (Field f : Item.class.getDeclaredFields()) {
                    if (f.getType() != int.class || Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    if (f.getInt(item) == old) { target = f; break; }
                }
            }
            if (target == null) {
                LOG.warn("[politics] не нашёл поле прочности у Item, молот не изменён");
                return;
            }
            target.setAccessible(true);
            target.setInt(item, HAMMER_DURABILITY);
            LOG.info("[politics] superbwarfare:hammer прочность {} -> {}", old, item.getMaxDamage());
        } catch (Throwable t) {
            LOG.warn("[politics] не удалось изменить прочность молота", t);
        }
    }
}
