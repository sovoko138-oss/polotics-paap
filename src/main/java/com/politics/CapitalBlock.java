package com.politics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class CapitalBlock extends BaseEntityBlock {
    public CapitalBlock(Properties props) { super(props); }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CapitalBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof CapitalBlockEntity be) {
            be.setOwner(player.getUUID(), player.getGameProfile().getName());
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof CapitalBlockEntity be)) return InteractionResult.PASS;

        if (!player.getUUID().equals(be.getOwnerId())) {
            player.displayClientMessage(Component.literal("Только игрок, поставивший этот блок, может создать страну."), false);
            return InteractionResult.CONSUME;
        }
        if (be.isUsed()) {
            player.displayClientMessage(Component.literal("Из этого блока страна уже создана."), false);
            return InteractionResult.CONSUME;
        }

        AABB area = new AABB(pos.getX() - 2, level.getMinBuildHeight(), pos.getZ() - 2,
                             pos.getX() + 3, level.getMaxBuildHeight(), pos.getZ() + 3);
        int count = level.getEntitiesOfClass(Player.class, area).size();
        int required = Config.MIN_PLAYERS.get();

        if (count < required) {
            player.displayClientMessage(Component.literal("Нужно игроков вокруг блока: " + required + ", сейчас: " + count), false);
            return InteractionResult.CONSUME;
        }

        level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        player.displayClientMessage(Component.literal("Условия выполнены (игроков: " + count + ")."), false);
        return InteractionResult.CONSUME;
    }
}
