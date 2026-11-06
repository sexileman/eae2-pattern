package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.net.C2SQueryNetworkStatusPacket;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import io.github.sexileman.eae2pattern.pattern.GenerationReport;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Virtual recipe list and independent inspector: work per frame depends on viewport size, not recipe count. */
public final class PatternWorkbenchScreen extends Screen implements GenerationListener {
    private static final int BG = 0xFF101923, SURFACE = 0xFF192633, LINE = 0xFF304253;
    private static final int TEXT = 0xFFE8F1F5, DIM = 0xFF99ADBD, ACCENT = 0xFF66D6BC, ROW = 28;
    private static final int[] MULTIPLIERS = {1, 2, 4, 8, 16, 32, 64};
    private final RecipePlan plan;
    private final Screen parent;
    private final List<RecipePlan.Entry> visible = new ArrayList<>();
    private final List<RecipePlan.Entry> matching = new ArrayList<>();
    private final List<DetailLine> details = new ArrayList<>();
    private ItemSearchQuery input = ItemSearchQuery.empty(), output = ItemSearchQuery.empty();
    private RecipeFilters filters = RecipeFilters.none();
    private String inputText = "", outputText = "";
    private EditBox inputBox, outputBox;
    private RecipePlan.Entry focused;
    private int inputMode, outputMode, outputPolicy, multiplierIndex;
    private boolean dropTools = true, selectedOnly, sending, dragList, dragDetails, filtering;
    private int filterCursor;
    private double listScroll, detailScroll;
    private int left, top, panelWidth, panelHeight, split, bodyTop, bodyBottom;
    private int selectedCount, eligibleCount, tickCount, searchDelay;
    private Component notice = Component.empty();
    private Action generate, inputFilter, outputFilter, tools, products, multiplier, selected;

    public PatternWorkbenchScreen(RecipePlan plan, Screen parent) {
        super(tr("title")); this.plan = plan; this.parent = parent; refresh();
    }
    private static Component tr(String key, Object... args) { return Component.translatable("eae2_pattern.workbench." + key, args); }

