package io.github.sexileman.eae2pattern.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Virtual option list with optional icon and complete identity on a second line. */
public final class WildcardChoiceScreen extends AeToolScreen {
    private final Screen parent;
    private final List<PickerOption> choices;
    private final Consumer<String> select;
    private final StackVisuals visuals=new StackVisuals();
    private final int rowHeight;
    private List<PickerOption> visible=List.of();
    private EditBox search;
    private int x,y,w,h,top;
    private double scroll;
    private boolean dragging;
    WildcardChoiceScreen(Screen parent,Component title,List<PickerOption> choices,Consumer<String> select) {
        super(title);this.parent=parent;this.choices=List.copyOf(choices);this.select=select;
        rowHeight=choices.stream().anyMatch(c->!c.detail().getString().isEmpty())?32:24;
    }
    @Override protected void init() {
        var frame=ToolViewport.measure(width,height,420,330);w=frame.width();h=frame.height();x=frame.left();y=frame.top();top=y+57;
        search=addRenderableWidget(new WorkbenchSearchBox(font,x+12,y+32,w-24,
                Component.translatable("eae2_pattern.editor.choice_search"),Component.translatable("eae2_pattern.editor.choice_search")));
        search.setBordered(false);
        search.setResponder(q->{visible=choices.stream().filter(c->c.matches(q)).toList();scroll=0;});visible=choices;
        addRenderableWidget(new AeTextButton(x+w-54,y+6,46,18,Component.translatable("gui.back"),this::onClose));
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        WorkbenchTheme.panel(g,x,y,w,h);
        g.drawString(font,title,x+12,y+11,WorkbenchTheme.TEXT,false);
        WorkbenchTheme.control(g,x+8,y+27,w-16,23,WorkbenchTheme.INPUT,WorkbenchTheme.LINE);
        int first=Math.max(0,(int)scroll/rowHeight),last=Math.min(visible.size(),first+viewport()/rowHeight+2);
        PickerOption hovered=null;
        g.enableScissor(x+8,top,x+w-8,y+h-8);
        for(int i=first;i<last;i++) {
            int row=top+i*rowHeight-(int)scroll;var option=visible.get(i);
            if(mx>=x+8&&mx<x+w-10&&my>=top&&my<y+h-8&&my>=row&&my<row+rowHeight) {
                g.fill(x+8,row,x+w-8,row+rowHeight-1,WorkbenchTheme.HOVER);hovered=option;
            }
            int textX=x+15;
            if(option.icon()!=null){visuals.drawIcon(g,option.icon(),x+14,row+(rowHeight-16)/2);textX=x+38;}
            boolean detail=!option.detail().getString().isEmpty();
            text(g,option.label(),textX,row+(detail?5:(rowHeight-font.lineHeight)/2),x+w-16-textX,WorkbenchTheme.TEXT);
            if(detail)text(g,option.detail(),textX,row+18,x+w-16-textX,WorkbenchTheme.DIM);
        }
        g.disableScissor();
        if(visible.isEmpty())g.drawString(font,Component.translatable("eae2_pattern.editor.choice_empty"),x+14,top+8,WorkbenchTheme.DIM,false);
        int content=visible.size()*rowHeight;
        if(content>viewport()) {
            int thumb=Math.max(12,viewport()*viewport()/content),sy=top+(int)((viewport()-thumb)*scroll/Math.max(1,content-viewport()));
            g.fill(x+w-7,top,x+w-4,y+h-8,WorkbenchTheme.LINE);g.fill(x+w-7,sy,x+w-4,sy+thumb,WorkbenchTheme.DIM);
        }
        super.render(g,mx,my,partial);
        if(hovered!=null&&!hovered.detail().getString().isEmpty()) {
            var tooltip=new ArrayList<Component>();tooltip.add(hovered.label());tooltip.add(hovered.detail());
            if(hovered.icon()!=null)tooltip.add(WildcardEditorScreen.tr(hovered.icon().isItem()?"example_item_id":"example_fluid_id",hovered.icon().id().toString()));
            Tooltips.show(g,font,width,tooltip,mx,my);
        }
    }
    private void text(GuiGraphics g,Component value,int px,int py,int room,int color) {
        g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(value,Math.max(1,room))),px,py,color,false);
    }
    private int viewport(){return h-65;}
    @Override public boolean mouseClicked(double mx,double my,int button) {
        if(super.mouseClicked(mx,my,button))return true;
        if(button==0&&my>=top&&my<y+h-8) {
            if(mx>=x+w-10&&mx<x+w){dragging=true;drag(my);return true;}
            if(mx>=x+8&&mx<x+w-10) {
                int i=(int)((my-top+scroll)/rowHeight);
                if(i>=0&&i<visible.size()){select.accept(visible.get(i).value());minecraft.setScreen(parent);return true;}
            }
        }
        return false;
    }
    private void drag(double my){scroll=Math.max(0,Math.min(Math.max(0,visible.size()*rowHeight-viewport()),(my-top)/Math.max(1,viewport())*visible.size()*rowHeight));}
    @Override public boolean mouseDragged(double mx,double my,int b,double dx,double dy){if(dragging){drag(my);return true;}return super.mouseDragged(mx,my,b,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int b){dragging=false;return super.mouseReleased(mx,my,b);}
    @Override public boolean mouseScrolled(double mx,double my,double delta){if(!insidePanel(mx,my))return false;scroll=Math.max(0,Math.min(Math.max(0,visible.size()*rowHeight-viewport()),scroll-delta*rowHeight));return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public net.minecraft.client.renderer.Rect2i toolBounds(){return new net.minecraft.client.renderer.Rect2i(x,y,w,h);}

    @Override public java.util.Optional<IngredientArea> jeiIngredientAt(double mx,double my){
        if(!insidePanel(mx,my)||my<top||my>=y+h-8)return java.util.Optional.empty();
        int index=(int)((my-top+scroll)/rowHeight);if(index<0||index>=visible.size())return java.util.Optional.empty();
        var option=visible.get(index);int iconY=top+index*rowHeight-(int)scroll+(rowHeight-16)/2;
        if(option.icon()==null||mx<x+14||mx>=x+30||my<iconY||my>=iconY+16)return java.util.Optional.empty();
        return java.util.Optional.of(new IngredientArea(option.icon(),new net.minecraft.client.renderer.Rect2i(x+14,iconY,16,16)));
    }
    @Override public List<GhostField> jeiGhostFields(){return search==null?List.of():List.of(ghostField(search,s->search.setValue(s.id().toString())));}

}
