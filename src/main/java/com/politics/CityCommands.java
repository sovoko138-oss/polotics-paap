package com.politics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.PacketDistributor;

public class CityCommands {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("capital")
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                .then(Commands.literal("change").executes(ctx -> change(ctx.getSource()))));
        d.register(Commands.literal("countries").executes(ctx -> countries(ctx.getSource())));
    }

    private static int info(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        CountryData data = CountryData.get(src.getServer());
        String dim = p.level().dimension().location().toString();
        ChunkPos cp = p.chunkPosition();
        CountryData.Country c = data.cityAt(dim, cp.x, cp.z);
        if (c == null) {
            CountryData.Country owner = data.byChunk(dim, cp.x, cp.z);
            src.sendFailure(Component.literal(owner == null
                    ? "Вы сейчас не в городе."
                    : "Вы на территории страны «" + owner.name + "», но не в городе."));
            return 0;
        }
        Component countryName = Component.literal(c.name)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(c.color)));
        src.sendSuccess(() -> Component.literal("Город: " + c.capitalName + " (столица)"), false);
        src.sendSuccess(() -> Component.literal("Страна: ").append(countryName), false);
        src.sendSuccess(() -> Component.literal("Мэр: " + c.mayor), false);
        src.sendSuccess(() -> Component.literal("Флаг: ").append(CountryService.flagComponent(c.cityFlag)), false);
        return 1;
    }

    private static int change(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        CountryData data = CountryData.get(src.getServer());
        String dim = p.level().dimension().location().toString();
        ChunkPos cp = p.chunkPosition();
        CountryData.Country c = data.cityAt(dim, cp.x, cp.z);
        if (c == null) {
            src.sendFailure(Component.literal("Вы сейчас не в городе."));
            return 0;
        }
        if (!CountryService.canEditCity(p, c)) {
            src.sendFailure(Component.literal("Изменять город может только президент или мэр."));
            return 0;
        }
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new OpenChangeCityPacket(c.id, c.capitalName, c.mayor, c.cityFlag));
        return 1;
    }

    private static int countries(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new CountriesPacket(CountryService.buildInfos(CountryData.get(src.getServer()))));
        return 1;
    }
}
