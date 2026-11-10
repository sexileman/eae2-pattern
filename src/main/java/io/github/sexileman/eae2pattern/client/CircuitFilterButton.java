package io.github.sexileman.eae2pattern.client;
import io.github.sexileman.eae2pattern.wildcard.CircuitCondition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Supplier;
/** Same compact icon control in both encoding workflows. */
final class CircuitFilterButton extends AbstractButton {
    private final CircuitCatalog catalog;
    private final Supplier<CircuitCondition> condition;
    private final Runnable action;
    private CircuitCondition last;
    private Component label;
    private io.github.sexileman.eae2pattern.pattern.StackSpec icon;
    private final StackVisuals visuals=new StackVisuals();
    CircuitFilterButton(int x,int y,int w,int h,CircuitCatalog catalog,Supplier<CircuitCondition> condition,Runnable action) {
        super(x,y,w,h,Component.translatable("eae2_pattern.circuit.title"));this.catalog=catalog;this.condition=condition;this.action=action;
    }
    @Override public void onPress(){action.run();}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        var selected=condition.get();
        if(selected!=last){last=selected;label=CircuitCatalog.label(selected);setMessage(label);
            icon=selected.configurations().stream().sorted().map(catalog.icons::get).filter(java.util.Objects::nonNull).findFirst().orElse(catalog.icons.get(catalog.icons.firstKey()));
        }
        WorkbenchTheme.control(g,getX(),getY(),getWidth(),getHeight(),isHoveredOrFocused()&&active?WorkbenchTheme.HOVER:WorkbenchTheme.SURFACE,WorkbenchTheme.LINE);
        visuals.drawIcon(g,icon,getX()+3,getY()+(getHeight()-16)/2);
        if(selected.mode()==0)drawAllCircuitMarker(g,getX()+3,getY()+(getHeight()-16)/2);

        var font=net.minecraft.client.Minecraft.getInstance().font;
        g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(label,Math.max(1,getWidth()-24))),getX()+22,getY()+(getHeight()-font.lineHeight)/2,active?WorkbenchTheme.TEXT:WorkbenchTheme.DIM,false);
        if(isHovered())Tooltips.show(g,font,net.minecraft.client.Minecraft.getInstance().screen.width,List.of(label,Component.translatable("eae2_pattern.circuit.hint")),mx,my);
    }
    /** Render above the item model; the mark denotes the unfiltered circuit state only. */
    private static void drawAllCircuitMarker(GuiGraphics g,int x,int y){
        g.pose().pushPose();
        try {
            g.pose().translate(x,y,220);
            int red=0xFFE01818;
            g.fill(3,1,13,3,red);g.fill(3,13,13,15,red);
            g.fill(1,3,3,13,red);g.fill(13,3,15,13,red);
            g.fill(2,2,4,4,red);g.fill(12,2,14,4,red);
            g.fill(2,12,4,14,red);g.fill(12,12,14,14,red);
            for(int i=3;i<=11;i++)g.fill(i,13-i,i+2,15-i,red);
        } finally {g.pose().popPose();}
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
}
