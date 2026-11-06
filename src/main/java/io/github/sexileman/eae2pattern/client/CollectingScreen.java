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
public final class CollectingScreen extends Screen {

    /** Roughly half a frame's worth of work; keeps the progress bar animating. */
    private static final long BUDGET_NANOS = 8_000_000L;
    private static final int MIN_PER_TICK = 4;
    private static final int MAX_PER_TICK = 128;

    private static final int COLOR_TEXT = 0xE0E0E0;
    private static final int COLOR_DIM = 0x909090;
    private static final int COLOR_BAR_BG = 0xFF202020;
    private static final int COLOR_BAR_FG = 0xFF3A6EA5;

    private final JeiRecipeCollector.Collector collector;
    private final Component machineName;
    @Nullable
    private final Screen returnTo;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    public CollectingScreen(IRecipeCategory<?> category, @Nullable Screen returnTo) {
        super(Component.translatable("eae2_pattern.collecting.title", category.getTitle()));
        this.collector = JeiRecipeCollector.begin(category);
        this.machineName = category.getTitle();
        this.returnTo = returnTo;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(300, this.width - 40);
        this.panelHeight = 72;
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("eae2_pattern.collecting.cancel"), b ->
                        this.onClose())
                .bounds(this.panelLeft + (this.panelWidth - 80) / 2, this.panelTop + this.panelHeight - 24, 80, 18)
                .build());
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
        PreviewGui.open(plan, this.returnTo);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int total = this.collector.total();
        int processed = this.collector.processed();

        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.panelTop + 8, COLOR_TEXT);
        graphics.drawCenteredString(this.font,
                Component.translatable("eae2_pattern.collecting.progress", processed, total),
                this.width / 2, this.panelTop + 24, COLOR_DIM);

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
}
