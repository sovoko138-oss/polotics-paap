package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** All packets of the /country trust windows. */
public class TrustPackets {

    /** S2C: open the window where a nickname is typed; lists the already trusted players. */
    public static class OpenName {
        public final UUID countryId;
        public final String countryName;
        public final List<String> trusted;

        public OpenName(UUID countryId, String countryName, List<String> trusted) {
            this.countryId = countryId; this.countryName = countryName; this.trusted = trusted;
        }

        public static void encode(OpenName m, FriendlyByteBuf buf) {
            buf.writeUUID(m.countryId);
            buf.writeUtf(m.countryName, 100);
            buf.writeVarInt(m.trusted.size());
            for (String s : m.trusted) buf.writeUtf(s, 64);
        }

        public static OpenName decode(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(100);
            int n = buf.readVarInt();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < n; i++) list.add(buf.readUtf(64));
            return new OpenName(id, name, list);
        }

        public static void handle(OpenName m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.openTrustName(m));
        }
    }

    /** S2C: open the rights window for one player. */
    public static class OpenRights {
        public final UUID countryId;
        public final String nick;
        public final int rights;

        public OpenRights(UUID countryId, String nick, int rights) {
            this.countryId = countryId; this.nick = nick; this.rights = rights;
        }

        public static void encode(OpenRights m, FriendlyByteBuf buf) {
            buf.writeUUID(m.countryId);
            buf.writeUtf(m.nick, 64);
            buf.writeVarInt(m.rights);
        }

        public static OpenRights decode(FriendlyByteBuf buf) {
            return new OpenRights(buf.readUUID(), buf.readUtf(64), buf.readVarInt());
        }

        public static void handle(OpenRights m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.openTrustRights(m));
        }
    }

    /** C2S: the nickname was typed, send me the rights window. */
    public static class Request {
        public final UUID countryId;
        public final String nick;

        public Request(UUID countryId, String nick) { this.countryId = countryId; this.nick = nick; }

        public static void encode(Request m, FriendlyByteBuf buf) {
            buf.writeUUID(m.countryId);
            buf.writeUtf(m.nick, 64);
        }

        public static Request decode(FriendlyByteBuf buf) { return new Request(buf.readUUID(), buf.readUtf(64)); }

        public static void handle(Request m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            CountryData data = CountryData.get(sender.getServer());
            CountryData.Country c = data.byId(m.countryId);
            String nick = m.nick.trim();
            String error = TrustService.checkTarget(sender, c, nick);
            if (error != null) {
                PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), new CreateResultPacket(false, error));
                return;
            }
            CountryData.Trust t = c.trusts.get(nick.toLowerCase());
            PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                    new OpenRights(c.id, t == null ? nick : t.name, c.rightsOf(nick)));
        }
    }

    /** C2S: save the rights of a player. */
    public static class Set {
        public final UUID countryId;
        public final String nick;
        public final int rights;

        public Set(UUID countryId, String nick, int rights) {
            this.countryId = countryId; this.nick = nick; this.rights = rights;
        }

        public static void encode(Set m, FriendlyByteBuf buf) {
            buf.writeUUID(m.countryId);
            buf.writeUtf(m.nick, 64);
            buf.writeVarInt(m.rights);
        }

        public static Set decode(FriendlyByteBuf buf) { return new Set(buf.readUUID(), buf.readUtf(64), buf.readVarInt()); }

        public static void handle(Set m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            String error = TrustService.setRights(sender, m);
            PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                    new CreateResultPacket(error == null, error == null ? "Права сохранены." : error));
        }
    }
}
