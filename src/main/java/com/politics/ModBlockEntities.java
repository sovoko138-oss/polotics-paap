package com.politics;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PoliticsMod.MODID);

    public static final RegistryObject<BlockEntityType<CapitalBlockEntity>> CAPITAL =
            BLOCK_ENTITIES.register("capital", () ->
                    BlockEntityType.Builder.of(CapitalBlockEntity::new, ModBlocks.CAPITAL_BLOCK.get()).build(null));
}
