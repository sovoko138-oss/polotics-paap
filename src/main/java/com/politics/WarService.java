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
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** /war (страна): объявление войны. Сам захват пока не сделан. */
public class WarService {
    private static final int WAR_RED = 0xFF5555;

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

        // Свиньи сами объявляют войну тому, кто их вызвал; людям войну объявляет президент.
        CountryData.Country attacker = target.npc ? target : mine;
        CountryData.Country victim = target.npc ? mine : target;
        if (data.atWar(attacker, victim)) {
            src.sendFailure(Component.literal("Война с этой страной уже идёт."));
            return 0;
        }
        data.declareWar(attacker, victim);

        Component msg = coloured(attacker)
                .append(Component.literal(" напала на "))
                .append(coloured(victim))
                .append(Component.literal("! Объявлена война."));
        src.getServer().getPlayerList().broadcastSystemMessage(msg, false);

        // экран в правом краю: президенту и всем жителям страны, на которую напали
        NoticePacket notice = new NoticePacket("Война!", attacker.name + " объявила вам войну!", WAR_RED);
        for (ServerPlayer sp : src.getServer().getPlayerList().getPlayers()) {
            if (victim.isMember(sp.getGameProfile().getName())) {
                PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), notice);
            }
        }
        return 1;
    }

    private static net.minecraft.network.chat.MutableComponent coloured(CountryData.Country c) {
        return Component.literal(c.name).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(c.color)));
    }
}
