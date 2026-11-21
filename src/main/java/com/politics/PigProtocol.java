package com.politics;

import net.minecraft.server.MinecraftServer;

/** Pigstan's automatic creation protocol is disabled. Constants remain for compatibility with existing saves. */
public class PigProtocol {
    public static final String NAME = "Свиностан";
    public static final String CAPITAL = "Свинск";
    public static final String PRESIDENT = "Главный Хряк";
    public static final int CHUNKS = 8;

    /** Deliberately disabled: pigs can no longer found Pigstan or expand it automatically. */
    public static void growTick(MinecraftServer server, CountryData data) { }

    /** Compatibility no-op. */
    public static void reset() { }
}
