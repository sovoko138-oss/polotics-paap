package com.politics;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PoliticsMod.MODID)
public class PoliticsEvents {

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) CountryService.syncBorder(sp);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("country")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> {
                    CountryData data = CountryData.get(ctx.getSource().getServer());
                    if (data.all().isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("Стран пока нет."), false);
                        return 0;
                    }
                    for (CountryData.Country c : data.all()) {
                        Component line = Component.literal(c.name)
                                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(c.color)))
                                .append(Component.literal(" | столица: " + c.capitalName
                                        + " | президент: " + c.presidentName
                                        + " | замы: " + (c.deputies.isEmpty() ? "-" : String.join(", ", c.deputies))
                                        + " | флаг: " + c.flagItem
                                        + " | блок: " + c.capitalPos.toShortString()));
                        ctx.getSource().sendSuccess(() -> line, false);
                    }
                    return data.all().size();
                }))
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            CountryData data = CountryData.get(ctx.getSource().getServer());
                            CountryData.Country c = data.byName(name);
                            if (c == null) {
                                ctx.getSource().sendFailure(Component.literal("Страна не найдена: " + name));
                                return 0;
                            }
                            data.remove(c);
                            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(c.dimension));
                            ServerLevel lvl = ctx.getSource().getServer().getLevel(key);
                            if (lvl != null && lvl.isLoaded(c.capitalPos)
                                    && lvl.getBlockEntity(c.capitalPos) instanceof CapitalBlockEntity be) {
                                be.setUsed(false);
                            }
                            ServerPlayer president = ctx.getSource().getServer().getPlayerList().getPlayer(c.presidentId);
                            if (president != null) CountryService.syncBorder(president);
                            ctx.getSource().sendSuccess(() -> Component.literal("Страна удалена: " + c.name), true);
                            return 1;
                        }))));
    }
}
