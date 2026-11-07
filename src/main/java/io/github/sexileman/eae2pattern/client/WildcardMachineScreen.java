package io.github.sexileman.eae2pattern.client;

import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import java.util.List;
import java.util.Locale;

/** Machine selection for a new or legacy item whose saved category is not available. */
public final class WildcardMachineScreen extends Screen {
    private final List<IRecipeCategory<?>> categories;
    private final Screen parent;
    private final WildcardEditorScreen.HeldTarget heldTarget;
    private List<IRecipeCategory<?>> visible;
    private EditBox search;
    private int scroll, x, y, w, h;
    public WildcardMachineScreen(List<IRecipeCategory<?>> categories, Screen parent) {
        this(categories, parent, null);
    }
    public WildcardMachineScreen(List<IRecipeCategory<?>> categories, Screen parent, WildcardEditorScreen.HeldTarget heldTarget) {
        super(WildcardEditorScreen.tr("pick_machine")); this.categories = categories; this.parent = parent; visible = categories; this.heldTarget = heldTarget;
    }
    @Override protected void init() {
        w = Math.min(480, width - 20); h = Math.min(360, height - 20); x = (width - w) / 2; y = (height - h) / 2;
        search = addRenderableWidget(new WorkbenchSearchBox(font, x + 10, y + 30, w - 20, title, WildcardEditorScreen.tr("machine_search")));
        search.setResponder(value -> {
            String q = value.toLowerCase(Locale.ROOT); visible = categories.stream().filter(c ->
                    (c.getTitle().getString() + " " + c.getRecipeType().getUid()).toLowerCase(Locale.ROOT).contains(q)).toList(); scroll = 0;
        });
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0xCC000000); g.fill(x, y, x + w, y + h, WorkbenchTheme.BACKGROUND);
        g.drawString(font, title, x + 10, y + 10, WorkbenchTheme.TEXT, false);
        g.enableScissor(x + 5, y + 55, x + w - 5, y + h - 5);
        for (int i = scroll; i < Math.min(visible.size(), scroll + (h - 60) / 25 + 1); i++) {
            int ry = y + 55 + (i - scroll) * 25;
            if (my >= ry && my < ry + 24) g.fill(x + 5, ry, x + w - 5, ry + 24, WorkbenchTheme.HOVER);
            g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(visible.get(i).getTitle(), w - 24)), x + 10, ry + 3, WorkbenchTheme.TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(visible.get(i).getRecipeType().getUid().toString(), w - 24), x + 10, ry + 14, WorkbenchTheme.DIM, false);
        }
        g.disableScissor(); super.render(g, mx, my, partial);
    }
    @Override public boolean mouseScrolled(double mx, double my, double delta) { scroll = Math.max(0, Math.min(Math.max(0, visible.size() - Math.max(1, (h - 60) / 25)), scroll - (int) delta)); return true; }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (mx < x || mx >= x + w || my < y + 55 || my >= y + h || button != 0) return false;
        int index = scroll + (int) (my - y - 55) / 25;
        if (index < visible.size()) minecraft.setScreen(new CollectingScreen(visible.get(index), this,
                plan -> minecraft.setScreen(new WildcardEditorScreen(plan, this, new CompoundTag(), null, true, heldTarget))));
        return true;
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
