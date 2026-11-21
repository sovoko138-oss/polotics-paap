package com.politics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /debugp: things for operators to test with. */
public class DebugService {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("debugp").requires(s -> s.hasPermission(2))
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("ally")
                        .then(countries(L_ALLY, true).executes(DebugService::ally)))
                .then(Commands.literal("unally")
                        .then(countries(L_UNALLY, true).executes(DebugService::unally)))
                .then(Commands.literal("war")
                        .then(countries(L_WAR, true).executes(DebugService::war)))
                .then(Commands.literal("endwar")
                        .then(countries(L_END, false).executes(DebugService::endWar)))
                .then(Commands.literal("countrylimit")
                        .then(Commands.argument("limit", IntegerArgumentType.integer(0))
                                .executes(DebugService::setCountryLimit)))
                .then(Commands.literal("nether")
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"on", "off"}, b))
                                .executes(ctx -> setDimension(ctx, "nether"))))
                .then(Commands.literal("end")
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"on", "off"}, b))
                                .executes(ctx -> setDimension(ctx, "end"))))
                .then(Commands.literal("wars")
                        .executes(ctx -> openWars(ctx.getSource()))));
    }

    /**
     * One argument that takes the rest of the line (so Cyrillic names work). The hints go name by name:
     * first the countries, and after a complete first name the countries for the second place.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> countries(String label, boolean two) {
        return Commands.argument(label, StringArgumentType.greedyString()).suggests((ctx, b) -> {
            List<String> names = new ArrayList<>();
            for (CountryData.Country c : CountryData.get(ctx.getSource().getServer()).all()) names.add(c.name);
            String rem = b.getRemaining();
            String lower = rem.toLowerCase(Locale.ROOT);
            if (two) {
                for (String first : names) {
                    if (lower.startsWith(first.toLowerCase(Locale.ROOT) + " ")) {
                        String prefix = rem.substring(0, first.length() + 1);
                        List<String> out = new ArrayList<>();
                        for (String second : names) if (!second.equals(first)) out.add(prefix + second);
                        return SharedSuggestionProvider.suggest(out, b);
                    }
                }
            }
            return SharedSuggestionProvider.suggest(names, b);
        });
    }

    private static final String L_ALLY = "страна1 страна2", L_UNALLY = "страна [страна2]", L_WAR = "кто кого", L_END = "страна";

    private static String raw(CommandContext<CommandSourceStack> ctx, String label) {
        return StringArgumentType.getString(ctx, label).trim().replace("\"", "");
    }

    /** Splits "A B" into two existing countries; names may contain spaces, so every split point is tried. */
    private static CountryData.Country[] pair(CommandContext<CommandSourceStack> ctx, CountryData data, String label) {
        String[] t = raw(ctx, label).split("\\s+");
        for (int i = 1; i < t.length; i++) {
            CountryData.Country x = data.byName(String.join(" ", java.util.Arrays.copyOfRange(t, 0, i)));
            CountryData.Country y = data.byName(String.join(" ", java.util.Arrays.copyOfRange(t, i, t.length)));
            if (x != null && y != null) return new CountryData.Country[]{x, y};
        }
        ctx.getSource().sendFailure(Component.literal("Нужны названия двух существующих стран, например: Свиностан Криперляндия"));
        return null;
    }

    private static CountryData.Country single(CommandContext<CommandSourceStack> ctx, CountryData data, String label) {
        CountryData.Country c = data.byName(raw(ctx, label));
        if (c == null) ctx.getSource().sendFailure(Component.literal("Страна не найдена: " + raw(ctx, label)));
        return c;
    }

    private static int help(CommandSourceStack src) {
        for (String line : new String[]{
                "/debugp ally Страна1 Страна2 - свести две страны в альянс (например: /debugp ally Криперляндия Свиностан)",
                "/debugp unally <страна> [страна2] - вывести страну из всех альянсов (или только из общих со страной2)",
                "/debugp war <нападающий> <защищающийся> - объявить войну",
                "/debugp endwar <страна> - отменить войну, в которой участвует страна",
                "/debugp countrylimit <число> - установить лимит стран (0 = без лимита)",
                "/debugp nether on|off - включить/запретить Нижний мир",
                "/debugp end on|off - включить/запретить Энд",
                "/debugp wars - список войн и изменение времени начала"}) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        DimensionRulesData dimensions = DimensionRulesData.get(src.getServer());
        src.sendSuccess(() -> Component.literal("Нижний мир: " + (dimensions.netherEnabled() ? "включён" : "запрещён")
                + " | Энд: " + (dimensions.endEnabled() ? "включён" : "запрещён")), false);
        return 1;
    }

    private static int openWars(CommandSourceStack src) {
        if (src.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            AdminWarsPacket.open(player);
            return 1;
        }
        src.sendFailure(Component.literal("Окно войн можно открыть только игроком в игре."));
        return 0;
    }

    private static int setDimension(CommandContext<CommandSourceStack> ctx, String dimension) {
        String state = StringArgumentType.getString(ctx, "state").toLowerCase(Locale.ROOT);
        if (!state.equals("on") && !state.equals("off")) {
            ctx.getSource().sendFailure(Component.literal("Используй on или off."));
            return 0;
        }
        boolean enabled = state.equals("on");
        DimensionRulesData data = DimensionRulesData.get(ctx.getSource().getServer());
        if (dimension.equals("nether")) data.setNetherEnabled(enabled);
        else data.setEndEnabled(enabled);
        String label = dimension.equals("nether") ? "Нижний мир" : "Энд";
        ctx.getSource().sendSuccess(() -> Component.literal(label + (enabled ? " разрешён." : " запрещён.")), true);
        return 1;
    }

    private static int ally(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CountryData data = CountryData.get(ctx.getSource().getServer());
        CountryData.Country[] pr = pair(ctx, data, L_ALLY);
        if (pr == null) return 0;
        CountryData.Country a = pr[0], b = pr[1];
        if (a == b) { ctx.getSource().sendFailure(Component.literal("Нужны две разные страны.")); return 0; }
        if (data.allied(a, b)) { ctx.getSource().sendFailure(Component.literal("Эти страны уже в одном альянсе.")); return 0; }

        CountryData.Alliance al = new CountryData.Alliance();
        String base = a.name + "-" + b.name;
        String name = base;
        for (int i = 2; data.allianceByName(name) != null; i++) name = base + " " + i;
        al.name = name;
        al.capital = a.capitalName == null ? "" : a.capitalName;
        al.flag = a.flagItem == null ? "minecraft:white_banner" : a.flagItem;
        int r = ((a.color >> 16 & 0xFF) + (b.color >> 16 & 0xFF)) / 2;
        int g = ((a.color >> 8 & 0xFF) + (b.color >> 8 & 0xFF)) / 2;
        int bl = ((a.color & 0xFF) + (b.color & 0xFF)) / 2;
        al.color = (Math.max(1, r) << 16) | (Math.max(1, g) << 8) | Math.max(1, bl);
        al.founderCountry = a.id;
        al.founderName = a.presidentName;
        al.created = System.currentTimeMillis();
        al.members.add(a.id);
        al.members.add(b.id);
        data.alliances().add(al);
        data.setDirty();

        String made = name;
        ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("Создан альянс «" + made + "»: " + a.name + " и " + b.name)
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(al.color))), false);
        return 1;
    }

    private static int unally(CommandContext<CommandSourceStack> ctx) {
        CountryData data = CountryData.get(ctx.getSource().getServer());
        // one country (all its alliances) or two (only the alliances they share)
        CountryData.Country one = data.byName(raw(ctx, L_UNALLY));
        CountryData.Country a, b = null;
        boolean pair = false;
        if (one != null) {
            a = one;
        } else {
            CountryData.Country[] pr = pair(ctx, data, L_UNALLY);
            if (pr == null) return 0;
            a = pr[0];
            b = pr[1];
            pair = true;
        }
        int n = 0;
        for (CountryData.Alliance al : new ArrayList<>(data.alliances())) {
            if (!al.members.contains(a.id)) continue;
            if (b != null && !al.members.contains(b.id)) continue;
            data.leaveAlliance(al, a);
            n++;
        }
        int count = n;
        ctx.getSource().sendSuccess(() -> Component.literal("Страна " + a.name + " вышла из альянсов: " + count), false);
        return n;
    }

    private static int war(CommandContext<CommandSourceStack> ctx) {
        CountryData data = CountryData.get(ctx.getSource().getServer());
        CountryData.Country[] pr = pair(ctx, data, L_WAR);
        if (pr == null) return 0;
        CountryData.Country a = pr[0], b = pr[1];
        if (a == b) { ctx.getSource().sendFailure(Component.literal("Нужны две разные страны.")); return 0; }
        if (data.allied(a, b)) { ctx.getSource().sendFailure(Component.literal("Эти страны в одном альянсе.")); return 0; }
        if (data.warOf(a) != null || data.warOf(b) != null) { ctx.getSource().sendFailure(Component.literal("Одна из стран уже воюет.")); return 0; }
        WarService.declare(ctx.getSource().getServer(), data, a, b);
        return 1;
    }

    private static int setCountryLimit(CommandContext<CommandSourceStack> ctx) {
        CountryData data = CountryData.get(ctx.getSource().getServer());
        int limit = IntegerArgumentType.getInteger(ctx, "limit");
        data.setCountryLimit(limit);
        String msg = limit == 0 ? "Лимит стран отключён (без ограничений)." : "Лимит стран установлен: " + limit + ". Сейчас создано: " + data.all().size() + ".";
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static int endWar(CommandContext<CommandSourceStack> ctx) {
        CountryData data = CountryData.get(ctx.getSource().getServer());
        CountryData.Country a = single(ctx, data, L_END);
        if (a == null) return 0;
        CountryData.War w = data.warOf(a);
        if (w == null) { ctx.getSource().sendFailure(Component.literal("Эта страна не воюет.")); return 0; }
        data.wars().remove(w);
        data.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("Война отменена."), false);
        return 1;
    }
}
