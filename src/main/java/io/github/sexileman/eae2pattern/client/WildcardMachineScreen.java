package io.github.sexileman.eae2pattern.client;

import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import java.util.List;
import java.util.Locale;

/** Machine selection for a new or legacy item whose saved category is not available. */
public final class WildcardMachineScreen extends AeToolScreen {
    private final List<IRecipeCategory<?>> categories;
    private final Screen parent;
    private final WildcardEditorScreen.HeldTarget heldTarget;
    private List<IRecipeCategory<?>> visible;
    private EditBox search;
    private int scroll, x, y, w, h;
    private boolean dragging;
    public WildcardMachineScreen(List<IRecipeCategory<?>> categories, Screen parent) {
        this(categories, parent, null);
    }
    public WildcardMachineScreen(List<IRecipeCategory<?>> categories, Screen parent, WildcardEditorScreen.HeldTarget heldTarget) {
        super(WildcardEditorScreen.tr("pick_machine")); this.categories = categories; this.parent = parent; visible = categories; this.heldTarget = heldTarget;
    }
    @Override protected void init() {
        var frame=ToolViewport.encoding(width,height);w=frame.width();h=frame.height();x=frame.left();y=frame.top();
        addRenderableWidget(new AeTextButton(x+w-54,y+6,46,18,net.minecraft.network.chat.Component.translatable("gui.back"),this::onClose));
        search = addRenderableWidget(new WorkbenchSearchBox(font, x + 10, y + 30, w - 20, title, WildcardEditorScreen.tr("machine_search")));
        search.setResponder(value -> {
            String q = value.toLowerCase(Locale.ROOT); visible = categories.stream().filter(c ->
                    OptionalPinyinSearch.contains(c.getTitle().getString() + " " + c.getRecipeType().getUid(), q)).toList(); scroll = 0;
        });
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
         WorkbenchTheme.panel(g,x,y,w,h);
        g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(title,w-74)), x + 10, y + 6 + (18 - font.lineHeight) / 2, WorkbenchTheme.TEXT, false);
        WorkbenchTheme.control(g,search.getX()-4,search.getY()-4,search.getWidth()+8,20,WorkbenchTheme.INPUT,WorkbenchTheme.LINE);
        g.enableScissor(x + 5, y + 55, x + w - 5, y + h - 5);
        for (int i = scroll; i < Math.min(visible.size(), scroll + (h - 60) / 25 + 1); i++) {
            int ry = y + 55 + (i - scroll) * 25;
            if (mx>=x+5&&mx<x+w-5&&my >= ry && my < ry + 24) g.fill(x + 5, ry, x + w - 5, ry + 24, WorkbenchTheme.HOVER);
            g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(visible.get(i).getTitle(), w - 24)), x + 10, ry + 3, WorkbenchTheme.TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(visible.get(i).getRecipeType().getUid().toString(), w - 24), x + 10, ry + 14, WorkbenchTheme.DIM, false);
        }
        g.disableScissor();
        int viewport=h-60,content=visible.size()*25;
        if(content>viewport){int thumb=Math.max(12,viewport*viewport/content),sy=y+55+(int)((viewport-thumb)*scroll/(double)Math.max(1,visible.size()-Math.max(1,viewport/25)));
            g.fill(x+w-9,y+55,x+w-4,y+h-5,WorkbenchTheme.LINE);WorkbenchTheme.control(g,x+w-9,sy,5,thumb,WorkbenchTheme.SURFACE,WorkbenchTheme.LINE);}
        super.render(g, mx, my, partial);
    }
    @Override public boolean mouseScrolled(double mx, double my, double delta) { if(!insidePanel(mx,my))return false; scroll = Math.max(0, Math.min(Math.max(0, visible.size() - Math.max(1, (h - 60) / 25)), scroll - (int) delta)); return true; }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (mx < x || mx >= x + w || my < y + 55 || my >= y + h || button != 0) return false;
        if(mx>=x+w-12){dragging=true;dragTo(my);return true;}
        int index = scroll + (int) (my - y - 55) / 25;
        if (index < visible.size()) minecraft.setScreen(new CollectingScreen(visible.get(index), this,
                plan -> minecraft.setScreen(new WildcardEditorScreen(plan, this, new CompoundTag(), null, true, heldTarget))));
        return true;
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public net.minecraft.client.renderer.Rect2i toolBounds(){return new net.minecraft.client.renderer.Rect2i(x,y,w,h);}

    private void dragTo(double my){int rows=Math.max(1,(h-60)/25),max=Math.max(0,visible.size()-rows);scroll=Math.max(0,Math.min(max,(int)((my-y-55)/Math.max(1,h-60)*visible.size())));}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(dragging){dragTo(my);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){dragging=false;return super.mouseReleased(mx,my,button);}

}
