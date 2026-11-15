package com.politics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class ClientHooks {
    public static void openCreateScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new CreateCountryScreen(pos));
    }

    public static void onCreateResult(boolean ok, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CreateCountryScreen s) s.onResult(ok, message);
    }
}
