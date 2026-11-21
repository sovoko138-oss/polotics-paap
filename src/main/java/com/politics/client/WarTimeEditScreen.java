package com.politics.client;

import com.politics.AdminWarsPacket;
import com.politics.ChangeWarTimePacket;
import com.politics.PoliticsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Enter a replacement scheduled time in Moscow time. */
public class WarTimeEditScreen extends Screen {
    private static final int W=250;
    private final Screen parent; private final AdminWarsPacket.Entry war; private final String initial;
    private EditBox input; private int left,top;
    public WarTimeEditScreen(Screen parent,AdminWarsPacket.Entry war,String initial){super(Component.literal("Перенос войны"));this.parent=parent;this.war=war;this.initial=initial;}
    @Override protected void init(){
        left=(width-W)/2;top=Math.max(8,height/2-54);
        input=new EditBox(font,left+10,top+40,W-20,20,Component.literal("Дата и время"));input.setMaxLength(16);input.setValue(initial);input.setHint(Component.literal("ДД.ММ.ГГГГ ЧЧ:ММ"));addRenderableWidget(input);setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.literal("Сохранить"),b->{PoliticsNetwork.CHANNEL.sendToServer(new ChangeWarTimePacket(war.attacker,war.defender,input.getValue().trim()));minecraft.setScreen(parent);}).bounds(left+10,top+82,110,20).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"),b->minecraft.setScreen(parent)).bounds(left+130,top+82,110,20).build());
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){renderBackground(g);Panel.draw(g,left,top,W,116);Panel.drawWrappedCentered(g,font,title,width/2,top+5,W-20,0x404040,2);g.drawString(font,"Московское время (МСК)",left+10,top+27,0x404040,false);g.drawString(font,"Формат: ДД.ММ.ГГГГ ЧЧ:ММ",left+10,top+62,0x777777,false);super.render(g,mx,my,pt);}
}
