package com.politics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Письма между странами: /mail (страна). Письмо сразу появляется на экране президента. */
public class MailService {
    public static final int MAX_TEXT = 300;
    private static final int MAX_INBOX = 20;

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("mail")
                .then(Commands.argument("country", StringArgumentType.greedyString())
                        .suggests((ctx, b) -> {
                            List<String> names = new ArrayList<>();
                            for (CountryData.Country c : CountryData.get(ctx.getSource().getServer()).all()) names.add(c.name);
                            return SharedSuggestionProvider.suggest(names, b);
                        })
                        .executes(ctx -> open(ctx.getSource(), StringArgumentType.getString(ctx, "country")))));
    }

    private static CountryData.Country countryOf(CountryData data, String nick) {
        for (CountryData.Country c : data.all()) if (c.isMember(nick)) return c;
        return null;
    }

    private static int open(CommandSourceStack src, String name) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        CountryData data = CountryData.get(src.getServer());
        CountryData.Country target = data.byName(name);
        if (target == null) {
            src.sendFailure(Component.literal("Страна не найдена: " + name.trim()));
            return 0;
        }
        if (target == countryOf(data, p.getGameProfile().getName())) {
            src.sendFailure(Component.literal("Это ваша страна. Письма можно писать только другим странам."));
            return 0;
        }
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new OpenMailPacket(target.id, target.name, target.presidentName, target.color));
        return 1;
    }

    /** Returns an error message, or null if the letter was sent. */
    public static String send(ServerPlayer p, UUID targetId, String raw) {
        CountryData data = CountryData.get(p.getServer());
        CountryData.Country target = data.byId(targetId);
        if (target == null) return "Страна не найдена.";
        String nick = p.getGameProfile().getName();
        CountryData.Country mine = countryOf(data, nick);
        if (mine == target) return "Нельзя отправить письмо своей стране.";

        String text = clean(raw);
        if (text.isEmpty()) return "Напишите текст письма.";

        CountryData.Mail m = new CountryData.Mail();
        m.fromCountry = mine == null ? nick : mine.name;
        m.fromColor = mine == null ? 0xFFFFFF : mine.color;
        m.fromPlayer = nick;
        m.text = text;
        deliver(p.getServer(), target, m);
        p.displayClientMessage(Component.literal("Письмо отправлено президенту страны «" + target.name + "».")
                .withStyle(s -> s.withColor(TextColor.fromRgb(target.color))), true);
        return null;
    }

    private static String clean(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char ch : raw.toCharArray()) {
            if (ch == '§') continue;
            if (ch == '\n' || ch == '\t' || !Character.isISOControl(ch)) sb.append(ch);
        }
        String s = sb.toString().trim();
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }

    /** The letter appears on the president's screen at once; if they are offline it waits until they log in. */
    public static void deliver(MinecraftServer server, CountryData.Country target, CountryData.Mail m) {
        if (target.npc) return; // свиньи читают, но не отвечают
        ServerPlayer pres = server.getPlayerList().getPlayer(target.presidentId);
        if (pres != null) {
            show(pres, m);
        } else {
            target.inbox.add(m);
            while (target.inbox.size() > MAX_INBOX) target.inbox.remove(0);
            CountryData.get(server).setDirty();
        }
    }

    private static void show(ServerPlayer p, CountryData.Mail m) {
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new NoticePacket("Письмо от " + m.fromCountry, m.text + "\n— " + m.fromPlayer, m.fromColor));
        // копия в чат, чтобы длинное письмо можно было перечитать
        p.sendSystemMessage(Component.literal("Письмо от " + m.fromCountry + " (" + m.fromPlayer + "): " + m.text)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(m.fromColor))));
    }

    public static void deliverPending(ServerPlayer p) {
        CountryData data = CountryData.get(p.getServer());
        CountryData.Country c = data.byPresident(p.getUUID());
        if (c == null || c.inbox.isEmpty()) return;
        List<CountryData.Mail> list = new ArrayList<>(c.inbox);
        c.inbox.clear();
        data.setDirty();
        for (CountryData.Mail m : list) show(p, m);
    }

    /** When a player country appears, the pigs greet its president. */
    public static void welcomeFromPigs(MinecraftServer server, CountryData.Country newCountry) {
        if (newCountry.npc) return;
        CountryData.Country pigs = CountryData.get(server).byName(PigProtocol.NAME);
        if (pigs == null || !pigs.npc) return;
        CountryData.Mail m = new CountryData.Mail();
        m.fromCountry = pigs.name;
        m.fromColor = pigs.color;
        m.fromPlayer = pigs.presidentName;
        m.text = "Добро пожаловать в мир!";
        deliver(server, newCountry, m);
    }
}
