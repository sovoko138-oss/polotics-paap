package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** The residents table of one country, prepared by the server for one particular viewer. */
public class PlayerListPacket {
    public static class Entry {
        public String name = "", title = "", rawTitle = "";
        public boolean online, canKick, canTitle;
        public int rank;
    }

    public static class Group {
        public String city = "";
        public List<Entry> entries = new ArrayList<>();
    }

    public final UUID countryId;
    public final String countryName;
    public final int color;
    public final boolean open; // true: open the window, false: only refresh it if it is already open
    public final List<Group> groups;

    public PlayerListPacket(UUID countryId, String countryName, int color, boolean open, List<Group> groups) {
        this.countryId = countryId; this.countryName = countryName; this.color = color;
        this.open = open; this.groups = groups;
    }

    public static void encode(PlayerListPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.countryId);
        buf.writeUtf(m.countryName, 100);
        buf.writeInt(m.color);
        buf.writeBoolean(m.open);
        buf.writeVarInt(m.groups.size());
        for (Group g : m.groups) {
            buf.writeUtf(g.city, 100);
            buf.writeVarInt(g.entries.size());
            for (Entry e : g.entries) {
                buf.writeUtf(e.name, 64);
                buf.writeUtf(e.title, 100);
                buf.writeUtf(e.rawTitle, 100);
                buf.writeBoolean(e.online);
                buf.writeBoolean(e.canKick);
                buf.writeBoolean(e.canTitle);
                buf.writeVarInt(e.rank);
            }
        }
    }

    public static PlayerListPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf(100);
        int color = buf.readInt();
        boolean open = buf.readBoolean();
        int gn = buf.readVarInt();
        List<Group> groups = new ArrayList<>();
        for (int i = 0; i < gn; i++) {
            Group g = new Group();
            g.city = buf.readUtf(100);
            int en = buf.readVarInt();
            for (int k = 0; k < en; k++) {
                Entry e = new Entry();
                e.name = buf.readUtf(64);
                e.title = buf.readUtf(100);
                e.rawTitle = buf.readUtf(100);
                e.online = buf.readBoolean();
                e.canKick = buf.readBoolean();
                e.canTitle = buf.readBoolean();
                e.rank = buf.readVarInt();
                g.entries.add(e);
            }
            groups.add(g);
        }
        return new PlayerListPacket(id, name, color, open, groups);
    }

    public static void handle(PlayerListPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.openPlayerList(m));
    }
}
