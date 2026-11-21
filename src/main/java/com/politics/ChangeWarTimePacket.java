package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.function.Supplier;

/** Operator-only rescheduling of a future war (Moscow local time). */
public class ChangeWarTimePacket {
    public final UUID attacker, defender;
    public final String dateTime;
    public ChangeWarTimePacket(UUID attacker,UUID defender,String dateTime){this.attacker=attacker;this.defender=defender;this.dateTime=dateTime;}
    public static void encode(ChangeWarTimePacket m,FriendlyByteBuf b){b.writeUUID(m.attacker);b.writeUUID(m.defender);b.writeUtf(m.dateTime,32);}
    public static ChangeWarTimePacket decode(FriendlyByteBuf b){return new ChangeWarTimePacket(b.readUUID(),b.readUUID(),b.readUtf(32));}
    public static void handle(ChangeWarTimePacket m,Supplier<NetworkEvent.Context> c){
        c.get().enqueueWork(()->{
            ServerPlayer p=c.get().getSender(); if(p==null||!p.createCommandSourceStack().hasPermission(2))return;
            CountryData data=CountryData.get(p.getServer()); CountryData.War found=null;
            for(CountryData.War w:data.wars()) if(w.attacker.equals(m.attacker)&&w.defender.equals(m.defender)){found=w;break;}
            if(found==null){p.sendSystemMessage(Component.literal("Война уже не найдена."));return;}
            if(found.active||found.scheduledStartMs<=0L){p.sendSystemMessage(Component.literal("Можно переносить только запланированные войны, которые ещё не начались."));return;}
            try{
                DateTimeFormatter fmt=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
                LocalDateTime local=LocalDateTime.parse(m.dateTime,fmt);
                long epoch=local.atZone(ZoneId.of("Europe/Moscow")).toInstant().toEpochMilli();
                if(epoch<=System.currentTimeMillis()){p.sendSystemMessage(Component.literal("Новое время должно быть в будущем."));return;}
                for(CountryData.War other:data.wars()) if(other!=found && other.scheduledStartMs==epoch){p.sendSystemMessage(Component.literal("На это время уже назначена другая война."));return;}
                found.scheduledStartMs=epoch; data.setDirty();
                p.sendSystemMessage(Component.literal("Война перенесена на "+WarService.scheduledMoscowText(epoch)+" МСК."));
                AdminWarsPacket.open(p);
            }catch(DateTimeParseException ex){p.sendSystemMessage(Component.literal("Неверный формат. Используй ДД.ММ.ГГГГ ЧЧ:ММ, например 22.11.2026 17:40."));}
        });
        c.get().setPacketHandled(true);
    }
}
