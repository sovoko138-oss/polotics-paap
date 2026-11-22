package com.politics;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;

/** Hides Superb Warfare items from creative tabs when the Politics mod is present. */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SbwCreativeFilter {
    private SbwCreativeFilter() {}

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!ModList.get().isLoaded("superbwarfare")) return;
        // Forge exposes entries as a mutable linked map, not a Collection with removeIf.
        ArrayList<ItemStack> toRemove = new ArrayList<>();
        for (var entry : event.getEntries()) {
            ItemStack stack = entry.getKey();
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null && "superbwarfare".equals(id.getNamespace())) {
                toRemove.add(stack);
            }
        }
        for (ItemStack stack : toRemove) {
            event.getEntries().remove(stack);
        }
    }
}
