package com.politics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Alliances: /alliance opens the windows, /alliance accept (name) answers an invitation, /ac (text) writes in the alliance chat. */
public class AllianceService {
    private static final int MAX_CHAT = 50;
    /** A country can be in at most this many alliances. */
    public static final int MAX_ALLIANCES = 4;

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("alliance")
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    sendList(p);
                    return 1;
                })
                .then(Commands.literal("accept")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((ctx, b) -> {
                                    List<String> names = new ArrayList<>();
                                    ServerPlayer p = ctx.getSource().getPlayer();
                                    if (p != null) {
                                        CountryData data = CountryData.get(p.getServer());
                                        CountryData.Country me = data.byPresident(p.getUUID());
                                        if (me != null) for (CountryData.Alliance a : data.alliances()) if (a.invites.contains(me.id)) names.add(a.name);
                                    }
                                    return SharedSuggestionProvider.suggest(names, b);
                                })
                                .executes(ctx -> accept(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))));

        d.register(Commands.literal("ac")
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayer();
                            CountryData data = CountryData.get(p.getServer());
                            CountryData.Country me = data.byPresident(p.getUUID());
                            List<CountryData.Alliance> mine = me == null ? new ArrayList<>() : data.alliancesOf(me);
                            if (mine.size() != 1) {
                                ctx.getSource().sendFailure(Component.literal(mine.isEmpty()
                                        ? "Писать в чат альянса могут президенты стран альянса."
                                        : "Ваша страна в нескольких альянсах: пишите через окно чата нужного альянса."));
                                return 0;
                            }
                            String err = chat(p, mine.get(0).id, StringArgumentType.getString(ctx, "text"));
                            if (err != null) { ctx.getSource().sendFailure(Component.literal(err)); return 0; }
                            return 1;
                        })));
    }

    // ------------------------------------------------------------------ helpers

    private static CountryData.Country memberCountry(CountryData data, String nick) {
        for (CountryData.Country c : data.all()) if (c.isMember(nick)) return c;
        return null;
    }

    private static int totalChunks(CountryData data, CountryData.Alliance a) {
        int sum = 0;
        for (UUID id : a.members) {
            CountryData.Country c = data.byId(id);
            if (c != null) sum += c.chunks.size();
        }
        return sum;
    }

    private static void error(ServerPlayer p, String msg) {
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new CreateResultPacket(false, msg));
    }

    private static void send(ServerPlayer p, Object msg) {
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    private static Style colour(int rgb) { return Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF)); }

    // ------------------------------------------------------------------ windows

    public static void sendList(ServerPlayer p) { sendList(p, false); }

    /** ownOnly: only the alliances the viewer's country is in. */
    public static void sendList(ServerPlayer p, boolean ownOnly) {
        CountryData data = CountryData.get(p.getServer());
        CountryData.Country pres = data.byPresident(p.getUUID());
        CountryData.Country mem = memberCountry(data, p.getGameProfile().getName());
        List<AlliancePackets.ListMsg.Entry> list = new ArrayList<>();
        for (CountryData.Alliance a : data.alliances()) {
            if (ownOnly && (mem == null || !a.members.contains(mem.id))) continue;
            list.add(new AlliancePackets.ListMsg.Entry(a.id, a.name, a.flag, a.color, a.members.size(), totalChunks(data, a)));
        }
        boolean canCreate = pres != null && !pres.npc && data.alliancesOf(pres).size() < MAX_ALLIANCES;
        int ownCount = mem == null ? 0 : data.alliancesOf(mem).size();
        send(p, new AlliancePackets.ListMsg(list, canCreate, ownCount, ownOnly));
    }

    public static void sendInfo(ServerPlayer p, CountryData.Alliance a) {
        CountryData data = CountryData.get(p.getServer());
        CountryData.Country founder = data.byId(a.founderCountry);
        List<AlliancePackets.InfoMsg.Member> members = new ArrayList<>();
        for (UUID id : a.members) {
            CountryData.Country c = data.byId(id);
            if (c == null) continue;
            members.add(new AlliancePackets.InfoMsg.Member(c.id, c.name, c.flagItem == null ? "minecraft:white_banner" : c.flagItem,
                    c.presidentName, c.color, c.chunks.size(), c.id.equals(a.founderCountry)));
        }
        CountryData.Country pres = data.byPresident(p.getUUID());
        CountryData.Country mem = memberCountry(data, p.getGameProfile().getName());
        boolean own = mem != null && a.members.contains(mem.id);
        boolean founderView = pres != null && pres.id.equals(a.founderCountry);
        boolean canChat = pres != null && a.members.contains(pres.id);
        boolean canLeave = canChat && !founderView;
        send(p, new AlliancePackets.InfoMsg(a.id, a.name, a.flag, a.capital,
                founder == null ? "?" : founder.name, founder == null ? a.founderName : founder.presidentName,
                a.color, totalChunks(data, a), a.created, members, own, founderView, canChat, canLeave));
    }

    private static AlliancePackets.ChatMsg chatPacket(CountryData.Alliance a, boolean open) {
        List<AlliancePackets.ChatMsg.Line> lines = new ArrayList<>();
        for (CountryData.ChatLine l : a.chat) lines.add(new AlliancePackets.ChatMsg.Line(l.sender, l.country, l.text, l.color, l.time));
        return new AlliancePackets.ChatMsg(a.id, a.name, a.color, open, lines);
    }

    // ------------------------------------------------------------------ actions from the windows

    public static void act(ServerPlayer p, AlliancePackets.Action m) {
        CountryData data = CountryData.get(p.getServer());
        CountryData.Country pres = data.byPresident(p.getUUID());
        switch (m.action) {
            case "list" -> sendList(p);
            case "info" -> {
                CountryData.Alliance a = data.allianceById(m.id);
                if (a == null) { error(p, "Альянс не найден."); sendList(p); }
                else sendInfo(p, a);
            }
            case "own" -> {
                CountryData.Country mem = memberCountry(data, p.getGameProfile().getName());
                List<CountryData.Alliance> mine = mem == null ? new ArrayList<>() : data.alliancesOf(mem);
                if (mine.isEmpty()) { error(p, "Ваша страна не состоит в альянсе."); sendList(p); }
                else if (mine.size() == 1) sendInfo(p, mine.get(0));
                else sendList(p, true);
            }
            case "country" -> {
                CountryData.Country c = data.byId(m.id2);
                if (c != null) send(p, new CountriesPacket(CountryService.buildInfos(data), c.name));
            }
            case "create_open" -> {
                if (pres == null || pres.npc) { error(p, "Создать альянс может только президент страны."); return; }
                if (data.alliancesOf(pres).size() >= MAX_ALLIANCES) { error(p, "Ваша страна уже в " + MAX_ALLIANCES + " альянсах: это максимум."); return; }
                List<String> names = new ArrayList<>();
                for (CountryData.Country c : data.all()) if (!c.npc && c != pres) names.add(c.name);
                send(p, new AlliancePackets.CreateOpen(names));
            }
            case "create" -> create(p, data, pres, m);
            case "kick" -> kick(p, data, pres, m);
            case "leave" -> leave(p, data, pres, m.id);
            case "chat_open" -> {
                CountryData.Alliance a = data.allianceById(m.id);
                if (a == null || pres == null || !a.members.contains(pres.id)) { error(p, "Чат доступен президентам стран альянса."); return; }
                send(p, chatPacket(a, true));
            }
            case "chat_send" -> {
                String err = chat(p, m.id, m.s1);
                if (err != null) error(p, err);
            }
            case "invite_open" -> {
                CountryData.Alliance a = data.allianceById(m.id);
                if (a == null) { error(p, "Альянс не найден."); return; }
                if (pres == null || !pres.id.equals(a.founderCountry)) { error(p, "Приглашать страны может только основатель альянса."); return; }
                List<String> names = new ArrayList<>();
                for (CountryData.Country c : data.all()) if (!c.npc && !a.members.contains(c.id)) names.add(c.name);
                send(p, new AlliancePackets.InviteOpen(a.id, a.name, names));
            }
            case "invite" -> inviteMore(p, data, pres, m);
            default -> { }
        }
    }

    private static void create(ServerPlayer p, CountryData data, CountryData.Country pres, AlliancePackets.Action m) {
        if (pres == null || pres.npc) { error(p, "Создать альянс может только президент страны."); return; }
        if (data.alliancesOf(pres).size() >= MAX_ALLIANCES) { error(p, "Ваша страна уже в " + MAX_ALLIANCES + " альянсах: это максимум."); return; }
        String name = m.s1.trim(), capital = m.s2.trim();
        if (name.isEmpty() || name.length() > 32) { error(p, "Название альянса: от 1 до 32 символов."); return; }
        if (capital.isEmpty() || capital.length() > 32) { error(p, "Название «столицы» альянса: от 1 до 32 символов."); return; }
        if (data.allianceByName(name) != null) { error(p, "Альянс с таким названием уже есть."); return; }

        Set<UUID> invited = new LinkedHashSet<>();
        for (String part : m.s3.split(",")) {
            String n = part.trim();
            if (n.isEmpty()) continue;
            CountryData.Country c = data.byName(n);
            if (c == null) { error(p, "Страна не найдена: " + n); return; }
            if (c == pres) { error(p, "Свою страну приглашать не нужно: она входит в альянс сама."); return; }
            if (c.npc) { error(p, "Страна «" + c.name + "» не отвечает на приглашения."); return; }
            if (data.alliancesOf(c).size() >= MAX_ALLIANCES) { error(p, "Страна «" + c.name + "» уже в " + MAX_ALLIANCES + " альянсах."); return; }
            invited.add(c.id);
        }

        CountryData.Alliance a = new CountryData.Alliance();
        a.name = name;
        a.capital = capital;
        a.flag = m.s4.isEmpty() ? "minecraft:white_banner" : m.s4;
        int r = Math.max(1, Math.min(255, m.color >> 16 & 0xFF));
        int g = Math.max(1, Math.min(255, m.color >> 8 & 0xFF));
        int b = Math.max(1, Math.min(255, m.color & 0xFF));
        a.color = (r << 16) | (g << 8) | b;
        a.founderCountry = pres.id;
        a.founderName = pres.presidentName;
        a.created = System.currentTimeMillis();
        a.members.add(pres.id);
        a.invites.addAll(invited);
        data.alliances().add(a);
        data.setDirty();

        for (UUID id : invited) {
            CountryData.Country c = data.byId(id);
            if (c != null) invite(p.getServer(), data, a, pres, c);
        }
        send(p, new CreateResultPacket(true, "Альянс создан!"));
        sendInfo(p, a);
        p.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("Создан альянс «" + a.name + "» (основатель: " + pres.name + ")").setStyle(colour(a.color)), false);
    }

    /** The "Пригласить" button of an existing alliance (founder only). */
    private static void inviteMore(ServerPlayer p, CountryData data, CountryData.Country pres, AlliancePackets.Action m) {
        CountryData.Alliance a = data.allianceById(m.id);
        if (a == null) { error(p, "Альянс не найден."); return; }
        if (pres == null || !pres.id.equals(a.founderCountry)) { error(p, "Приглашать страны может только основатель альянса."); return; }
        List<CountryData.Country> targets = new ArrayList<>();
        for (String part : m.s3.split(",")) {
            String n = part.trim();
            if (n.isEmpty()) continue;
            CountryData.Country c = data.byName(n);
            if (c == null) { error(p, "Страна не найдена: " + n); return; }
            if (c.npc) { error(p, "Страна «" + c.name + "» не отвечает на приглашения."); return; }
            if (a.members.contains(c.id)) { error(p, "Страна «" + c.name + "» уже в этом альянсе."); return; }
            if (data.alliancesOf(c).size() >= MAX_ALLIANCES) { error(p, "Страна «" + c.name + "» уже в " + MAX_ALLIANCES + " альянсах."); return; }
            if (!targets.contains(c)) targets.add(c);
        }
        if (targets.isEmpty()) { error(p, "Укажите хотя бы одну страну."); return; }
        for (CountryData.Country c : targets) {
            a.invites.add(c.id);
            invite(p.getServer(), data, a, pres, c);
        }
        data.setDirty();
        send(p, new CreateResultPacket(true, "Приглашения отправлены."));
        sendInfo(p, a);
    }

    /** The invitation arrives as a letter (/mail) and, if the president is online, with a click-to-accept line. */
    private static void invite(MinecraftServer server, CountryData data, CountryData.Alliance a,
                               CountryData.Country from, CountryData.Country to) {
        CountryData.Mail mail = new CountryData.Mail();
        mail.fromCountry = from.name;
        mail.fromColor = from.color;
        mail.fromPlayer = from.presidentName;
        mail.text = "Приглашаем вашу страну в альянс «" + a.name + "»! Чтобы вступить, напишите /alliance accept " + a.name;
        MailService.deliver(server, to, mail);

        ServerPlayer pres = server.getPlayerList().getPlayer(to.presidentId);
        if (pres != null) {
            pres.sendSystemMessage(Component.literal("[Принять приглашение в альянс «" + a.name + "»]")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/alliance accept " + a.name))));
        }
    }

    private static int accept(CommandSourceStack src, String name) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayer();
        CountryData data = CountryData.get(src.getServer());
        CountryData.Country me = data.byPresident(p.getUUID());
        if (me == null || me.npc) { src.sendFailure(Component.literal("Принимать приглашения может только президент страны.")); return 0; }
        if (data.alliancesOf(me).size() >= MAX_ALLIANCES) { src.sendFailure(Component.literal("Ваша страна уже в " + MAX_ALLIANCES + " альянсах: это максимум.")); return 0; }
        CountryData.Alliance a = data.allianceByName(name);
        if (a == null) { src.sendFailure(Component.literal("Альянс не найден: " + name.trim())); return 0; }
        if (a.members.contains(me.id)) { src.sendFailure(Component.literal("Ваша страна уже в этом альянсе.")); return 0; }
        if (!a.invites.contains(me.id)) { src.sendFailure(Component.literal("Вашу страну в этот альянс не приглашали.")); return 0; }

        a.invites.remove(me.id);
        a.members.add(me.id);
        data.setDirty();
        src.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("Страна " + me.name + " вступила в альянс «" + a.name + "»").setStyle(colour(a.color)), false);
        return 1;
    }

    private static void kick(ServerPlayer p, CountryData data, CountryData.Country pres, AlliancePackets.Action m) {
        CountryData.Alliance a = data.allianceById(m.id);
        if (a == null) { error(p, "Альянс не найден."); return; }
        if (pres == null || !pres.id.equals(a.founderCountry)) { error(p, "Исключать страны может только основатель альянса."); return; }
        if (m.id2.equals(a.founderCountry)) { error(p, "Основателя исключить нельзя."); return; }
        CountryData.Country target = data.byId(m.id2);
        if (target == null || !a.members.remove(m.id2)) { error(p, "Этой страны уже нет в альянсе."); sendInfo(p, a); return; }
        data.setDirty();

        CountryData.Mail mail = new CountryData.Mail();
        mail.fromCountry = pres.name;
        mail.fromColor = pres.color;
        mail.fromPlayer = pres.presidentName;
        mail.text = "Ваша страна исключена из альянса «" + a.name + "».";
        MailService.deliver(p.getServer(), target, mail);
        sendInfo(p, a);
    }

    private static void leave(ServerPlayer p, CountryData data, CountryData.Country pres, UUID id) {
        CountryData.Alliance a = data.allianceById(id);
        if (a == null || pres == null || !a.members.contains(pres.id)) { error(p, "Ваша страна не состоит в этом альянсе."); return; }
        if (pres.id.equals(a.founderCountry)) { error(p, "Основатель не может выйти из своего альянса."); return; }
        a.members.remove(pres.id);
        data.setDirty();

        CountryData.Country founder = data.byId(a.founderCountry);
        if (founder != null) {
            CountryData.Mail mail = new CountryData.Mail();
            mail.fromCountry = pres.name;
            mail.fromColor = pres.color;
            mail.fromPlayer = pres.presidentName;
            mail.text = "Наша страна вышла из альянса «" + a.name + "».";
            MailService.deliver(p.getServer(), founder, mail);
        }
        sendList(p);
    }

    // ------------------------------------------------------------------ alliance chat

    /** Returns an error message or null. The presidents of all member countries get the line. */
    public static String chat(ServerPlayer p, UUID allianceId, String raw) {
        CountryData data = CountryData.get(p.getServer());
        CountryData.Country pres = data.byPresident(p.getUUID());
        CountryData.Alliance a = data.allianceById(allianceId);
        if (a == null || pres == null || !a.members.contains(pres.id)) return "Писать в чат альянса могут президенты стран альянса.";

        StringBuilder sb = new StringBuilder();
        for (char ch : raw.toCharArray()) if (ch != '§' && (ch == ' ' || !Character.isISOControl(ch))) sb.append(ch);
        String text = sb.toString().trim();
        if (text.isEmpty()) return "Напишите сообщение.";
        if (text.length() > 200) text = text.substring(0, 200);

        CountryData.ChatLine l = new CountryData.ChatLine();
        l.sender = p.getGameProfile().getName();
        l.country = pres.name;
        l.color = pres.color;
        l.text = text;
        l.time = System.currentTimeMillis();
        a.chat.add(l);
        while (a.chat.size() > MAX_CHAT) a.chat.remove(0);
        data.setDirty();

        AlliancePackets.ChatMsg refresh = chatPacket(a, false);
        for (UUID id : a.members) {
            CountryData.Country c = data.byId(id);
            if (c == null) continue;
            ServerPlayer target = p.getServer().getPlayerList().getPlayer(c.presidentId);
            if (target == null) continue;
            target.sendSystemMessage(Component.literal("[Альянс «" + a.name + "»] ").setStyle(colour(a.color))
                    .append(Component.literal(l.sender + " (" + l.country + "): ").setStyle(colour(l.color)))
                    .append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
            send(target, refresh);
        }
        return null;
    }
}
