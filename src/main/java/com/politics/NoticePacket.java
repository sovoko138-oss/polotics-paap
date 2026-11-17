package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A message in the right edge of the screen. It never opens a window and never pauses the game. */
public class NoticePacket {
    public final String title, body;
    public final int color;

    public NoticePacket(String title, String body, int color) { this.title = title; this.body = body; this.color = color; }

    public static void encode(NoticePacket m, FriendlyByteBuf buf) {
        buf.writeUtf(m.title, 200);
        buf.writeUtf(m.body, 1000);
        buf.writeInt(m.color);
    }

    public static NoticePacket decode(FriendlyByteBuf buf) {
        return new NoticePacket(buf.readUtf(200), buf.readUtf(1000), buf.readInt());
    }

    public static void handle(NoticePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.notice(m));
    }
}
