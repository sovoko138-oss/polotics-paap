package com.politics.client;

import com.politics.BorderSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class ClientHooks {
    // Border of the player's own country (only filled in for presidents)
    public static volatile boolean borderActive = false;
    public static volatile String borderDimension = "";
    public static volatile int borderChunkX, borderChunkZ, borderColor;

    public static void openCreateScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new CreateCountryScreen(pos));
    }

    public static void onCreateResult(boolean ok, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CreateCountryScreen s) s.onResult(ok, message);
    }

    public static void setBorder(BorderSyncPacket m) {
        borderActive = m.active;
        borderDimension = m.dimension;
        borderChunkX = m.chunkX;
        borderChunkZ = m.chunkZ;
        borderColor = m.color;
    }
}
