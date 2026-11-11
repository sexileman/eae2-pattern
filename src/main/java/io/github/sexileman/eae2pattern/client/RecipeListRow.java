package io.github.sexileman.eae2pattern.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Shared virtual list row for ordinary and wildcard encoding. */
public final class RecipeListRow {
    public static final int HEIGHT=34;
    public interface HitSink { void add(int x,int y,Ingredient ingredient,int side); }
    private RecipeListRow() {}
    public static void draw(GuiGraphics g,Font font,StackVisuals visuals,RecipePlan.Entry entry,int x,int y,int right,
                            boolean selected,boolean focused,boolean hovered,Component badge,HitSink hits) {
        if(focused||hovered)g.fill(x,y,right,y+HEIGHT-1,focused?WorkbenchTheme.HOVER:WorkbenchTheme.SURFACE);
        g.fill(x+4,y+3,x+13,y+12,WorkbenchTheme.LINE);
        g.fill(x+5,y+4,x+12,y+11,selected?WorkbenchTheme.PRIMARY:WorkbenchTheme.BACKGROUND);
        if(selected){g.fill(x+6,y+7,x+8,y+9,WorkbenchTheme.TEXT);g.fill(x+8,y+5,x+10,y+9,WorkbenchTheme.TEXT);}
        int badgeWidth=badge.getString().isEmpty()?0:font.width(badge)+5;
        g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(entry.label(),Math.max(1,right-x-23-badgeWidth))),x+19,y+3,selected?WorkbenchTheme.TEXT:WorkbenchTheme.DIM,false);
        if(badgeWidth>0)g.drawString(font,badge,right-badgeWidth,y+3,WorkbenchTheme.DIM,false);
        var columns=RecipeStripLayout.measure(right-x-23);
        var inputs=new ArrayList<>(entry.inputs());inputs.addAll(entry.catalysts());
        strip(g,font,visuals,inputs,x+19,y+15,columns.inputMax(),columns.inputWidth(),0,hits);
        g.drawString(font,"→",x+19+columns.arrowX(),y+20,WorkbenchTheme.DIM,false);
        strip(g,font,visuals,entry.outputs(),x+19+columns.outputX(),y+15,columns.outputMax(),columns.outputWidth(),1,hits);
    }
    private static void strip(GuiGraphics g,Font font,StackVisuals visuals,List<Ingredient> slots,int x,int y,int max,int room,int side,HitSink hits){
        for(int i=0;i<Math.min(max,slots.size());i++){
            var ingredient=slots.get(i);var state=IngredientPresentation.of(ingredient,side);
            WorkbenchTheme.slot(g,x-1,y-1,state);visuals.draw(g,ingredient.spec(),x,y);visuals.drawRule(g,state,x,y);
            hits.add(x,y,ingredient,side);x+=22;
        }
        if(slots.size()>max && RecipeStripLayout.overflowWidth(room,Math.min(max,slots.size()))>=12)g.drawString(font,font.plainSubstrByWidth("+"+(slots.size()-max),Math.min(27,RecipeStripLayout.overflowWidth(room,max))),x+3,y+5,WorkbenchTheme.DIM,false);
    }
}
