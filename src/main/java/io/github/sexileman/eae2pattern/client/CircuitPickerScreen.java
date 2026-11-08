package io.github.sexileman.eae2pattern.client;
import io.github.sexileman.eae2pattern.wildcard.CircuitCondition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Compact multi-select grid, using the actual circuit stacks exposed by this recipe category. */
final class CircuitPickerScreen extends AeToolScreen {
    private final Screen parent;
    private final CircuitCatalog catalog;
    private final Consumer<CircuitCondition> apply;
    private final Set<Integer> selected = new TreeSet<>();
    private final StackVisuals visuals = new StackVisuals();
    private final List<Integer> numbers;
    private int mode, x,y,w,h,columns, rows;
    private double scroll;
    CircuitPickerScreen(Screen parent,CircuitCatalog catalog,CircuitCondition condition,Consumer<CircuitCondition> apply) {
        super(Component.translatable("eae2_pattern.circuit.title"));
        this.parent=parent;this.catalog=catalog;this.apply=apply;mode=condition.mode();selected.addAll(condition.configurations());
        numbers=List.copyOf(catalog.icons.keySet());
    }
    @Override protected void init() {
        var frame=ToolViewport.measure(width,height,300,230);
        columns=Math.max(1,(frame.width()-24)/26);
        int contentRows=Math.max(1,(numbers.size()+columns-1)/columns);
        frame=ToolViewport.measure(width,height,300,Math.min(230,91+contentRows*26));
        x=frame.left();y=frame.top();w=frame.width();h=frame.height();
        rows=Math.max(1,(h-91)/26);
        scroll=Math.min(scroll,Math.max(0,contentRows-rows));
        addRenderableWidget(new AeTextButton(x+10,y+31,(w-26)/2,18,Component.translatable("eae2_pattern.circuit.all"),()->{mode=0;selected.clear();}));
        addRenderableWidget(new AeTextButton(x+16+(w-26)/2,y+31,(w-26)/2,18,Component.translatable("eae2_pattern.circuit.none"),()->{mode=1;selected.clear();}));
        addRenderableWidget(new AeTextButton(x+w-62,y+5,52,18,Component.translatable("gui.done"),this::onClose));
    }
    private int index(double mx,double my) {
        if(mx<x+12||mx>=x+12+columns*26||my<y+58||my>=y+58+rows*26)return -1;
        int i=((int)(my-y-58)/26+(int)scroll)*columns+(int)(mx-x-12)/26;
        return i<numbers.size()?i:-1;
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        WorkbenchTheme.panel(g,x,y,w,h);
        g.drawString(font,title,x+10,y+9,WorkbenchTheme.TEXT,false);
        int first=(int)scroll*columns,last=Math.min(numbers.size(),first+rows*columns);
        for(int i=first;i<last;i++) {
            int n=numbers.get(i),px=x+12+(i%columns)*26,py=y+58+(i/columns-(int)scroll)*26;
            boolean available=catalog.icons.containsKey(n),checked=mode==2&&selected.contains(n);
            WorkbenchTheme.control(g,px,py,24,24,checked?WorkbenchTheme.HOVER:WorkbenchTheme.SURFACE,WorkbenchTheme.LINE);
            if(available)visuals.drawIcon(g,catalog.icons.get(n),px+4,py+2);
            String label=""+n;g.drawString(font,label,px+23-font.width(label),py+15,checked?WorkbenchTheme.PRIMARY:available?WorkbenchTheme.TEXT:WorkbenchTheme.DIM,false);
        }
        var status=CircuitCatalog.label(new CircuitCondition(mode,mode==2?selected:Set.of()));
        g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(status,w-20)),x+10,y+h-19,WorkbenchTheme.TEXT,false);
        super.render(g,mx,my,partial);
        int i=index(mx,my);if(i>=0){int n=numbers.get(i);Tooltips.show(g,font,width,List.of(Component.translatable("eae2_pattern.circuit.configuration",n),Component.translatable("eae2_pattern.circuit.matches",catalog.counts.getOrDefault(n,0))),mx,my);}
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        if(super.mouseClicked(mx,my,button))return true;
        int i=index(mx,my);if(button==0&&i>=0){int n=numbers.get(i);if(catalog.icons.containsKey(n)||selected.contains(n)){
            if(mode!=2)selected.clear();mode=2;if(selected.contains(n))selected.remove(n);else if(selected.size()<256)selected.add(n);if(selected.isEmpty())mode=0;
        }return true;}return false;
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta){if(!insidePanel(mx,my))return false;scroll=Math.max(0,Math.min(Math.max(0,(numbers.size()+columns-1)/columns-rows),scroll-delta));return true;}
    @Override public void onClose(){apply.accept(new CircuitCondition(mode,mode==2?selected:Set.of()));minecraft.setScreen(parent);}
    @Override public Rect2i toolBounds(){return new Rect2i(x,y,w,h);}
    @Override public Optional<IngredientArea> jeiIngredientAt(double mx,double my){int i=index(mx,my);if(i<0)return Optional.empty();var icon=catalog.icons.get(numbers.get(i));return icon==null?Optional.empty():Optional.of(new IngredientArea(icon,new Rect2i(x+12+(i%columns)*26,y+58+(i/columns-(int)scroll)*26,24,24)));}
    @Override public List<GhostField> jeiGhostFields(){return List.of(new GhostField(toolBounds(),s->{int n=CircuitCondition.configuration(s);if(n>=0&&catalog.icons.containsKey(n)){if(mode!=2)selected.clear();if(selected.size()<256)selected.add(n);mode=2;}}));}
}
