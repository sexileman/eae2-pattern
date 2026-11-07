package io.github.sexileman.eae2pattern.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** AE2 15.x native palette and panel texture, using the installed AE2/resource-pack assets. */
public final class WorkbenchTheme {
    private static final ResourceLocation PANEL=new ResourceLocation("ae2","textures/guis/background.png");
    public static final int BACKGROUND=0xFFC6C6C6, SURFACE=0xFFB8B8B8, HOVER=0xFFDAD1E8;
    public static final int LINE=0xFF747474, TEXT=0xFF404040, DIM=0xFF7E7E7E, ACCENT=0xFF563A7C;
    public static final int WARNING=0xFFB34D00, PLACEHOLDER=0xFFAEAEAE, INPUT=0xFF252525;
    public static final int INPUT_TEXT=0xFFFFFFFF, PRIMARY=0xFFC5B6D8, PRIMARY_HOVER=0xFFD9C8EF;
    private WorkbenchTheme(){}
    public static void panel(GuiGraphics g,int x,int y,int w,int h){g.blitNineSliced(PANEL,x,y,w,h,3,256,256,0,0);}
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
