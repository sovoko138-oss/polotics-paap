package com.politics;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue MIN_PLAYERS = BUILDER
            .comment("Minimum players around the capital block (including the president). Set to 1 for testing.")
            .defineInRange("minPlayers", 4, 1, 100);

    public static final ForgeConfigSpec.BooleanValue DISABLE_NETHER = BUILDER
            .comment("If true, players cannot enter the Nether.")
            .define("disableNether", true);

    public static final ForgeConfigSpec.BooleanValue DISABLE_END = BUILDER
            .comment("If true, players cannot enter the End.")
            .define("disableEnd", true);

    public static final ForgeConfigSpec.BooleanValue PROTECT_TERRITORY = BUILDER
            .comment("If true, only the president, deputies and trusted players can act inside a country's chunks.")
            .define("protectTerritory", true);

    public static final ForgeConfigSpec.BooleanValue OPS_BYPASS = BUILDER
            .comment("If true, operators (permission level 2) ignore territory protection.")
            .define("opsBypassProtection", false);

    public static final ForgeConfigSpec.IntValue PIGS_REQUIRED = BUILDER
            .comment("How many pigs must stand next to a free capital block to start the pig protocol (Свиностан). Set to 1 for testing.")
            .defineInRange("pigsRequired", 4, 1, 50);

    public static final ForgeConfigSpec.IntValue WAR_MIN_CHUNKS = BUILDER
            .comment("How many chunks a country needs before /war can be used.")
            .defineInRange("warMinChunks", 8, 1, 1000);

    public static final ForgeConfigSpec.IntValue PIG_CLAIM_SECONDS = BUILDER
            .comment("The pigs claim one more chunk every this many seconds until they have 8.")
            .defineInRange("pigClaimSeconds", 30, 1, 3600);

    public static final ForgeConfigSpec.IntValue WAR_PREP_SECONDS = BUILDER
            .comment("Seconds between the declaration of war and the start of the fighting.")
            .defineInRange("warPrepSeconds", 120, 0, 3600);

    public static final ForgeConfigSpec.IntValue CHUNK_CAPTURE_SECONDS = BUILDER
            .comment("Seconds one player must stand in an enemy chunk to capture it.")
            .defineInRange("chunkCaptureSeconds", 50, 1, 3600);

    public static final ForgeConfigSpec.IntValue CAPITAL_CAPTURE_SECONDS = BUILDER
            .comment("Seconds one player must stand in the capital chunk of an enemy country to capture it.")
            .defineInRange("capitalCaptureSeconds", 100, 1, 3600);

    public static final ForgeConfigSpec.IntValue CAPTURE_SECONDS_PER_EXTRA = BUILDER
            .comment("Every additional player standing in the chunk shortens the capture by this many seconds.")
            .defineInRange("captureSecondsPerExtraPlayer", 5, 0, 600);

    public static final ForgeConfigSpec.IntValue CAPTURE_MAX_PLAYERS = BUILDER
            .comment("Most players that count for the capture bonus (5 players = 20 seconds faster by default).")
            .defineInRange("captureMaxPlayers", 5, 1, 50);

    public static final ForgeConfigSpec.BooleanValue DEBUG_FAKE_JOIN_REQUESTS = BUILDER
            .comment("TEST: when a country is created its president gets two fake join requests (Steve, Alex). Turn off for real play.")
            .define("debugFakeJoinRequests", true);

    public static final ForgeConfigSpec.IntValue FORT_SECONDS = BUILDER
            .comment("NPC countries build one more watchtower along their border every this many seconds.")
            .defineInRange("fortSeconds", 45, 1, 3600);

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
