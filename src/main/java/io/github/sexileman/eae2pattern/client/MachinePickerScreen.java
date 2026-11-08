package io.github.sexileman.eae2pattern.client;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shown when the item under the cursor is a machine for more than one JEI category.
 *
 * <p>Common examples: a modded furnace that is both smelting and a custom "alloying" category, or an interface
 * block that appears as a catalyst for several of its own machines. Picking before building the plan matters,
 * because the plan for a broad category can be thousands of entries.</p>
 */
public final class MachinePickerScreen extends AeToolScreen {

    private static final int ROW_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 30;

    private static final int COLOR_TEXT = WorkbenchTheme.TEXT;
    private static final int COLOR_DIM = WorkbenchTheme.DIM;
    private static final int COLOR_ROW_ALT = 0x30FFFFFF;

    private final List<IRecipeCategory<?>> categories;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int listTop;
    private int listBottom;
    private int scroll;
    private boolean dragging;

    public MachinePickerScreen(ItemStack machine, List<IRecipeCategory<?>> categories) {
        this(Component.translatable("eae2_pattern.picker.title", machine.getHoverName()), categories);
    }

    /** Used when the choice is not about one item, for example JEI showing its category index instead of a recipe. */
    public MachinePickerScreen(Component title, List<IRecipeCategory<?>> categories) {
        super(title);
        this.categories = categories;
    }

    @Override
    protected void init() {
        var frame=ToolViewport.measure(width,height,360,260);panelWidth=frame.width();panelHeight=frame.height();panelLeft=frame.left();panelTop=frame.top();
        this.listTop = this.panelTop + HEADER_HEIGHT;
        this.listBottom = this.panelTop + this.panelHeight - FOOTER_HEIGHT;
        this.scroll = 0;

        int buttonY = this.panelTop + this.panelHeight - 24;
        this.addRenderableWidget(new AeTextButton(panelLeft+panelWidth-68,buttonY,60,18,Component.translatable("gui.cancel"),this::onClose));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {


        int left = this.panelLeft;
        int top = this.panelTop;
        WorkbenchTheme.panel(graphics,left,top,panelWidth,panelHeight);

        graphics.drawString(this.font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(title,panelWidth-16)), left + 8, top + 8, COLOR_TEXT, false);
        graphics.drawString(this.font,
                Component.translatable("eae2_pattern.picker.hint"),
                left + 8, top + 20, COLOR_DIM, false);

        this.renderList(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderScrollbar(graphics);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int visible = Math.max(1, (this.listBottom - this.listTop) / ROW_HEIGHT);
        int total = Math.max(0, this.categories.size() * ROW_HEIGHT - (this.listBottom - this.listTop));
        this.scroll = Mth.clamp(this.scroll, 0, total);

        graphics.enableScissor(this.panelLeft + 1, this.listTop, this.panelLeft + this.panelWidth - 1, this.listBottom);
        for (int i = 0; i < this.categories.size(); i++) {
            int rowY = this.listTop + i * ROW_HEIGHT - this.scroll;
            if (rowY + ROW_HEIGHT < this.listTop || rowY > this.listBottom) {
                continue;
            }
            boolean hovered = mouseX >= this.panelLeft + 4 && mouseX <= this.panelLeft + this.panelWidth - 12
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hovered) {
                graphics.fill(this.panelLeft + 4, rowY, this.panelLeft + this.panelWidth - 12, rowY + ROW_HEIGHT - 1, WorkbenchTheme.HOVER);
            } else if (i % 2 == 1) {
                graphics.fill(this.panelLeft + 4, rowY, this.panelLeft + this.panelWidth - 12, rowY + ROW_HEIGHT - 1, COLOR_ROW_ALT);
            }

            IRecipeCategory<?> category = this.categories.get(i);
            IDrawable icon = category.getIcon();
            if (icon != null) {
                icon.draw(graphics, this.panelLeft + 8, rowY + 6);
            }
            String label = this.font.plainSubstrByWidth(category.getTitle().getString(), this.panelWidth - 48);
            graphics.drawString(this.font, label, this.panelLeft + 26, rowY + 6, COLOR_TEXT, false);
        }
        graphics.disableScissor();
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int trackHeight = this.listBottom - this.listTop;
        int contentHeight = this.categories.size() * ROW_HEIGHT;
        if (contentHeight <= trackHeight) {
            return;
        }
        int barX = this.panelLeft + this.panelWidth - 8;
        int barHeight = Math.max(12, trackHeight * trackHeight / contentHeight);
        int maxScroll = contentHeight - trackHeight;
        int barY = this.listTop + (trackHeight - barHeight) * this.scroll / maxScroll;
        graphics.fill(barX, this.listTop, barX + 4, this.listBottom, 0x40000000);
        graphics.fill(barX, barY, barX + 4, barY + barHeight, WorkbenchTheme.LINE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= this.listTop && mouseY < this.listBottom) {
            if(mouseX>=panelLeft+panelWidth-12&&mouseX<panelLeft+panelWidth){dragging=true;dragTo(mouseY);return true;}
            int index = (int) ((mouseY - this.listTop + this.scroll) / ROW_HEIGHT);
            if (index >= 0 && index < this.categories.size() && mouseX >= this.panelLeft + 4
                    && mouseX <= this.panelLeft + this.panelWidth - 12) {
                IRecipeCategory<?> category = this.categories.get(index);
                EAE2PatternClient.openCategory(category);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if(!insidePanel(mouseX,mouseY))return false;
        int trackHeight = this.listBottom - this.listTop;
        int contentHeight = this.categories.size() * ROW_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - trackHeight);
        this.scroll = Mth.clamp(this.scroll - (int) (delta * ROW_HEIGHT), 0, maxScroll);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
    @Override public net.minecraft.client.renderer.Rect2i toolBounds(){return new net.minecraft.client.renderer.Rect2i(panelLeft,panelTop,panelWidth,panelHeight);}

    private void dragTo(double my){int viewport=listBottom-listTop,content=categories.size()*ROW_HEIGHT;scroll=Math.max(0,Math.min(Math.max(0,content-viewport),(int)((my-listTop)/Math.max(1,viewport)*content)));}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(dragging){dragTo(my);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){dragging=false;return super.mouseReleased(mx,my,button);}

}
