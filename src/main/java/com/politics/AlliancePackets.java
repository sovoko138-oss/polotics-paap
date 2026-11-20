package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** All packets of the alliance windows. */
public class AlliancePackets {

    /** S2C: the list of alliances. */
    public static class ListMsg {
        public static class Entry {
            public final UUID id; public final String name, flag; public final int color, members, chunks;
            public Entry(UUID id, String name, String flag, int color, int members, int chunks) {
                this.id = id; this.name = name; this.flag = flag; this.color = color; this.members = members; this.chunks = chunks;
            }
        }
        public final List<Entry> entries;
        public final boolean canCreate, ownOnly;
        /** How many alliances the viewer's country is in. */
        public final int ownCount;

        public ListMsg(List<Entry> entries, boolean canCreate, int ownCount, boolean ownOnly) {
            this.entries = entries; this.canCreate = canCreate; this.ownCount = ownCount; this.ownOnly = ownOnly;
        }

        public static void encode(ListMsg m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.canCreate);
            buf.writeVarInt(m.ownCount);
            buf.writeBoolean(m.ownOnly);
            buf.writeVarInt(m.entries.size());
            for (Entry e : m.entries) {
                buf.writeUUID(e.id); buf.writeUtf(e.name, 100); buf.writeUtf(e.flag, 100);
                buf.writeInt(e.color); buf.writeVarInt(e.members); buf.writeVarInt(e.chunks);
            }
        }

