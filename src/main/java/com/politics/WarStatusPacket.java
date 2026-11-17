package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Sent every second to the people of both countries at war: the table of chunks on the right edge. */
public class WarStatusPacket {
    public static class Row {
        public final int cx, cz, percent;
        public final boolean mine, done;
        public Row(int cx, int cz, int percent, boolean mine, boolean done) {
            this.cx = cx; this.cz = cz; this.percent = percent; this.mine = mine; this.done = done;
        }
    }

    /** 1 = preparation (countdown), 2 = fighting. */
    public final int phase, seconds;
    public final String enemyName;
    public final int enemyColor, myTotal, myLost, enemyTotal, enemyLost;
    public final List<Row> rows;

    public WarStatusPacket(int phase, int seconds, String enemyName, int enemyColor,
                           int myTotal, int myLost, int enemyTotal, int enemyLost, List<Row> rows) {
        this.phase = phase; this.seconds = seconds; this.enemyName = enemyName; this.enemyColor = enemyColor;
        this.myTotal = myTotal; this.myLost = myLost; this.enemyTotal = enemyTotal; this.enemyLost = enemyLost;
        this.rows = rows;
    }

    public static void encode(WarStatusPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.phase);
        buf.writeVarInt(m.seconds);
        buf.writeUtf(m.enemyName, 100);
        buf.writeInt(m.enemyColor);
        buf.writeVarInt(m.myTotal);
        buf.writeVarInt(m.myLost);
        buf.writeVarInt(m.enemyTotal);
        buf.writeVarInt(m.enemyLost);
        buf.writeVarInt(m.rows.size());
        for (Row r : m.rows) {
            buf.writeInt(r.cx);
            buf.writeInt(r.cz);
            buf.writeVarInt(r.percent);
            buf.writeBoolean(r.mine);
            buf.writeBoolean(r.done);
        }
    }

    public static WarStatusPacket decode(FriendlyByteBuf buf) {
        int phase = buf.readVarInt(), seconds = buf.readVarInt();
        String name = buf.readUtf(100);
        int color = buf.readInt();
        int mt = buf.readVarInt(), ml = buf.readVarInt(), et = buf.readVarInt(), el = buf.readVarInt();
        int n = buf.readVarInt();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) rows.add(new Row(buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
        return new WarStatusPacket(phase, seconds, name, color, mt, ml, et, el, rows);
    }

    public static void handle(WarStatusPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.warStatus(m));
    }
}
