package com.politics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /peace: one side offers peace, the leader of the other side accepts with /peace accept. */
public class PeaceService {
    private static final int YELLOW = 0xFFFF55;

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("peace")
                .executes(ctx -> propose(ctx.getSource()))
                .then(Commands.literal("accept").executes(ctx -> accept(ctx.getSource()))));
    }

    private static List<CountryData.Country> side(CountryData data, List<UUID> ids) {
        List<CountryData.Country> out = new ArrayList<>();
        for (UUID id : ids) {
            CountryData.Country c = data.byId(id);
            if (c != null) out.add(c);
        }
        return out;
    }

    private static int propose(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayer();
        MinecraftServer server = src.getServer();
        CountryData data = CountryData.get(server);
        CountryData.Country me = data.byPresident(p.getUUID());
        if (me == null) { src.sendFailure(Component.literal("Предлагать мир может только президент страны.")); return 0; }
        CountryData.War w = data.warOf(me);
        if (w == null) { src.sendFailure(Component.literal("Ваша страна сейчас не воюет.")); return 0; }

        // the other side has offered peace to me already: that is an answer
        if (me.id.equals(w.peaceTo)) return accept(src);

        List<UUID> enemyIds = w.onAttackers(me.id) ? w.defenders : w.attackers;
        List<CountryData.Country> enemy = side(data, enemyIds);
        if (enemy.isEmpty()) { src.sendFailure(Component.literal("Не с кем заключать мир.")); return 0; }
        CountryData.Country leader = enemy.get(0);
        if (leader.npc) {
            src.sendFailure(Component.literal("Страна «" + leader.name + "» не отвечает на предложения мира."));
            return 0;
        }

        w.peaceFrom = me.id;
        w.peaceTo = leader.id;
        data.setDirty();

        String text = me.name + " предлагает вам мир, если примите предложение - война закончится и все территории вернутся на свои места. "
                + "если хотите закончить войну на определенных условиях то обсудите это с " + me.name;
        for (CountryData.Country c : enemy) {
            ServerPlayer pres = server.getPlayerList().getPlayer(c.presidentId);
            if (pres == null) continue;
            PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pres), new NoticePacket("Предложение мира", text, YELLOW));
            pres.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.YELLOW));
            if (c == leader) {
                pres.sendSystemMessage(Component.literal("[Принять мир]")
                        .withStyle(s -> s.withColor(ChatFormatting.GREEN).withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/peace accept"))));
            } else {
                pres.sendSystemMessage(Component.literal("Ответить на предложение может президент страны «" + leader.name + "».")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        src.sendSuccess(() -> Component.literal("Предложение мира отправлено стране «" + leader.name + "».")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int accept(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayer();
        MinecraftServer server = src.getServer();
        CountryData data = CountryData.get(server);
        CountryData.Country me = data.byPresident(p.getUUID());
        CountryData.War w = me == null ? null : data.warOf(me);
        if (w == null) { src.sendFailure(Component.literal("Ваша страна сейчас не воюет.")); return 0; }
        if (w.peaceTo == null) { src.sendFailure(Component.literal("Вам не предлагали мир.")); return 0; }
        if (!me.id.equals(w.peaceTo)) {
            CountryData.Country leader = data.byId(w.peaceTo);
            src.sendFailure(Component.literal("Принять мир может только президент страны «" + (leader == null ? "?" : leader.name) + "»."));
            return 0;
        }

        StringBuilder names = new StringBuilder();
        for (UUID id : w.attackers) { CountryData.Country c = data.byId(id); if (c != null) { if (names.length() > 0) names.append(", "); names.append(c.name); } }
        names.append(" и ");
        boolean first = true;
        for (UUID id : w.defenders) { CountryData.Country c = data.byId(id); if (c != null) { if (!first) names.append(", "); names.append(c.name); first = false; } }

        data.wars().remove(w); // the chunks that were marked as captured simply stay with their owners
        data.setDirty();
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("Мир! Война между " + names + " закончилась, все территории остались на своих местах.")
                        .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
