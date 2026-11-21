package com.politics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.*;

/** /war (страна): объявление войны. Ход самой войны ведёт WarManager. */
public class WarService {
    public static final int WAR_RED = 0xFF5555;

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("war")
                .then(Commands.argument("country", StringArgumentType.greedyString())
                        .suggests((ctx, b) -> {
                            List<String> names = new ArrayList<>();
                            for (CountryData.Country c : CountryData.get(ctx.getSource().getServer()).all()) names.add(c.name);
                            return SharedSuggestionProvider.suggest(names, b);
                        })
                        .executes(ctx -> war(ctx.getSource(), StringArgumentType.getString(ctx, "country")))));
    }

    private static int war(CommandSourceStack src, String name) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayer();
        CountryData data = CountryData.get(src.getServer());
        CountryData.Country mine = data.byPresident(p.getUUID());
        if (mine == null) {
            src.sendFailure(Component.literal("Войну может объявлять только президент страны."));
            return 0;
        }
        CountryData.Country target = data.byName(name);
        if (target == null) {
            src.sendFailure(Component.literal("Страна не найдена: " + name.trim()));
            return 0;
        }
        if (target == mine) {
            src.sendFailure(Component.literal("Нельзя объявить войну своей стране."));
            return 0;
        }
        int need = Config.WAR_MIN_CHUNKS.get();
        if (mine.chunks.size() < need) {
            src.sendFailure(Component.literal("Чтобы воевать, нужно минимум " + need
                    + " чанков. Сейчас в вашей стране: " + mine.chunks.size() + "."));
            return 0;
        }
        if (data.allied(mine, target)) {
            src.sendFailure(Component.literal("Вы с этой страной в одном альянсе. Чтобы объявить ей войну, сначала выйдите из альянса."));
            return 0;
        }
        if (data.warOf(mine) != null) {
            src.sendFailure(Component.literal("Ваша страна уже воюет."));
            return 0;
        }
        if (data.warOf(target) != null) {
            src.sendFailure(Component.literal("Эта страна уже воюет."));
            return 0;
        }
        String limitError = schedulePlayerWar(src.getServer(), data, mine, target);
        if (limitError != null) { src.sendFailure(Component.literal(limitError)); return 0; }
        src.sendSuccess(() -> Component.literal("Война объявлена и назначена на " + scheduledMoscowText(data.wars().get(data.wars().size() - 1).scheduledStartMs) + " МСК."), false);
        return 1;
    }

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final int[] WAR_SLOTS_MINUTES = {0, 20, 40, 60, 80};

    /** Applies the daily 13:00 reset and schedules a player war for tomorrow's slot. */
    private static String schedulePlayerWar(MinecraftServer server, CountryData data, CountryData.Country attacker, CountryData.Country defender) {
        long now = System.currentTimeMillis();
        ZonedDateTime moscowNow = Instant.ofEpochMilli(now).atZone(MOSCOW);
        long resetAt = data.warDeclarationResetAt();
        int count = data.dailyWarDeclarations();
        if (resetAt == 0L) resetAt = nextReset(moscowNow).toInstant().toEpochMilli();
        if (now >= resetAt) {
            count = 0;
            resetAt = nextReset(moscowNow).toInstant().toEpochMilli();
        }
        if (count >= WAR_SLOTS_MINUTES.length) {
            data.setWarDeclarationState(count, resetAt);
            ZonedDateTime reset = Instant.ofEpochMilli(resetAt).atZone(MOSCOW);
            return "На сегодня лимит объявлений войн исчерпан (5). Новые объявления будут доступны после 13:00 МСК " + reset.toLocalDate() + ".";
        }
        ZonedDateTime scheduled = findNextAvailableSlot(data, moscowNow.toLocalDate().plusDays(1));
        declare(server, data, attacker, defender, scheduled.toInstant().toEpochMilli());
        CountryData.War w = data.warOf(attacker);
        if (w == null) return "Не удалось зарегистрировать войну.";
        count++;
        data.setWarDeclarationState(count, resetAt);
        data.setDirty();
        return null;
    }

    /** Avoid assigning two wars to the same time when the 13:00 quota reset creates another batch. */
    private static ZonedDateTime findNextAvailableSlot(CountryData data, LocalDate firstDate) {
        for (int day = 0; day < 366; day++) {
            LocalDate date = firstDate.plusDays(day);
            for (int minute : WAR_SLOTS_MINUTES) {
                ZonedDateTime candidate = date.atTime(17, 0).atZone(MOSCOW).plusMinutes(minute);
                long epoch = candidate.toInstant().toEpochMilli();
                boolean occupied = false;
                for (CountryData.War war : data.wars()) {
                    if (war.scheduledStartMs == epoch) { occupied = true; break; }
                }
                if (!occupied) return candidate;
            }
        }
        // Practically unreachable; still preserve a deterministic schedule.
        return firstDate.plusDays(366).atTime(17, 0).atZone(MOSCOW);
    }

    private static ZonedDateTime nextReset(ZonedDateTime now) {
        ZonedDateTime reset = now.toLocalDate().atTime(13, 0).atZone(MOSCOW);
        if (!now.isBefore(reset)) reset = reset.plusDays(1);
        return reset;
    }

    public static String scheduledMoscowText(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(MOSCOW).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    /** Declares the war; the allies of both countries join their sides (a country allied to both stays out). */
    public static void declare(MinecraftServer server, CountryData data, CountryData.Country attacker, CountryData.Country victim) {
        declare(server, data, attacker, victim, 0L);
    }

    public static void declare(MinecraftServer server, CountryData data, CountryData.Country attacker, CountryData.Country victim, long scheduledStartMs) {
        CountryData.War war = data.declareWar(attacker, victim, server.overworld().getGameTime());
        war.scheduledStartMs = scheduledStartMs;

        List<CountryData.Country> joinedA = new ArrayList<>(), joinedB = new ArrayList<>();
        for (CountryData.Country c : data.all()) {
            if (c == attacker || c == victim || data.warOf(c) != null) continue;
            boolean allyA = data.allied(c, attacker), allyB = data.allied(c, victim);
            if (allyA == allyB) continue; // no ally of either, or an ally of both: stays out
            if (allyB) { war.defenders.add(c.id); joinedB.add(c); } else { war.attackers.add(c.id); joinedA.add(c); }
        }
        data.setDirty();

        int prep = Config.WAR_PREP_SECONDS.get();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String nick = sp.getGameProfile().getName();
            MutableComponent msg;
            if (attacker.isMember(nick)) {
                msg = Component.literal("Ваша страна объявила войну стране ").append(coloured(victim)).append("!");
            } else {
                msg = coloured(attacker).append(Component.literal(" объявила войну стране ")).append(coloured(victim)).append("!");
            }
            sp.sendSystemMessage(msg);
            for (CountryData.Country j : joinedB) {
                sp.sendSystemMessage(coloured(j).append(Component.literal(" вступилась за союзника ")).append(coloured(victim))
                        .append(Component.literal(" и вступает в войну.")));
            }
            for (CountryData.Country j : joinedA) {
                sp.sendSystemMessage(coloured(j).append(Component.literal(" поддерживает союзника ")).append(coloured(attacker))
                        .append(Component.literal(" и вступает в войну.")));
            }
            boolean involved = false;
            for (UUID id : war.attackers) { CountryData.Country c = data.byId(id); if (c != null && c.isMember(nick)) involved = true; }
            for (UUID id : war.defenders) { CountryData.Country c = data.byId(id); if (c != null && c.isMember(nick)) involved = true; }
            if (involved) {
                String when = war.scheduledStartMs > 0L ? "Боевые действия начнутся " + scheduledMoscowText(war.scheduledStartMs) + " МСК." : "Боевые действия начнутся через " + formatTime(prep) + ".";
                sp.sendSystemMessage(Component.literal(when).withStyle(ChatFormatting.GRAY));
            }
            if (victim.isMember(nick)) {
                PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                        new NoticePacket("Война!", attacker.name + " объявила вам войну!", WAR_RED));
            } else {
                for (CountryData.Country j : joinedA) {
                    if (j.isMember(nick)) PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                            new NoticePacket("Война!", "Ваша страна вступила в войну на стороне " + attacker.name + ".", WAR_RED));
                }
                for (CountryData.Country j : joinedB) {
                    if (j.isMember(nick)) PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                            new NoticePacket("Война!", attacker.name + " объявила войну вашему союзнику " + victim.name
                                    + ". Ваша страна вступила в войну.", WAR_RED));
                }
            }
        }
    }

    public static String formatTime(int seconds) {
        if (seconds >= 120 && seconds % 60 == 0) return (seconds / 60) + " мин.";
        if (seconds >= 60) return (seconds / 60) + " мин. " + (seconds % 60) + " сек.";
        return seconds + " сек.";
    }

    public static MutableComponent coloured(CountryData.Country c) {
        return Component.literal(c.name).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(c.color)));
    }
}
