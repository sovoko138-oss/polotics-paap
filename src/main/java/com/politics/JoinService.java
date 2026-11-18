package com.politics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Joining a country: the player writes /join, the president answers with /accept. */
public class JoinService {
    private static final int YELLOW = 0xFFFF55;

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("join")
                .then(Commands.argument("country", StringArgumentType.greedyString())
                        .suggests((ctx, b) -> {
                            List<String> names = new ArrayList<>();
                            for (CountryData.Country c : CountryData.get(ctx.getSource().getServer()).all()) {
                                if (c.npc) continue;
                                names.add(c.name);
                                names.add(c.presidentName);
                            }
                            return SharedSuggestionProvider.suggest(names, b);
                        })
                        .executes(ctx -> join(ctx.getSource(), StringArgumentType.getString(ctx, "country")))));

        d.register(Commands.literal("accept")
                .executes(ctx -> list(ctx.getSource()))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            List<String> nicks = new ArrayList<>();
                            try {
                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                CountryData.Country c = ResidentService.leaderCountry(p, CountryData.get(p.getServer()));
                                if (c != null) for (CountryData.JoinRequest r : c.joinRequests) nicks.add(r.nick);
                            } catch (CommandSyntaxException ignored) { }
                            return SharedSuggestionProvider.suggest(nicks, b);
                        })
                        .executes(ctx -> accept(ctx.getSource(), StringArgumentType.getString(ctx, "player")))));
    }

    private static CountryData.Country countryOf(CountryData data, String nick) {
        for (CountryData.Country c : data.all()) if (c.isMember(nick)) return c;
        return null;
    }

    private static int join(CommandSourceStack src, String arg) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        CountryData data = CountryData.get(src.getServer());
        String nick = p.getGameProfile().getName();

        CountryData.Country current = countryOf(data, nick);
        if (current != null) {
            src.sendFailure(Component.literal("Вы уже живёте в стране «" + current.name + "»."));
            return 0;
        }
        String q = arg.trim();
        CountryData.Country target = data.byName(q);
        if (target == null) {
            for (CountryData.Country c : data.all()) if (c.presidentName.equalsIgnoreCase(q)) { target = c; break; }
        }
        if (target == null) {
            src.sendFailure(Component.literal("Не нашёл страну или президента: " + q));
            return 0;
        }
        if (target.npc) {
            src.sendFailure(Component.literal("Эта страна не принимает игроков."));
            return 0;
        }
        for (CountryData.JoinRequest r : target.joinRequests) {
            if (r.nick.equalsIgnoreCase(nick)) {
                src.sendFailure(Component.literal("Вы уже подали заявку в эту страну."));
                return 0;
            }
        }
        addRequest(src.getServer(), data, target, nick);
        CountryData.Country t = target;
        src.sendSuccess(() -> Component.literal("Заявка отправлена президенту страны «" + t.name + "».")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static void addRequest(MinecraftServer server, CountryData data, CountryData.Country target, String nick) {
        CountryData.JoinRequest r = new CountryData.JoinRequest();
        r.nick = nick;
        r.prevCountry = data.historyOf(nick);
        target.joinRequests.add(r);
        data.setDirty();
        notifyPresident(server, target, nick);
    }

    /** The president sees a yellow notice on the screen and a hint in the chat. */
    private static void notifyPresident(MinecraftServer server, CountryData.Country c, String nick) {
        ServerPlayer pres = server.getPlayerList().getPlayer(c.presidentId);
        if (pres == null) return;
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pres),
                new NoticePacket("Заявка на вступление", "Игрок " + nick + " хочет вступить в вашу страну", YELLOW));
        pres.sendSystemMessage(Component.literal("Чтобы принять игрока " + nick + " в страну, напишите команду /accept")
                .withStyle(s -> s.withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/accept"))));
    }

    /** The list of everybody who wants to join; hovering over a nick shows where they lived before. */
    private static int list(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        CountryData data = CountryData.get(src.getServer());
        CountryData.Country c = ResidentService.leaderCountry(p, data);
        if (c == null) {
            src.sendFailure(Component.literal("Принимать игроков могут только президент и заместители."));
            return 0;
        }
        if (c.joinRequests.isEmpty()) {
            src.sendSuccess(() -> Component.literal("Заявок на вступление нет.").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        src.sendSuccess(() -> Component.literal("Хотят вступить в страну «" + c.name + "»:").withStyle(ChatFormatting.YELLOW), false);
        for (CountryData.JoinRequest r : c.joinRequests) {
            Component history = r.prevCountry.isEmpty()
                    ? Component.literal("Нет истории жизни в странах")
                    : Component.literal("Жил в стране: " + r.prevCountry);
            MutableComponent hover = Component.empty().append(history)
                    .append(Component.literal("\nНажмите, чтобы принять").withStyle(ChatFormatting.GRAY));
            MutableComponent line = Component.literal(" - ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(r.nick).withStyle(s -> s.withColor(ChatFormatting.YELLOW).withUnderlined(true)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/accept " + r.nick))));
            src.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int accept(CommandSourceStack src, String nick) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        CountryData data = CountryData.get(src.getServer());
        CountryData.Country c = ResidentService.leaderCountry(p, data);
        if (c == null) {
            src.sendFailure(Component.literal("Принимать игроков могут только президент и заместители."));
            return 0;
        }
        CountryData.JoinRequest req = null;
        for (CountryData.JoinRequest r : c.joinRequests) if (r.nick.equalsIgnoreCase(nick)) { req = r; break; }
        if (req == null) {
            src.sendFailure(Component.literal("Заявки от игрока " + nick + " нет."));
            return 0;
        }
        CountryData.Country other = countryOf(data, req.nick);
        if (other != null) {
            c.joinRequests.remove(req);
            data.setDirty();
            src.sendFailure(Component.literal("Игрок " + req.nick + " уже живёт в стране «" + other.name + "»."));
            return 0;
        }

        CountryData.Resident res = new CountryData.Resident();
        res.name = req.nick;
        c.residents.add(res);
        c.joinRequests.remove(req);
        data.setDirty();

        String accepted = req.nick;
        src.sendSuccess(() -> Component.literal("Игрок " + accepted + " принят в страну «" + c.name + "».")
                .withStyle(ChatFormatting.YELLOW), false);
        for (ServerPlayer sp : src.getServer().getPlayerList().getPlayers()) {
            if (sp.getGameProfile().getName().equalsIgnoreCase(accepted)) {
                sp.sendSystemMessage(Component.literal("Вас приняли в страну «" + c.name + "»!")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        return 1;
    }

    /** TEST: two fake requests right after the president creates a country: Steve (lived in Криперляндия) and Alex (nowhere). */
    public static void addDemoRequests(MinecraftServer server, CountryData.Country c) {
        if (!Config.DEBUG_FAKE_JOIN_REQUESTS.get() || c.npc) return;
        CountryData data = CountryData.get(server);
        String[][] demo = {{"Steve", "Криперляндия"}, {"Alex", ""}};
        for (String[] d : demo) {
            boolean exists = false;
            for (CountryData.JoinRequest r : c.joinRequests) if (r.nick.equalsIgnoreCase(d[0])) exists = true;
            if (exists) continue;
            CountryData.JoinRequest r = new CountryData.JoinRequest();
            r.nick = d[0];
            r.prevCountry = d[1];
            c.joinRequests.add(r);
            notifyPresident(server, c, r.nick);
        }
        data.setDirty();
    }

    /** A president who logs in is reminded about waiting requests. */
    public static void loginHint(ServerPlayer p) {
        CountryData data = CountryData.get(p.getServer());
        CountryData.Country c = data.byPresident(p.getUUID());
        if (c == null || c.joinRequests.isEmpty()) return;
        p.sendSystemMessage(Component.literal("Игроки хотят вступить в вашу страну: " + c.joinRequests.size()
                + ". Напишите команду /accept").withStyle(ChatFormatting.YELLOW));
    }
}
