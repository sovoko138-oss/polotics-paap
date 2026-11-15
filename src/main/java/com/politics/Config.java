package com.politics;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue MIN_PLAYERS = BUILDER
            .comment("Minimum players around the capital block (including the president). Set to 1 for testing.")
            .defineInRange("minPlayers", 4, 1, 100);

    public static final ForgeConfigSpec.BooleanValue PROTECT_TERRITORY = BUILDER
            .comment("If true, only the president, deputies and trusted players can act inside a country's chunks.")
            .define("protectTerritory", true);

    public static final ForgeConfigSpec.BooleanValue OPS_BYPASS = BUILDER
            .comment("If true, operators (permission level 2) ignore territory protection.")
            .define("opsBypassProtection", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
