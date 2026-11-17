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
        ServerPlayer p = src.getPlayerOrException();
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
        if (data.warOf(mine) != null) {
            src.sendFailure(Component.literal("Ваша страна уже воюет."));
            return 0;
        }
        if (data.warOf(target) != null) {
            src.sendFailure(Component.literal("Эта страна уже воюет."));
            return 0;
        }
        declare(src.getServer(), data, mine, target);
        return 1;
    }

    /** Declares the war, tells everybody in chat and shows the victim's people the message on screen. */
    public static void declare(MinecraftServer server, CountryData data, CountryData.Country attacker, CountryData.Country victim) {
        data.declareWar(attacker, victim, server.overworld().getGameTime());

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
            if (attacker.isMember(nick) || victim.isMember(nick)) {
                sp.sendSystemMessage(Component.literal("Боевые действия начнутся через " + formatTime(prep) + ".")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (victim.isMember(nick)) {
                PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                        new NoticePacket("Война!", attacker.name + " объявила вам войну!", WAR_RED));
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
