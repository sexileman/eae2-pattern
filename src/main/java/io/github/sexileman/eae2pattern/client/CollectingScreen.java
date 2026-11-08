package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.compat.jei.JeiRecipeCollector;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Reads a whole JEI category a few recipes per tick instead of in one blocking call.
 *
 * <p>A crafting table category in a large pack can hold thousands of recipes, and building a JEI layout for each one
 * costs real time. Doing that inside a click handler freezes the window long enough for the OS to offer to kill the
 * game, so the work is spread over ticks behind this progress screen.</p>
 */
public final class CollectingScreen extends AeToolScreen {

    /** Roughly half a frame's worth of work; keeps the progress bar animating. */
    private static final long BUDGET_NANOS = 8_000_000L;
    private static final int MIN_PER_TICK = 4;
    private static final int MAX_PER_TICK = 128;

    private static final int COLOR_TEXT = WorkbenchTheme.TEXT;
    private static final int COLOR_DIM = WorkbenchTheme.DIM;
    private static final int COLOR_BAR_BG = 0xFF8B8B8B;
    private static final int COLOR_BAR_FG = WorkbenchTheme.ACCENT;

    private final JeiRecipeCollector.Collector collector;
    private final Component machineName;
    @Nullable
    private final Screen returnTo;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private java.util.function.Consumer<RecipePlan> destination;

    public CollectingScreen(IRecipeCategory<?> category, Screen returnTo, java.util.function.Consumer<RecipePlan> destination) {
        this(category, returnTo); this.destination = destination;
    }

    public CollectingScreen(IRecipeCategory<?> category, @Nullable Screen returnTo) {
        super(Component.translatable("eae2_pattern.collecting.title", category.getTitle()));
        this.collector = JeiRecipeCollector.begin(category);
        this.machineName = category.getTitle();
        this.returnTo = returnTo;
    }

    @Override
    protected void init() {
        var frame=ToolViewport.measure(width,height,300,84);panelWidth=frame.width();panelHeight=frame.height();panelLeft=frame.left();panelTop=frame.top();

        this.addRenderableWidget(new AeTextButton(panelLeft+(panelWidth-80)/2,panelTop+panelHeight-24,80,18,Component.translatable("eae2_pattern.collecting.cancel"),this::onClose));
    }

    @Override
    public void tick() {
        long deadline = System.nanoTime() + BUDGET_NANOS;
        int done = 0;
        while (!this.collector.finished() && done < MAX_PER_TICK) {
            this.collector.step();
            done++;
            if (done >= MIN_PER_TICK && System.nanoTime() >= deadline) {
                break;
            }
        }
        if (this.collector.finished()) {
            finish();
        }
    }

    private void finish() {
        RecipePlan plan = this.collector.finish();
        if (this.minecraft == null) {
            return;
        }
        if (plan.isEmpty()) {
            if (this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        Component.translatable("eae2_pattern.msg.no_patterns", this.machineName), false);
            }
            this.minecraft.setScreen(this.returnTo);
            return;
        }
        if(minecraft.player!=null&&(collector.skippedCount()>0||collector.truncated()))
            minecraft.player.displayClientMessage(Component.translatable("eae2_pattern.collecting.result",plan.entries().size(),collector.skippedCount(),collector.truncated()?Component.translatable("eae2_pattern.collecting.limited"):Component.empty()),false);
        if (destination != null) destination.accept(plan);
        else PreviewGui.open(plan, this.returnTo);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WorkbenchTheme.panel(graphics,panelLeft,panelTop,panelWidth,panelHeight);
        super.render(graphics, mouseX, mouseY, partialTick);

        int total = this.collector.total();
        int processed = this.collector.processed();

        var header=net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(title,panelWidth-16));
        graphics.drawString(font,header,panelLeft+(panelWidth-font.width(header))/2,panelTop+8,COLOR_TEXT,false);
        var progress=Component.translatable("eae2_pattern.collecting.progress",processed,total);
        graphics.drawString(font,progress,panelLeft+(panelWidth-font.width(progress))/2,panelTop+24,COLOR_DIM,false);

        int barLeft = this.panelLeft + 12;
        int barWidth = this.panelWidth - 24;
        int barTop = this.panelTop + 40;
        graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + 6, COLOR_BAR_BG);
        int filled = total <= 0 ? barWidth : (int) ((long) barWidth * processed / total);
        if (filled > 0) {
            graphics.fill(barLeft, barTop, barLeft + Math.min(filled, barWidth), barTop + 6, COLOR_BAR_FG);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.returnTo);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
    @Override public net.minecraft.client.renderer.Rect2i toolBounds(){return new net.minecraft.client.renderer.Rect2i(panelLeft,panelTop,panelWidth,panelHeight);}

}
