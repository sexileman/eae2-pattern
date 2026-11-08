package io.github.sexileman.eae2pattern.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
public final class AeTextButton extends AbstractButton {
    private final Runnable action;
    public AeTextButton(int x,int y,int width,int height,Component title,Runnable action){super(x,y,width,height,title);this.action=action;}
    public void onPress(){action.run();}
    protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        WorkbenchTheme.control(g,getX(),getY(),getWidth(),getHeight(),isHoveredOrFocused()&&active?WorkbenchTheme.HOVER:WorkbenchTheme.SURFACE,WorkbenchTheme.LINE);
        var font=Minecraft.getInstance().font;var text=font.substrByWidth(getMessage(),Math.max(1,getWidth()-8));
        g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(text),getX()+4,getY()+(getHeight()-font.lineHeight)/2,active?WorkbenchTheme.TEXT:WorkbenchTheme.DIM,false);
    }
    protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
}
