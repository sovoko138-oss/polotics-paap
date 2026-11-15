package com.politics.client;

import com.politics.BorderSyncPacket;
import com.politics.ClientBorderState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

public class ClientHooks {
    public static void openCreateScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new CreateCountryScreen(pos));
    }

    public static void onCreateResult(boolean ok, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CreateCountryScreen s) s.onResult(ok, message);
    }

    public static void setBorder(BorderSyncPacket m) {
        Set<Long> set = new HashSet<>();
        for (long l : m.chunks) set.add(l);
        ClientBorderState.chunks = set;
        ClientBorderState.dimension = m.dimension;
        ClientBorderState.color = m.color;
        ClientBorderState.active = m.active;
    }
}
