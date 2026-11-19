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
        public final int cx, cz, percent, eta;
        /** done = the chunk is captured; retake = somebody is taking a captured chunk back (percent is that progress). */
        public final boolean mine, done, capital, retake;
        public Row(int cx, int cz, int percent, boolean mine, boolean done, boolean capital, boolean retake, int eta) {
            this.cx = cx; this.cz = cz; this.percent = percent; this.mine = mine; this.done = done;
            this.capital = capital; this.retake = retake; this.eta = eta;
        }
    }

    /** 1 = preparation (countdown), 2 = fighting. */
    public final int phase, seconds;
    public final String enemyName;
    public final int enemyColor, myColor, myTotal, myLost, enemyTotal, enemyLost, myPercent, enemyPercent;
    public final List<Row> rows;

    public WarStatusPacket(int phase, int seconds, String enemyName, int enemyColor, int myColor,
                           int myTotal, int myLost, int enemyTotal, int enemyLost,
                           int myPercent, int enemyPercent, List<Row> rows) {
        this.phase = phase; this.seconds = seconds; this.enemyName = enemyName; this.enemyColor = enemyColor;
        this.myColor = myColor;
        this.myTotal = myTotal; this.myLost = myLost; this.enemyTotal = enemyTotal; this.enemyLost = enemyLost;
        this.myPercent = myPercent; this.enemyPercent = enemyPercent;
        this.rows = rows;
    }

    public static void encode(WarStatusPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.phase);
        buf.writeVarInt(m.seconds);
        buf.writeUtf(m.enemyName, 100);
        buf.writeInt(m.enemyColor);
        buf.writeInt(m.myColor);
        buf.writeVarInt(m.myPercent);
        buf.writeVarInt(m.enemyPercent);
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
            buf.writeBoolean(r.capital);
            buf.writeBoolean(r.retake);
            buf.writeVarInt(r.eta);
        }
    }

    public static WarStatusPacket decode(FriendlyByteBuf buf) {
        int phase = buf.readVarInt(), seconds = buf.readVarInt();
        String name = buf.readUtf(100);
        int color = buf.readInt();
        int myColor = buf.readInt();
        int myPercent = buf.readVarInt(), enemyPercent = buf.readVarInt();
        int mt = buf.readVarInt(), ml = buf.readVarInt(), et = buf.readVarInt(), el = buf.readVarInt();
        int n = buf.readVarInt();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) rows.add(new Row(buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));
        return new WarStatusPacket(phase, seconds, name, color, myColor, mt, ml, et, el, myPercent, enemyPercent, rows);
    }

    public static void handle(WarStatusPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.warStatus(m));
    }
}
