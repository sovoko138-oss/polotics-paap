package com.politics;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class CapitalBlockEntity extends BlockEntity {
    private UUID ownerId;
    private String ownerName = "";
    private boolean used = false;

    public CapitalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAPITAL.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() != null && !getLevel().isClientSide) PigProtocol.register(getLevel().dimension(), getBlockPos());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (getLevel() != null && !getLevel().isClientSide) PigProtocol.unregister(getLevel().dimension(), getBlockPos());
    }

    public void setOwner(UUID id, String name) { ownerId = id; ownerName = name; setChanged(); }
    public UUID getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; setChanged(); }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putString("OwnerName", ownerName);
        tag.putBoolean("Used", used);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        ownerName = tag.getString("OwnerName");
        used = tag.getBoolean("Used");
    }
}
