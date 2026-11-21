package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Admin GUI snapshot of all wars and server-side time changes. */
public class AdminWarsPacket {
    public static class Entry {
        public final UUID attacker, defender;
        public final String attackers, defenders, time, status;
        public final boolean editable;
        public Entry(UUID attacker, UUID defender, String attackers, String defenders, String time, String status, boolean editable) {
            this.attacker=attacker; this.defender=defender; this.attackers=attackers; this.defenders=defenders; this.time=time; this.status=status; this.editable=editable;
        }
    }
    public final List<Entry> entries;
    public AdminWarsPacket(List<Entry> entries) { this.entries=entries; }

    public static AdminWarsPacket make(MinecraftServer server) {
        CountryData data=CountryData.get(server);
        List<Entry> out=new ArrayList<>();
        for (CountryData.War w : data.wars()) {
            String a=side(data,w.attackers), b=side(data,w.defenders);
            boolean editable=!w.active && w.scheduledStartMs>0L;
            String time=w.active ? "Идёт сейчас" : (w.scheduledStartMs>0L ? WarService.scheduledMoscowText(w.scheduledStartMs)+" МСК" : "По таймеру подготовки");
            String status=w.active ? "Активная" : "Запланирована";
            out.add(new Entry(w.attacker,w.defender,a,b,time,status,editable));
        }
        return new AdminWarsPacket(out);
    }
    private static String side(CountryData data, List<UUID> ids) {
        List<String> names=new ArrayList<>();
        for (UUID id:ids) {
            CountryData.Country c=data.byId(id); if(c==null) continue;
            CountryData.Alliance alliance=data.allianceOf(c);
            names.add(alliance==null ? c.name : c.name+" («"+alliance.name+"»)");
        }
        return names.isEmpty()?"(нет стран)":String.join(", ",names);
    }
    public static void encode(AdminWarsPacket m,FriendlyByteBuf b) {
        b.writeVarInt(m.entries.size());
        for(Entry e:m.entries){ b.writeUUID(e.attacker); b.writeUUID(e.defender); b.writeUtf(e.attackers,500); b.writeUtf(e.defenders,500); b.writeUtf(e.time,64); b.writeUtf(e.status,32); b.writeBoolean(e.editable); }
    }
    public static AdminWarsPacket decode(FriendlyByteBuf b) {
        int n=b.readVarInt(); List<Entry> list=new ArrayList<>();
        for(int i=0;i<n;i++) list.add(new Entry(b.readUUID(),b.readUUID(),b.readUtf(500),b.readUtf(500),b.readUtf(64),b.readUtf(32),b.readBoolean()));
        return new AdminWarsPacket(list);
    }
    public static void handle(AdminWarsPacket m,Supplier<NetworkEvent.Context> c) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.politics.client.ClientHooks.openAdminWars(m));
    }
    public static void open(ServerPlayer player) {
        PoliticsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),make(player.getServer()));
    }
}
