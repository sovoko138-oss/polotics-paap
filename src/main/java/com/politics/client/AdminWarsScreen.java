package com.politics.client;

import com.politics.AdminWarsPacket;
import com.politics.ChangeWarTimePacket;
import com.politics.PoliticsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Operator view: wars between countries/alliance members and clickable scheduled times. */
public class AdminWarsScreen extends Screen {
    private static final int W=360,H=230,ROWS=4,ROW_H=34;
    private final AdminWarsPacket packet;
    private int page=0,left,top;
    public AdminWarsScreen(AdminWarsPacket packet){super(Component.literal("Управление войнами"));this.packet=packet;}
    @Override protected void init(){
        left=(width-W)/2; top=Math.max(5,(height-H)/2);
        int bottom=top+H-25;
        addRenderableWidget(Button.builder(Component.literal("‹"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(left+10,bottom,28,20).build());
        addRenderableWidget(Button.builder(Component.literal("›"),b->{page=Math.min(pageCount()-1,page+1);rebuildWidgets();}).bounds(left+W-38,bottom,28,20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"),b->onClose()).bounds(left+W/2-40,bottom,80,20).build());
        for(int i=0;i<ROWS;i++){
            int idx=page*ROWS+i;if(idx>=packet.entries.size())break;
            AdminWarsPacket.Entry e=packet.entries.get(idx);
            int y=top+52+i*ROW_H;
            if(e.editable){
                String initial=e.time.replace(" МСК","");
                addRenderableWidget(Button.builder(Component.literal(initial),b->minecraft.setScreen(new WarTimeEditScreen(this,e,initial)))
                    .bounds(left+W-112,y+7,102,20).build());
            }
        }
    }
    private int pageCount(){return Math.max(1,(packet.entries.size()+ROWS-1)/ROWS);}
    private String fit(String value,int maxWidth){
        if(font.width(value)<=maxWidth)return value;
        String s=value;while(s.length()>3&&font.width(s+"…")>maxWidth)s=s.substring(0,s.length()-1);return s+"…";
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        renderBackground(g);Panel.draw(g,left,top,W,H);
        g.drawString(font,title,left+(W-font.width(title))/2,top+7,0x404040,false);
        int rowsTop=top+32;
        g.fill(left+8,rowsTop,left+W-8,top+H-28,0xFF202020);
        for(int i=0;i<ROWS;i++){
            int idx=page*ROWS+i;if(idx>=packet.entries.size())break;
            AdminWarsPacket.Entry e=packet.entries.get(idx);int y=top+52+i*ROW_H;
            g.fill(left+10,y-3,left+W-10,y+ROW_H-3,(i%2==0)?0xFF333333:0xFF424242);
            g.drawString(font,fit(e.attackers,230),left+16,y,0xFFFFFFFF,false);
            g.drawString(font,"vs",left+16,y+11,0xFFFFAA55,false);
            g.drawString(font,fit(e.defenders,215),left+34,y+11,0xFFFFFFFF,false);
            String status=e.status;
            g.drawString(font,status,left+16,y+22,0xFFAAAAAA,false);
            if(!e.editable){
                String t=e.time;g.drawString(font,t,left+W-12-font.width(t),y+10,0xFFBBBBBB,false);
            }
        }
        if(packet.entries.isEmpty())g.drawString(font,"Войн сейчас нет.",left+20,rowsTop+20,0xFFAAAAAA,false);
        String pg=(page+1)+" / "+pageCount();g.drawString(font,pg,left+(W-font.width(pg))/2,top+H-23,0x404040,false);
        super.render(g,mx,my,pt);
    }
}
