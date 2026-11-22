package com.politics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** /givechunk: the president gives the chunk he is standing in to another country. */
public class GiveChunkService {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("givechunk").executes(ctx -> open(ctx.getSource())));
    }

    private static boolean isCapitalChunk(CountryData.Country c, long key) {
        return c.capitalPos != null && key == CountryData.chunkKey(c.capitalPos.getX() >> 4, c.capitalPos.getZ() >> 4);
    }

    /** The reason why this chunk cannot be given away, or null if it can. */
    private static String check(CountryData data, CountryData.Country me, String dim, int cx, int cz) {
        long k = CountryData.chunkKey(cx, cz);
        if (!me.dimension.equals(dim) || !me.chunks.contains(k)) {
            return "Вы стоите не в чанке своей страны. Встаньте в чанк, который хотите отдать.";
        }
        if (isCapitalChunk(me, k)) return "Столичный чанк отдать нельзя.";
        if (me.chunks.size() <= 1) return "Это последний чанк вашей страны, его отдать нельзя.";
        if (data.warOf(me) != null) return "Во время войны отдавать чанки нельзя.";
        return null;
    }

    private static int open(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        CountryData data = CountryData.get(src.getServer());
        CountryData.Country me = data.byPresident(p.getUUID());
        if (me == null) { src.sendFailure(Component.literal("Передавать чанки может только президент страны.")); return 0; }

        int cx = p.chunkPosition().x, cz = p.chunkPosition().z;
        String err = check(data, me, p.level().dimension().location().toString(), cx, cz);
        if (err != null) { src.sendFailure(Component.literal(err)); return 0; }

        List<String> names = new ArrayList<>();
        for (CountryData.Country c : data.all()) if (c != me) names.add(c.name);
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Open(cx, cz, names));
        return 1;
    }

    /** Returns an error message or null if the chunk was given. */
    public static String give(ServerPlayer p, int cx, int cz, String targetName) {
        MinecraftServer server = p.getServer();
        CountryData data = CountryData.get(server);
        CountryData.Country me = data.byPresident(p.getUUID());
        if (me == null) return "Передавать чанки может только президент страны.";
        String err = check(data, me, me.dimension, cx, cz); // the chunk is checked by the country, the window may be old
        if (err != null) return err;

        CountryData.Country target = data.byName(targetName);
        if (target == null) return "Страна не найдена: " + targetName.trim();
        if (target == me) return "Нельзя отдать чанк самому себе.";
        if (!target.dimension.equals(me.dimension)) return "Страны находятся в разных измерениях.";
        if (data.warOf(target) != null) return "Страна «" + target.name + "» сейчас воюет, ей нельзя передать чанк.";

        long k = CountryData.chunkKey(cx, cz);
        me.chunks.remove(k);
        target.chunks.add(k);
        data.setDirty();

        Component msg = Component.literal("Страна «" + me.name + "» передала стране «" + target.name + "» чанк " + cx + ", " + cz + ".")
                .withStyle(ChatFormatting.YELLOW);
        for (CountryData.Country c : new CountryData.Country[]{me, target}) {
            ServerPlayer pres = server.getPlayerList().getPlayer(c.presidentId);
            if (pres == null) continue;
            if (c == target) {
                PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pres),
                        new NoticePacket("Новый чанк", me.name + " передала вам чанк " + cx + ", " + cz, 0xFFFF55));
            }
            pres.sendSystemMessage(msg);
        }
        return null;
    }

    // ------------------------------------------------------------------ packets

    /** S2C: opens the window with the question and the country field. */
    public static class Open {
        public final int cx, cz;
        public final List<String> countries;

        public Open(int cx, int cz, List<String> countries) { this.cx = cx; this.cz = cz; this.countries = countries; }

        public static void encode(Open m, FriendlyByteBuf buf) {
            buf.writeInt(m.cx);
            buf.writeInt(m.cz);
            buf.writeVarInt(m.countries.size());
            for (String s : m.countries) buf.writeUtf(s, 100);
        }

        public static Open decode(FriendlyByteBuf buf) {
            int cx = buf.readInt(), cz = buf.readInt();
            int n = buf.readVarInt();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < n; i++) list.add(buf.readUtf(100));
            return new Open(cx, cz, list);
        }

        public static void handle(Open m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.openGiveChunk(m));
        }
    }

    /** C2S: the answer of the window. */
    public static class Confirm {
        public final int cx, cz;
        public final String country;

        public Confirm(int cx, int cz, String country) { this.cx = cx; this.cz = cz; this.country = country; }

        public static void encode(Confirm m, FriendlyByteBuf buf) {
            buf.writeInt(m.cx);
            buf.writeInt(m.cz);
            buf.writeUtf(m.country, 100);
        }

        public static Confirm decode(FriendlyByteBuf buf) { return new Confirm(buf.readInt(), buf.readInt(), buf.readUtf(100)); }

        public static void handle(Confirm m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            String err = give(sender, m.cx, m.cz, m.country);
            PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                    new CreateResultPacket(err == null, err == null ? "Чанк передан." : err));
        }
    }
}
