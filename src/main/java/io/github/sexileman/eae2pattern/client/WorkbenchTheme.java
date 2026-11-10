package io.github.sexileman.eae2pattern.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** AE2 15.x native palette and panel texture, using the installed AE2/resource-pack assets. */
public final class WorkbenchTheme {
    private static final ResourceLocation PANEL=new ResourceLocation("ae2","textures/guis/background.png");
    public static final int BACKGROUND=0xFFC6C6C6, SURFACE=0xFFB8B8B8, HOVER=0xFFC7C1D1;
    public static final int LINE=0xFF626262, TEXT=0xFF303030, DIM=0xFF505050, ACCENT=0xFF563A7C;
    public static final int WARNING=0xFFB34D00, PLACEHOLDER=0xFFAEAEAE, INPUT=0xFF252525;
    public static final int INPUT_TEXT=0xFFFFFFFF, PRIMARY=0xFFB8AEC9, PRIMARY_HOVER=0xFFC7BDD8;
    private WorkbenchTheme(){}
    public static void nativePanel(GuiGraphics g,int x,int y,int w,int h){g.blitNineSliced(PANEL,x,y,w,h,3,256,256,0,0);}
    public static void panel(GuiGraphics g,int x,int y,int w,int h){nativePanel(g,x,y,w,h);}
    /** Shared small metadata text; primary labels and controls retain their native size. */
    public static void text(GuiGraphics g,net.minecraft.client.gui.Font font,
            net.minecraft.network.chat.Component text,int x,int y,int room,int color,float requestedScale){
        if(room<1)return;
        float scale=Math.max(.5f,Math.min(1f,requestedScale));
        g.pose().pushPose();
        try {
            g.pose().translate(x,y,0);
            g.pose().scale(scale,scale,1);
            var clipped=font.substrByWidth(text,Math.max(1,(int)(room/scale)));
            g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(clipped),0,0,color,false);
        } finally {g.pose().popPose();}
    }
    public static void rounded(GuiGraphics g,int x,int y,int w,int h,int color){g.fill(x,y,x+w,y+h,color);}
    /** One-pixel frame on all four sides; no adjacent dark inset that doubles an edge. */
    public static void control(GuiGraphics g,int x,int y,int w,int h,int fill,int border){
        if(w<2||h<2)return;
        g.fill(x,y,x+w,y+h,border);
        if(w>2&&h>2)g.fill(x+1,y+1,x+w-1,y+h-1,fill);
    }
    public static void slot(GuiGraphics g,int x,int y,IngredientPresentation presentation){
        g.fill(x,y,x+18,y+18,0xFF373737);g.fill(x+1,y+1,x+18,y+18,0xFFF0F0F0);g.fill(x+1,y+1,x+17,y+17,0xFF8B8B8B);
        if(presentation.kind()!=IngredientPresentation.Kind.REGULAR)g.fill(x+1,y+16,x+17,y+17,presentation.color());
    }
}
