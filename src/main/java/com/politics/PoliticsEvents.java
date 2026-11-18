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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PoliticsMod.MODID)
public class PoliticsEvents {

    /** Wooden hoe never tills the ground: it is the territory tool. */
    @SubscribeEvent
    public static void onTill(BlockEvent.BlockToolModificationEvent e) {
        if (e.getToolAction() == ToolActions.HOE_TILL && e.getHeldItemStack().is(Items.WOODEN_HOE)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) { handleClaim(e); }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) { handleClaim(e); }

    private static void handleClaim(PlayerInteractEvent e) {
        if (e.getHand() != InteractionHand.MAIN_HAND || !e.getItemStack().is(Items.WOODEN_HOE)) return;
        if (e.getLevel().isClientSide) {
            // The client knows whether this player is a president, so it does not predict anything
            if (ClientBorderState.active) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (CountryData.get(sp.getServer()).byPresident(sp.getUUID()) == null) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        CountryService.tryClaim(sp);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            CountryService.syncBorder(sp);
            MailService.deliverPending(sp);
            JoinService.loginHint(sp);
            BorderSync.sendAll(sp, CountryData.get(sp.getServer()));
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        CityCommands.register(e.getDispatcher());
        MailService.register(e.getDispatcher());
        WarService.register(e.getDispatcher());
        JoinService.register(e.getDispatcher());
        MapService.register(e.getDispatcher());
        e.getDispatcher().register(Commands.literal("country")
                .then(Commands.literal("playerlist").executes(ctx -> CityCommands.playerList(ctx.getSource())))
                .then(Commands.literal("trust")
                        .executes(ctx -> CityCommands.trustOpen(ctx.getSource()))
                        .then(Commands.argument("nick", StringArgumentType.word())
                                .executes(ctx -> CityCommands.trustNick(ctx.getSource(), StringArgumentType.getString(ctx, "nick")))))
                .then(Commands.literal("addresident").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("nick", StringArgumentType.word())
                                .then(Commands.argument("country", StringArgumentType.greedyString()).executes(ctx -> {
                                    CountryData data = CountryData.get(ctx.getSource().getServer());
                                    CountryData.Country c = data.byName(StringArgumentType.getString(ctx, "country"));
                                    if (c == null) {
                                        ctx.getSource().sendFailure(Component.literal("Страна не найдена."));
                                        return 0;
                                    }
                                    String err = ResidentService.addResident(data, c, StringArgumentType.getString(ctx, "nick"));
                                    if (err != null) {
                                        ctx.getSource().sendFailure(Component.literal(err));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("Житель добавлен."), true);
                                    return 1;
                                }))))
                .then(Commands.literal("list").requires(s -> s.hasPermission(2)).executes(ctx -> {
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
                .then(Commands.literal("delete").requires(s -> s.hasPermission(2))
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
