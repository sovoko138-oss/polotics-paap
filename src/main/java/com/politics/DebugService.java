package com.politics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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

/** /debugp: things for operators to test with. */
public class DebugService {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("debugp").requires(s -> s.hasPermission(2))
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("ally")
                        .then(country("a").then(country("b").executes(DebugService::ally))))
                .then(Commands.literal("unally")
                        .then(country("a").executes(ctx -> unally(ctx, false))
                                .then(country("b").executes(ctx -> unally(ctx, true)))))
                .then(Commands.literal("war")
                        .then(country("a").then(country("b").executes(DebugService::war))))
                .then(Commands.literal("endwar")
                        .then(country("a").executes(DebugService::endWar))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> country(String name) {
        return Commands.argument(name, StringArgumentType.string()).suggests((ctx, b) -> {
            List<String> names = new ArrayList<>();
            for (CountryData.Country c : CountryData.get(ctx.getSource().getServer()).all()) {
                names.add(c.name.contains(" ") ? "\"" + c.name + "\"" : c.name);
            }
            return SharedSuggestionProvider.suggest(names, b);
        });
    }

    private static int help(CommandSourceStack src) {
        for (String line : new String[]{
                "/debugp ally <страна1> <страна2> - свести две страны в альянс",
                "/debugp unally <страна> [страна2] - вывести страну из всех альянсов (или только из общих со страной2)",
                "/debugp war <нападающий> <защищающийся> - объявить войну",
                "/debugp endwar <страна> - отменить войну, в которой участвует страна"}) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static CountryData.Country find(CommandContext<CommandSourceStack> ctx, String arg) {
        String name = StringArgumentType.getString(ctx, arg);
        CountryData.Country c = CountryData.get(ctx.getSource().getServer()).byName(name);
        if (c == null) ctx.getSource().sendFailure(Component.literal("Страна не найдена: " + name));
        return c;
    }

    private static int ally(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CountryData data = CountryData.get(ctx.getSource().getServer());
        CountryData.Country a = find(ctx, "a"), b = find(ctx, "b");
        if (a == null || b == null) return 0;
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

    private static int unally(CommandContext<CommandSourceStack> ctx, boolean pair) {
        CountryData data = CountryData.get(ctx.getSource().getServer());
        CountryData.Country a = find(ctx, "a");
        CountryData.Country b = pair ? find(ctx, "b") : null;
        if (a == null || (pair && b == null)) return 0;
        int n = 0;
        for (CountryData.Alliance al : new ArrayList<>(data.alliances())) {
            if (!al.members.contains(a.id)) continue;
            if (pair && !al.members.contains(b.id)) continue;
            data.leaveAlliance(al, a);
            n++;
        }
        int count = n;
        ctx.getSource().sendSuccess(() -> Component.literal("Страна " + a.name + " вышла из альянсов: " + count), false);
        return n;
    }

    private static int war(CommandContext<CommandSourceStack> ctx) {
        CountryData data = CountryData.get(ctx.getSource().getServer());
        CountryData.Country a = find(ctx, "a"), b = find(ctx, "b");
        if (a == null || b == null) return 0;
        if (a == b) { ctx.getSource().sendFailure(Component.literal("Нужны две разные страны.")); return 0; }
        if (data.allied(a, b)) { ctx.getSource().sendFailure(Component.literal("Эти страны в одном альянсе.")); return 0; }
        if (data.warOf(a) != null || data.warOf(b) != null) { ctx.getSource().sendFailure(Component.literal("Одна из стран уже воюет.")); return 0; }
        WarService.declare(ctx.getSource().getServer(), data, a, b);
        return 1;
    }

    private static int endWar(CommandContext<CommandSourceStack> ctx) {
        CountryData data = CountryData.get(ctx.getSource().getServer());
        CountryData.Country a = find(ctx, "a");
        if (a == null) return 0;
        CountryData.War w = data.warOf(a);
        if (w == null) { ctx.getSource().sendFailure(Component.literal("Эта страна не воюет.")); return 0; }
        data.wars().remove(w);
        data.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("Война отменена."), false);
        return 1;
    }
}
