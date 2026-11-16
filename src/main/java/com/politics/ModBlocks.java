package com.politics;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, PoliticsMod.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PoliticsMod.MODID);

    public static final RegistryObject<Block> CAPITAL_BLOCK = BLOCKS.register("capital_block",
            () -> new CapitalBlock(BlockBehaviour.Properties.copy(Blocks.NETHERITE_BLOCK)));

    public static final RegistryObject<Item> TEST_PICKAXE = ITEMS.register("test_pickaxe",
            () -> new TestPickaxe(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> CAPITAL_BLOCK_ITEM = ITEMS.register("capital_block",
            () -> new BlockItem(CAPITAL_BLOCK.get(), new Item.Properties()));
}
