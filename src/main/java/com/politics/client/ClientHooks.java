package com.politics.client;

import com.politics.BorderSyncPacket;
import com.politics.ClientBorderState;
import com.politics.CountriesPacket;
import com.politics.OpenChangeCityPacket;
import com.politics.PlayerListPacket;
import com.politics.TrustPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

public class ClientHooks {
    public static void openCreateScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new CreateCountryScreen(pos));
    }

    public static void openChangeCity(OpenChangeCityPacket m) {
        ResourceLocation rl = ResourceLocation.tryParse(m.flag);
        Item flag = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        Minecraft.getInstance().setScreen(new ChangeCityScreen(m.countryId, m.cityName, m.mayor, flag));
    }

    public static void openCountries(CountriesPacket m) {
        Minecraft.getInstance().setScreen(new CountriesScreen(m.countries));
    }

    public static void openPlayerList(PlayerListPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PlayerListScreen s && s.countryId().equals(m.countryId)) {
            s.update(m);
        } else if (m.open) {
            mc.setScreen(new PlayerListScreen(m));
        }
    }

    public static void openTrustName(TrustPackets.OpenName m) {
        Minecraft.getInstance().setScreen(new TrustNameScreen(m.countryId, m.countryName, m.trusted));
    }

    public static void openTrustRights(TrustPackets.OpenRights m) {
        Minecraft.getInstance().setScreen(new TrustRightsScreen(m.countryId, m.nick, m.rights));
    }

    public static void onCreateResult(boolean ok, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ResultScreen s) s.onResult(ok, message);
    }

    public static void setBorder(BorderSyncPacket m) {
        Set<Long> set = new HashSet<>();
        for (long l : m.chunks) set.add(l);
        ClientBorderState.chunks = set;
        ClientBorderState.dimension = m.dimension;
        ClientBorderState.color = m.color;
        ClientBorderState.active = m.active;
    }
}
