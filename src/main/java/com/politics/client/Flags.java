package com.politics.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public class Flags {
    /** The banner item behind a flag id, or the white banner if the id is unknown. */
    public static Item item(String id) {
        try {
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            return it == null || it == Items.AIR ? Items.WHITE_BANNER : it;
        } catch (Exception e) {
            return Items.WHITE_BANNER;
        }
    }
}