        public static ListMsg decode(FriendlyByteBuf buf) {
            boolean cc = buf.readBoolean();
            int oc = buf.readVarInt();
            boolean oo = buf.readBoolean();
            int n = buf.readVarInt();
            List<Entry> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                list.add(new Entry(buf.readUUID(), buf.readUtf(100), buf.readUtf(100), buf.readInt(), buf.readVarInt(), buf.readVarInt()));
            }
            return new ListMsg(list, cc, oc, oo);
        }

        public static void handle(ListMsg m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.allianceList(m));
        }
    }

    /** S2C: one alliance in detail. */
    public static class InfoMsg {
        public static class Member {
            public final UUID id; public final String name, flag, president; public final int color, chunks; public final boolean founder;
            public Member(UUID id, String name, String flag, String president, int color, int chunks, boolean founder) {
                this.id = id; this.name = name; this.flag = flag; this.president = president;
                this.color = color; this.chunks = chunks; this.founder = founder;
            }
        }
        public final UUID id;
        public final String name, flag, capital, founderCountry, founderPresident;
        public final int color, totalChunks;
        public final long created;
        public final List<Member> members;
        /** own: the viewer's country is in it; founderView: can exclude; canChat: president of a member; canLeave: president of a non-founder member. */
        public final boolean own, founderView, canChat, canLeave;

        public InfoMsg(UUID id, String name, String flag, String capital, String founderCountry, String founderPresident,
                       int color, int totalChunks, long created, List<Member> members,
                       boolean own, boolean founderView, boolean canChat, boolean canLeave) {
            this.id = id; this.name = name; this.flag = flag; this.capital = capital;
            this.founderCountry = founderCountry; this.founderPresident = founderPresident;
            this.color = color; this.totalChunks = totalChunks; this.created = created; this.members = members;
            this.own = own; this.founderView = founderView; this.canChat = canChat; this.canLeave = canLeave;
        }

        public static void encode(InfoMsg m, FriendlyByteBuf buf) {
            buf.writeUUID(m.id);
            buf.writeUtf(m.name, 100); buf.writeUtf(m.flag, 100); buf.writeUtf(m.capital, 100);
            buf.writeUtf(m.founderCountry, 100); buf.writeUtf(m.founderPresident, 100);
            buf.writeInt(m.color); buf.writeVarInt(m.totalChunks); buf.writeLong(m.created);
            buf.writeBoolean(m.own); buf.writeBoolean(m.founderView); buf.writeBoolean(m.canChat); buf.writeBoolean(m.canLeave);
            buf.writeVarInt(m.members.size());
            for (Member x : m.members) {
                buf.writeUUID(x.id); buf.writeUtf(x.name, 100); buf.writeUtf(x.flag, 100); buf.writeUtf(x.president, 100);
                buf.writeInt(x.color); buf.writeVarInt(x.chunks); buf.writeBoolean(x.founder);
            }
        }

        public static InfoMsg decode(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(100), flag = buf.readUtf(100), capital = buf.readUtf(100);
            String fc = buf.readUtf(100), fp = buf.readUtf(100);
            int color = buf.readInt(), total = buf.readVarInt();
            long created = buf.readLong();
            boolean own = buf.readBoolean(), fv = buf.readBoolean(), cc = buf.readBoolean(), cl = buf.readBoolean();
            int n = buf.readVarInt();
            List<Member> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                list.add(new Member(buf.readUUID(), buf.readUtf(100), buf.readUtf(100), buf.readUtf(100),
                        buf.readInt(), buf.readVarInt(), buf.readBoolean()));
            }
            return new InfoMsg(id, name, flag, capital, fc, fp, color, total, created, list, own, fv, cc, cl);
        }

        public static void handle(InfoMsg m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.allianceInfo(m));
        }
    }

    /** S2C: open the "create alliance" window; carries the names of the countries that can be invited. */
    public static class CreateOpen {
        public final List<String> countries;
        public CreateOpen(List<String> countries) { this.countries = countries; }

        public static void encode(CreateOpen m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.countries.size());
            for (String s : m.countries) buf.writeUtf(s, 100);
        }

        public static CreateOpen decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < n; i++) list.add(buf.readUtf(100));
            return new CreateOpen(list);
        }

        public static void handle(CreateOpen m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.allianceCreate(m));
        }
    }

    /** S2C: open the "invite countries" window of an alliance. */
    public static class InviteOpen {
        public final UUID id;
        public final String name;
        public final List<String> countries;
        public InviteOpen(UUID id, String name, List<String> countries) { this.id = id; this.name = name; this.countries = countries; }

        public static void encode(InviteOpen m, FriendlyByteBuf buf) {
            buf.writeUUID(m.id);
            buf.writeUtf(m.name, 100);
            buf.writeVarInt(m.countries.size());
            for (String s : m.countries) buf.writeUtf(s, 100);
        }

        public static InviteOpen decode(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(100);
            int n = buf.readVarInt();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < n; i++) list.add(buf.readUtf(100));
            return new InviteOpen(id, name, list);
        }

        public static void handle(InviteOpen m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.allianceInvite(m));
        }
    }

    /** S2C: the alliance chat (open = open the window, otherwise only refresh it if it is open). */
    public static class ChatMsg {
        public static class Line {
            public final String sender, country, text; public final int color; public final long time;
            public Line(String sender, String country, String text, int color, long time) {
                this.sender = sender; this.country = country; this.text = text; this.color = color; this.time = time;
            }
        }
        public final UUID id;
        public final String name;
        public final int color;
        public final boolean open;
        public final List<Line> lines;

        public ChatMsg(UUID id, String name, int color, boolean open, List<Line> lines) {
            this.id = id; this.name = name; this.color = color; this.open = open; this.lines = lines;
        }

        public static void encode(ChatMsg m, FriendlyByteBuf buf) {
            buf.writeUUID(m.id); buf.writeUtf(m.name, 100); buf.writeInt(m.color); buf.writeBoolean(m.open);
            buf.writeVarInt(m.lines.size());
            for (Line l : m.lines) {
                buf.writeUtf(l.sender, 64); buf.writeUtf(l.country, 100); buf.writeUtf(l.text, 400);
                buf.writeInt(l.color); buf.writeLong(l.time);
            }
        }

        public static ChatMsg decode(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(100);
            int color = buf.readInt();
            boolean open = buf.readBoolean();
            int n = buf.readVarInt();
            List<Line> list = new ArrayList<>();
            for (int i = 0; i < n; i++) list.add(new Line(buf.readUtf(64), buf.readUtf(100), buf.readUtf(400), buf.readInt(), buf.readLong()));
            return new ChatMsg(id, name, color, open, list);
        }

        public static void handle(ChatMsg m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.allianceChat(m));
        }
    }

    /** C2S: everything the player does in the alliance windows. */
    public static class Action {
        public final String action;
        public final UUID id, id2;
        public final String s1, s2, s3, s4;
        public final int color;

        public Action(String action, UUID id, UUID id2, String s1, String s2, String s3, String s4, int color) {
            this.action = action; this.id = id; this.id2 = id2;
            this.s1 = s1; this.s2 = s2; this.s3 = s3; this.s4 = s4; this.color = color;
        }

        public static Action simple(String action, UUID id) {
            return new Action(action, id, new UUID(0, 0), "", "", "", "", 0);
        }

        public static void encode(Action m, FriendlyByteBuf buf) {
            buf.writeUtf(m.action, 32); buf.writeUUID(m.id); buf.writeUUID(m.id2);
            buf.writeUtf(m.s1, 400); buf.writeUtf(m.s2, 400); buf.writeUtf(m.s3, 800); buf.writeUtf(m.s4, 100);
            buf.writeInt(m.color);
        }

        public static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readUtf(32), buf.readUUID(), buf.readUUID(),
                    buf.readUtf(400), buf.readUtf(400), buf.readUtf(800), buf.readUtf(100), buf.readInt());
        }

        public static void handle(Action m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) AllianceService.act(sender, m);
        }
    }
}
