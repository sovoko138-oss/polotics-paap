package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Everybody who wants to join a country, for the /accept window. */
public class JoinListPacket {
    public static class Entry {
        public final String nick, prevCountry;
        public Entry(String nick, String prevCountry) { this.nick = nick; this.prevCountry = prevCountry; }
    }

    public final UUID countryId;
    public final String countryName;
    public final int color;
    public final boolean open; // true: open the window, false: only refresh it if it is already open
    public final List<Entry> entries;

    public JoinListPacket(UUID countryId, String countryName, int color, boolean open, List<Entry> entries) {
        this.countryId = countryId; this.countryName = countryName; this.color = color;
        this.open = open; this.entries = entries;
    }

    public static void encode(JoinListPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.countryId);
        buf.writeUtf(m.countryName, 100);
        buf.writeInt(m.color);
        buf.writeBoolean(m.open);
        buf.writeVarInt(m.entries.size());
        for (Entry e : m.entries) {
            buf.writeUtf(e.nick, 64);
            buf.writeUtf(e.prevCountry, 100);
        }
    }

    public static JoinListPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf(100);
        int color = buf.readInt();
        boolean open = buf.readBoolean();
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(new Entry(buf.readUtf(64), buf.readUtf(100)));
        return new JoinListPacket(id, name, color, open, list);
    }

    public static void handle(JoinListPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.openJoinList(m));
    }
}
