package io.github.sexileman.eae2pattern.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Searchable virtual option list, shared by forms, material families and ingredient-derived rules. */
final class WildcardChoiceScreen extends Screen {
    record Choice(String value,Component label){}
    private final Screen parent;private final List<Choice> choices;private final Consumer<String> select;
    private List<Choice> visible=List.of();private EditBox search;private int x,y,w,h,top;private double scroll;private boolean dragging;
    WildcardChoiceScreen(Screen parent,Component title,List<Choice> choices,Consumer<String> select){super(title);this.parent=parent;this.choices=List.copyOf(choices);this.select=select;}
    protected void init(){w=Math.min(420,width-24);h=Math.min(330,height-24);x=(width-w)/2;y=(height-h)/2;top=y+57;
        search=addRenderableWidget(new WorkbenchSearchBox(font,x+12,y+32,w-24,Component.translatable("eae2_pattern.editor.choice_search"),title));search.setBordered(false);
        search.setResponder(q->{String text=q.toLowerCase(Locale.ROOT);visible=choices.stream().filter(c->(c.label().getString()+" "+c.value()).toLowerCase(Locale.ROOT).contains(text)).toList();scroll=0;});visible=choices;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x+w-54,y+6,46,18).build());
    }
    public void render(GuiGraphics g,int mx,int my,float p){g.fill(0,0,width,height,0xDD101010);g.fill(x,y,x+w,y+h,WorkbenchTheme.BACKGROUND);g.drawString(font,title,x+12,y+11,WorkbenchTheme.TEXT,false);
        WorkbenchTheme.control(g,x+8,y+27,w-16,23,WorkbenchTheme.INPUT,WorkbenchTheme.LINE);
        g.enableScissor(x+8,top,x+w-8,y+h-8);int first=Math.max(0,(int)scroll/24),last=Math.min(visible.size(),first+(h-57)/24+2);
        for(int i=first;i<last;i++){int row=top+i*24-(int)scroll;var c=visible.get(i);if(mx>=x+8&&mx<x+w-8&&my>=row&&my<row+24)g.fill(x+8,row,x+w-8,row+23,WorkbenchTheme.HOVER);
            g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(c.label(),w-35)),x+15,row+7,WorkbenchTheme.TEXT,false);}
        g.disableScissor();if(visible.isEmpty())g.drawString(font,Component.translatable("eae2_pattern.editor.choice_empty"),x+14,top+8,WorkbenchTheme.DIM,false);
        if(visible.size()*24>h-65){int viewport=h-65,thumb=Math.max(12,viewport*viewport/(visible.size()*24));int sy=top+(int)((viewport-thumb)*scroll/Math.max(1,visible.size()*24-viewport));g.fill(x+w-7,top,x+w-4,y+h-8,WorkbenchTheme.LINE);g.fill(x+w-7,sy,x+w-4,sy+thumb,WorkbenchTheme.DIM);}
        super.render(g,mx,my,p);
    }
    public boolean mouseClicked(double mx,double my,int b){if(super.mouseClicked(mx,my,b))return true;if(b==0&&my>=top&&my<y+h-8){if(mx>=x+w-10&&mx<x+w){dragging=true;drag(my);return true;}if(mx>=x+8&&mx<x+w-10){int i=(int)((my-top+scroll)/24);if(i>=0&&i<visible.size()){select.accept(visible.get(i).value());minecraft.setScreen(parent);return true;}}}return false;}
    private void drag(double my){scroll=Math.max(0,Math.min(Math.max(0,visible.size()*24-(h-65)),(my-top)/Math.max(1,h-65)*visible.size()*24));}
    public boolean mouseDragged(double mx,double my,int b,double dx,double dy){if(dragging){drag(my);return true;}return super.mouseDragged(mx,my,b,dx,dy);}
    public boolean mouseReleased(double mx,double my,int b){dragging=false;return super.mouseReleased(mx,my,b);}
    public boolean mouseScrolled(double mx,double my,double delta){scroll=Math.max(0,Math.min(Math.max(0,visible.size()*24-(h-65)),scroll-delta*24));return true;}
    public void onClose(){minecraft.setScreen(parent);}public boolean isPauseScreen(){return false;}
}
