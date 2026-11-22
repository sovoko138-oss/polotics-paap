package com.politics;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

/** Hides Superb Warfare items from creative tabs when the Politics mod is present. */
@Mod.EventBusSubscriber(modid = PoliticsMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SbwCreativeFilter {
    private SbwCreativeFilter() {}

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!ModList.get().isLoaded("superbwarfare")) return;
        event.getEntries().removeIf(stack -> {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return id != null && "superbwarfare".equals(id.getNamespace());
        });
    }
}
