package io.github.sexileman.eae2pattern.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;
public final class Tooltips {
    private Tooltips(){}
    public static int maxWidth(int screenWidth){return Math.max(80,Math.min(260,screenWidth-32));}
    public static void show(GuiGraphics g,Font font,int screenWidth,List<Component> text,int mx,int my){
        var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for(var part:text)lines.addAll(font.split(part,maxWidth(screenWidth)));
        g.renderTooltip(font,lines,mx,my);
    }
}