    @Override protected void init() {
        WorkbenchLayout layout = WorkbenchLayout.measure(width, height);
        panelWidth = layout.width(); panelHeight = layout.height(); left = layout.left(); top = layout.top(); split = layout.split();
        int searchY = layout.searchY(), filterY = layout.filterY(), toolbarY = layout.toolbarY();
        bodyTop = layout.bodyTop(); bodyBottom = layout.bodyBottom();
        int half = (panelWidth - 28) / 2;
        inputBox = addRenderableWidget(new EditBox(font, left + 10, top + searchY, half, 18, tr("input")));
        outputBox = addRenderableWidget(new EditBox(font, left + 18 + half, top + searchY, half, 18, tr("output")));
        inputBox.setMaxLength(160); outputBox.setMaxLength(160);
        inputBox.setValue(inputText); outputBox.setValue(outputText);
        inputBox.setHint(tr("input")); outputBox.setHint(tr("output"));
        inputBox.setResponder(value -> { inputText = value; searchDelay = 3; });
        outputBox.setResponder(value -> { outputText = value; searchDelay = 3; });
        int q = (panelWidth - 32) / 4;
        inputFilter = action(left + 10, top + filterY, q, () -> { inputMode = (inputMode + 1) % 4; refresh(); });
        outputFilter = action(left + 14 + q, top + filterY, q, () -> { outputMode = (outputMode + 1) % 6; refresh(); });
        tools = action(left + 18 + q * 2, top + filterY, q, () -> { dropTools = !dropTools; recount(); });
        products = action(left + 22 + q * 3, top + filterY, q, () -> { outputPolicy = (outputPolicy + 1) % 3; recount(); });
        int w = Math.min(68, (split - left - 28) / 4);
        action(left + 10, top + toolbarY, w, () -> { if (filtering) return; visible.forEach(e -> e.setSelected(true)); refresh(); }).setMessage(tr("all"));
        action(left + 14 + w, top + toolbarY, w, () -> { if (filtering) return; visible.forEach(e -> e.setSelected(false)); refresh(); }).setMessage(tr("none"));
        action(left + 18 + w * 2, top + toolbarY, w, () -> { if (filtering) return; visible.forEach(RecipePlan.Entry::toggle); refresh(); }).setMessage(tr("invert"));
        selected = action(left + 22 + w * 3, top + toolbarY, w, () -> { selectedOnly = !selectedOnly; refresh(); });
        multiplier = action(left + 10, top + panelHeight - 45, 75, () -> { multiplierIndex = (multiplierIndex + 1) % MULTIPLIERS.length; recount(); });
        action(left + panelWidth - 62, top + 9, 52, this::onClose).setMessage(tr("close"));
        generate = action(left + panelWidth - 142, top + panelHeight - 45, 132, this::generate);
        ClientGenerationFeedback.setListener(this); queryNetwork(); updateButtons();
    }
    private Action action(int x, int y, int w, Runnable click) { return addRenderableWidget(new Action(x, y, w, click)); }
    private void queryNetwork() { ClientNetworkState.reset(); PacketHandler.sendToServer(new C2SQueryNetworkStatusPacket(0)); }
    @Override public void tick() {
        inputBox.tick(); outputBox.tick();
        if (searchDelay > 0 && --searchDelay == 0) { input = ItemSearchQuery.parse(inputText); output = ItemSearchQuery.parse(outputText); refresh(); }
        if (filtering && searchDelay == 0) filterStep();
        if (++tickCount % 60 == 0 && !sending) queryNetwork();
        updateButtons();
    }
    private void refresh() {
        filters = new RecipeFilters(output, input, inputMode, outputMode, selectedOnly);
        matching.clear(); filterCursor = 0; filtering = true;
        updateButtons();
    }
    private void filterStep() {
        long deadline = System.nanoTime() + 4_000_000L;
        int work = 0;
        while (filterCursor < plan.entries().size() && work++ < 128 && System.nanoTime() < deadline) {
            RecipePlan.Entry e = plan.entries().get(filterCursor++);
            if (filters.rejects(e)) continue;
            matching.add(e);
        }
        if (filterCursor < plan.entries().size()) return;
        visible.clear(); visible.addAll(matching); matching.clear(); filtering = false;
        listScroll = 0;
        if (!visible.contains(focused)) focus(visible.isEmpty() ? null : visible.get(0));
        recount();
    }
    private void recount() {
        selectedCount = 0; eligibleCount = 0;
        for (RecipePlan.Entry e : visible) if (e.isSelected()) { selectedCount++; if (e.canEncode(outputPolicy, dropTools)) eligibleCount++; }
        updateButtons();
    }
    private void updateButtons() {
        if (generate == null) return;
        inputFilter.setMessage(tr("input_mode." + inputMode)); outputFilter.setMessage(tr("output_mode." + outputMode));
        tools.setMessage(tr(dropTools ? "tools_drop" : "tools_keep")); products.setMessage(tr("policy." + outputPolicy));
        selected.setMessage(tr(selectedOnly ? "selected_on" : "selected")); multiplier.setMessage(tr("multiplier", MULTIPLIERS[multiplierIndex]));
        generate.setMessage(tr(sending ? "sending" : filtering ? "filtering" : "generate", eligibleCount));
        generate.active = !sending && !filtering && searchDelay == 0 && eligibleCount > 0 && ClientNetworkState.hasNetwork() && ClientNetworkState.blankPatterns() >= eligibleCount;
    }
    private void generate() {
        if (!generate.active) return;
        List<PatternSpec> specs = new ArrayList<>(eligibleCount);
        for (RecipePlan.Entry e : visible) if (e.isSelected()) {
            PatternSpec spec = e.specForPolicy(MULTIPLIERS[multiplierIndex], outputPolicy, dropTools);
            if (spec != null) specs.add(spec);
        }
        try { PatternRequestSender.send(specs); sending = true; notice = tr("sending"); }
        catch (IllegalArgumentException e) { notice = Component.literal(e.getMessage()); }
        updateButtons();
    }
    @Override public void onGenerationFinished(GenerationReport report) {
        sending = false; notice = ClientGenerationFeedback.describe(report); queryNetwork();
    }
    private void focus(RecipePlan.Entry e) {
        focused = e; detailScroll = 0; details.clear();
        if (e == null) return;
        details.add(new DetailLine(e.label(), null, TEXT));
        details.add(new DetailLine(Component.literal(e.recipeId().toString()), null, DIM));
        details.add(new DetailLine(Component.translatable("eae2_pattern.kind." + e.kind().name().toLowerCase(Locale.ROOT)), null, ACCENT));
        details.add(new DetailLine(tr("inputs"), null, ACCENT));
        for (Ingredient i : e.inputs()) addIngredient(i, false, false);
        for (Ingredient i : e.catalysts()) addIngredient(i, false, false);
        details.add(new DetailLine(tr("outputs"), null, ACCENT));
        for (int n = 0; n < e.outputs().size(); n++) addIngredient(e.outputs().get(n), true, n != e.mainOutputIndex());
        details.add(new DetailLine(tr("semantics"), null, DIM));
    }
    private void addIngredient(Ingredient i, boolean product, boolean byproduct) {
        StackSpec spec = i.spec();
        String state = product ? (byproduct ? "byproduct" : "main") : (i.neverConsumed() ? "not_consumed" : i.probabilistic() ? "chance_consumed" : "consumed");
        details.add(new DetailLine(Component.literal(spec.amount() + (spec.isItem() ? " × " : " mB ")).append(name(spec)), spec, TEXT));
        details.add(new DetailLine(tr(state).copy().append(" · " + i.chanceText()), null, i.probabilistic() || i.neverConsumed() ? 0xFFEBC078 : DIM));
    }
    private Component name(StackSpec spec) {
        if (spec.isItem()) return spec.toItemStack().getHoverName();
        var fluid = ForgeRegistries.FLUIDS.getValue(spec.id());
        return fluid == null ? Component.literal(spec.id().toString()) : new FluidStack(fluid, 1, spec.tag()).getDisplayName();
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0xDC080F16); g.fill(left, top, left + panelWidth, top + panelHeight, BG);
        g.fill(left, top, left + 3, top + 26, ACCENT);
        text(g, tr("title").copy().append(" / ").append(plan.machineName()), left + 10, top + 10, panelWidth - 86, TEXT);
        g.fill(left + 8, bodyTop, split - 4, bodyBottom, SURFACE);
        g.fill(split + 4, bodyTop - 27, left + panelWidth - 8, bodyBottom, SURFACE);
        text(g, tr("detail"), split + 12, bodyTop - 21, left + panelWidth - split - 24, ACCENT);
        renderList(g, mx, my); renderDetails(g);
        Component status = !ClientNetworkState.hasStatus() ? tr("querying") : !ClientNetworkState.hasNetwork() ? tr("no_network") : tr("stock", ClientNetworkState.blankPatterns(), eligibleCount);
        text(g, status, left + 92, top + panelHeight - 39, panelWidth - 244, ClientNetworkState.hasNetwork() && ClientNetworkState.blankPatterns() >= eligibleCount ? ACCENT : 0xFFEBC078);
        text(g, notice.getString().isEmpty() ? tr("counts", visible.size(), plan.entries().size(), selectedCount, eligibleCount) : notice, left + 10, top + panelHeight - 17, panelWidth - 20, DIM);
        super.render(g, mx, my, partial);
        if (over(mx, my, left + 92, top + panelHeight - 45, left + panelWidth - 146, top + panelHeight - 22)) g.renderComponentTooltip(font, List.of(status), mx, my);
        if (inputBox.isMouseOver(mx, my) || outputBox.isMouseOver(mx, my)) g.renderComponentTooltip(font, List.of(tr("search_help")), mx, my);
        for (var child : children()) if (child instanceof Action a && a.isHovered())
            g.renderComponentTooltip(font, List.of(a.getMessage(), tr("option_help")), mx, my);
        if (over(mx, my, split + 4, bodyTop, left + panelWidth - 18, bodyBottom)) {
            int index = (int) ((my - bodyTop + detailScroll) / 18);
            if (index >= 0 && index < details.size()) {
                DetailLine line = details.get(index);
                if (line.spec != null && line.spec.isItem()) g.renderTooltip(font, line.spec.toItemStack(), mx, my);
                else g.renderComponentTooltip(font, List.of(line.text), mx, my);
            }
        }
    }
    private void renderList(GuiGraphics g, int mx, int my) {
        int x = left + 10, right = split - 12;
        g.enableScissor(x, bodyTop, right, bodyBottom);
        int first = (int) listScroll / ROW, count = Math.max(1, (bodyBottom - bodyTop) / ROW + 2);
        for (int n = first; n < Math.min(visible.size(), first + count); n++) {
            RecipePlan.Entry e = visible.get(n); int y = bodyTop + n * ROW - (int) listScroll;
            if (e == focused || over(mx, my, x, y, right, y + ROW)) g.fill(x, y, right, y + ROW - 1, e == focused ? 0xFF28483F : 0xFF243848);
            g.fill(x + 4, y + 9, x + 12, y + 17, e.isSelected() ? ACCENT : LINE);
            if (e.isSelected()) g.drawString(font, "+", x + 5, y + 9, BG, false);
            if (!e.inputs().isEmpty()) drawStack(g, e.inputs().get(0).spec(), x + 18, y + 5);
            g.drawString(font, ">", x + 37, y + 9, DIM, false);
            if (!e.outputs().isEmpty()) drawStack(g, e.outputs().get(0).spec(), x + 47, y + 5);
            text(g, e.label(), x + 69, y + 4, right - x - 73, e.isSelected() ? TEXT : DIM);
            String flags = (e.hasNeverConsumedInput() ? "NC " : "") + (e.hasProbabilisticInput() ? "IN% " : "") + (e.hasProbabilisticOutput() ? "OUT% " : "") + (e.hasByproduct() ? "BY " : "");
            text(g, Component.literal(flags.isEmpty() ? e.recipeId().getNamespace() : flags), x + 69, y + 16, right - x - 73, DIM);
        }
        if (visible.isEmpty()) text(g, tr("empty"), x + 6, bodyTop + 12, right - x - 12, DIM);
        g.disableScissor(); scrollbar(g, split - 10, listScroll, visible.size() * ROW);
    }
    private void renderDetails(GuiGraphics g) {
        int x = split + 12, right = left + panelWidth - 18;
        g.enableScissor(x, bodyTop, right, bodyBottom);
        int first = (int) detailScroll / 18, count = Math.max(1, (bodyBottom - bodyTop) / 18 + 2);
        for (int n = first; n < Math.min(details.size(), first + count); n++) {
            DetailLine line = details.get(n); int y = bodyTop + n * 18 - (int) detailScroll;
            if (line.spec != null) drawStack(g, line.spec, x, y);
            text(g, line.text, x + (line.spec == null ? 0 : 20), y + 4, right - x - (line.spec == null ? 0 : 20), line.color);
        }
        g.disableScissor(); scrollbar(g, left + panelWidth - 15, detailScroll, details.size() * 18);
    }
    private void drawStack(GuiGraphics g, StackSpec spec, int x, int y) {
        ItemStack stack = spec.toItemStack();
        if (!stack.isEmpty()) g.renderItem(stack, x, y);
        else { g.fill(x + 3, y + 2, x + 13, y + 14, 0xFF55ACCF); g.drawString(font, "~", x + 5, y + 4, TEXT, false); }
    }
    private void text(GuiGraphics g, Component value, int x, int y, int room, int color) {
        String s = value.getString(); g.drawString(font, font.width(s) <= room ? s : font.plainSubstrByWidth(s, Math.max(0, room - 6)) + "…", x, y, color, false);
    }
    private int thumbHeight(int content) { int h = Math.max(1, bodyBottom - bodyTop); return Math.min(h, Math.max(16, h * h / Math.max(1, content))); }
    private void scrollbar(GuiGraphics g, int x, double offset, int content) {
        int h = Math.max(1, bodyBottom - bodyTop), thumb = thumbHeight(content);
        int pos = bodyTop + (content <= h ? 0 : (int) ((h - thumb) * offset / (content - h)));
        g.fill(x, bodyTop, x + 4, bodyBottom, LINE); g.fill(x, pos, x + 4, pos + thumb, ACCENT);
    }
    private static boolean over(double x, double y, int l, int t, int r, int b) { return x >= l && x < r && y >= t && y < b; }
    private double clamp(double value, int content) { return Math.max(0, Math.min(value, Math.max(0, content - (bodyBottom - bodyTop)))); }
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if (over(mx, my, left + 8, bodyTop, split, bodyBottom)) { listScroll = clamp(listScroll - delta * ROW * 2, visible.size() * ROW); return true; }
        if (over(mx, my, split, bodyTop, left + panelWidth, bodyBottom)) { detailScroll = clamp(detailScroll - delta * 36, details.size() * 18); return true; }
        return super.mouseScrolled(mx, my, delta);
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && my >= bodyTop && my < bodyBottom) {
            dragList = mx >= split - 12 && mx < split; dragDetails = mx >= left + panelWidth - 18 && mx < left + panelWidth - 8;
            if (dragList || dragDetails) { dragTo(my); return true; }
            if (!filtering && over(mx, my, left + 10, bodyTop, split - 12, bodyBottom)) {
                int n = (int) ((my - bodyTop + listScroll) / ROW);
                if (n < visible.size()) { RecipePlan.Entry e = visible.get(n); focus(e); if (mx < left + 27) { e.toggle(); if (selectedOnly) refresh(); else recount(); } return true; }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
    private void dragTo(double my) {
        int h = Math.max(1, bodyBottom - bodyTop), content = dragList ? visible.size() * ROW : details.size() * 18, thumb = thumbHeight(content);
        double offset = (my - bodyTop - thumb / 2.0) / Math.max(1, h - thumb) * Math.max(0, content - h);
        if (dragList) listScroll = clamp(offset, content); else detailScroll = clamp(offset, content);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragList || dragDetails) { dragTo(my); return true; } return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) { dragList = dragDetails = false; return super.mouseReleased(mx, my, button); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void removed() { ClientGenerationFeedback.setListener(null); }
    @Override public boolean isPauseScreen() { return false; }
    private record DetailLine(Component text, StackSpec spec, int color) {}
    private final class Action extends AbstractButton {
        private final Runnable click;
        Action(int x, int y, int w, Runnable click) { super(x, y, w, 19, Component.empty()); this.click = click; }
        @Override public void onPress() { click.run(); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            int color = this == generate ? 0xFF265D50 : SURFACE;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isHoveredOrFocused() && active ? 0xFF365568 : color);
            g.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), active ? LINE : BG);
            String value = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 6);
            g.drawCenteredString(font, value, getX() + getWidth() / 2, getY() + 5, active ? TEXT : 0xFF657887);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }
}
