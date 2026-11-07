package io.github.sexileman.eae2pattern.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** AE2 15.x native palette and panel texture, using the installed AE2/resource-pack assets. */
public final class WorkbenchTheme {
    private static final ResourceLocation PANEL=new ResourceLocation("ae2","textures/guis/background.png");
    public static final int BACKGROUND=0xFFAAAAAA, SURFACE=0xFFB0B0B0, HOVER=0xFFC4BCD0;
    public static final int LINE=0xFF747474, TEXT=0xFF303030, DIM=0xFF606060, ACCENT=0xFF563A7C;
    public static final int WARNING=0xFFB34D00, PLACEHOLDER=0xFFAEAEAE, INPUT=0xFF252525;
    public static final int INPUT_TEXT=0xFFFFFFFF, PRIMARY=0xFFB5A6C8, PRIMARY_HOVER=0xFFC7B6DD;
    private WorkbenchTheme(){}
    public static void panel(GuiGraphics g,int x,int y,int w,int h){g.blitNineSliced(PANEL,x,y,w,h,3,256,256,0,0);if(w>6&&h>6)g.fill(x+3,y+3,x+w-3,y+h-3,BACKGROUND);}
    public static void rounded(GuiGraphics g,int x,int y,int w,int h,int color){g.fill(x,y,x+w,y+h,color);}
    public static void control(GuiGraphics g,int x,int y,int w,int h,int fill,int border){
        if(w<2||h<2)return;
        g.fill(x,y,x+w,y+h,border);g.fill(x+1,y+1,x+w-1,y+h-1,fill);
        int light=fill==INPUT?0xFF373737:0xFFF0F0F0,dark=fill==INPUT?0xFFBBBBBB:0xFF555555;
        g.fill(x+1,y+1,x+w-1,y+2,light);g.fill(x+1,y+1,x+2,y+h-1,light);
        g.fill(x+1,y+h-2,x+w-1,y+h-1,dark);g.fill(x+w-2,y+1,x+w-1,y+h-1,dark);
    }
    public static void slot(GuiGraphics g,int x,int y,IngredientPresentation presentation){
        g.fill(x,y,x+18,y+18,0xFF373737);g.fill(x+1,y+1,x+18,y+18,0xFFF0F0F0);g.fill(x+1,y+1,x+17,y+17,0xFF8B8B8B);
        if(presentation.kind()!=IngredientPresentation.Kind.REGULAR)g.fill(x+1,y+16,x+17,y+17,presentation.color());
    }
}
