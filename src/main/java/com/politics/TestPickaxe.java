package com.politics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Test tool for checking how often ores are found: breaks everything instantly, never wears out,
 * and mines a 3x3 area (perpendicular to the face that was hit) instead of one block.
 */
public class TestPickaxe extends PickaxeItem {
    private static boolean breaking = false;

    /** A tier that never breaks and mines every block instantly. */
    private static class TestTier implements Tier {
        public int getUses() { return 0; } // 0 durability means the item cannot be damaged
        public float getSpeed() { return 100000f; }
        public float getAttackDamageBonus() { return 0f; }
        public int getLevel() { return 4; }
        public int getEnchantmentValue() { return 0; }
        public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    }

    public TestPickaxe(Properties props) {
        super(new TestTier(), 1, -2.8f, props);
    }

    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 100000f; // dirt, sand and stone alike
    }

    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) { return true; }

    public boolean isCorrectToolForDrops(BlockState state) { return true; }

    /** Called before the targeted block breaks: break the 8 blocks around it first. */
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
        if (breaking || !(player instanceof ServerPlayer sp)) return false;
        Level level = sp.level();

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) return false;
        Direction.Axis axis = hit.getDirection().getAxis();

        breaking = true;
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    BlockPos p = switch (axis) {
                        case X -> pos.offset(0, a, b);
                        case Y -> pos.offset(a, 0, b);
                        case Z -> pos.offset(a, b, 0);
                    };
                    BlockState st = level.getBlockState(p);
                    if (st.isAir() || st.getDestroySpeed(level, p) < 0) continue; // air and bedrock
                    sp.gameMode.destroyBlock(p);
                }
            }
        } finally {
            breaking = false;
        }
        return false; // the main block breaks as usual
    }
}
