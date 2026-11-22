package com.politics.client;

import com.politics.BorderSyncPacket;
import com.politics.ClientBorderState;
import com.politics.CountriesPacket;
import com.politics.OpenChangeCityPacket;
import com.politics.PlayerListPacket;
import com.politics.TrustPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

public class ClientHooks {
    public static void openGiveChunk(com.politics.GiveChunkService.Open m) {
        Minecraft.getInstance().setScreen(new GiveChunkScreen(m.cx, m.cz, m.countries));
    }

    public static void openHelp() { Minecraft.getInstance().setScreen(new HelpPolitScreen()); }

    public static void openAdminWars(com.politics.AdminWarsPacket m) {
        Minecraft.getInstance().setScreen(new AdminWarsScreen(m));
    }

    public static void openCreateScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new CreateCountryScreen(pos));
    }

    public static void openChangeCity(OpenChangeCityPacket m) {
        ResourceLocation rl = ResourceLocation.tryParse(m.flag);
        Item flag = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        Minecraft.getInstance().setScreen(new ChangeCityScreen(m.countryId, m.cityName, m.mayor, flag));
    }

    public static void openCountries(CountriesPacket m) {
        Minecraft mc = Minecraft.getInstance();
        Screen parent = mc.screen instanceof AllianceInfoScreen ? mc.screen : null;
        mc.setScreen(new CountriesScreen(m.countries, m.selected, parent));
    }

    public static void allianceList(com.politics.AlliancePackets.ListMsg m) {
        Minecraft.getInstance().setScreen(new AllianceListScreen(m));
    }

    public static void allianceInfo(com.politics.AlliancePackets.InfoMsg m) {
        Minecraft.getInstance().setScreen(new AllianceInfoScreen(m));
    }

    public static void allianceCreate(com.politics.AlliancePackets.CreateOpen m) {
        Minecraft.getInstance().setScreen(new AllianceCreateScreen(m.countries));
    }

    public static void allianceInvite(com.politics.AlliancePackets.InviteOpen m) {
        Minecraft.getInstance().setScreen(new AllianceInviteScreen(m.id, m.name, m.countries));
    }

    public static void allianceChat(com.politics.AlliancePackets.ChatMsg m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AllianceChatScreen s && s.allianceId().equals(m.id)) s.update(m);
        else if (m.open) mc.setScreen(new AllianceChatScreen(m));
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

    public static void openJoinList(com.politics.JoinListPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof JoinRequestsScreen s && s.countryId().equals(m.countryId)) {
            s.update(m);
        } else if (m.open) {
            mc.setScreen(new JoinRequestsScreen(m));
        }
    }

    public static void openMap(com.politics.OpenMapPacket m) {
        Minecraft.getInstance().setScreen(new WorldMapScreen(m));
    }

    public static void openMail(com.politics.OpenMailPacket m) {
        Minecraft.getInstance().setScreen(new MailScreen(m.countryId, m.countryName, m.presidentName, m.color));
    }

    public static void warStatus(com.politics.WarStatusPacket m) {
        WarPanel.update(m);
    }

    public static void setAllBorders(com.politics.AllBordersPacket m) {
        DebugBorders.entries = m.entries;
    }

    public static void notice(com.politics.NoticePacket m) {
        NoticeOverlay.add(m.title, m.body, m.color);
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
