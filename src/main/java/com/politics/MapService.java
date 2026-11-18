package com.politics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** /map: opens the world map window. */
public class MapService {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("map").executes(ctx -> open(ctx.getSource())));
    }

    private static int open(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        List<OpenMapPacket.Entry> list = new ArrayList<>();
        for (CountryData.Country c : CountryData.get(src.getServer()).all()) {
            list.add(new OpenMapPacket.Entry(c.name, c.presidentName, c.dimension, c.color,
                    c.chunks.stream().mapToLong(Long::longValue).toArray()));
        }
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new OpenMapPacket(list));
        return 1;
    }
}
