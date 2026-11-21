package com.politics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent operator-controlled dimension switches. */
public class DimensionRulesData extends SavedData {
    private boolean netherEnabled;
    private boolean endEnabled;

    public DimensionRulesData() {
        netherEnabled = !Config.DISABLE_NETHER.get();
        endEnabled = !Config.DISABLE_END.get();
    }

    public static DimensionRulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                DimensionRulesData::load, DimensionRulesData::new, "politics_dimension_rules");
    }

    public static DimensionRulesData load(CompoundTag tag) {
        DimensionRulesData data = new DimensionRulesData();
        if (tag.contains("NetherEnabled")) data.netherEnabled = tag.getBoolean("NetherEnabled");
        if (tag.contains("EndEnabled")) data.endEnabled = tag.getBoolean("EndEnabled");
        return data;
    }

    public boolean netherEnabled() { return netherEnabled; }
    public boolean endEnabled() { return endEnabled; }

    public void setNetherEnabled(boolean enabled) {
        netherEnabled = enabled;
        setDirty();
    }

    public void setEndEnabled(boolean enabled) {
        endEnabled = enabled;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("NetherEnabled", netherEnabled);
        tag.putBoolean("EndEnabled", endEnabled);
        return tag;
    }
}
