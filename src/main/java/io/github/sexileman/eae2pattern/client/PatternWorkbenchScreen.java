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
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import org.lwjgl.glfw.GLFW;

/** Virtual recipe list and independent inspector: work per frame depends on viewport size, not recipe count. */
public final class PatternWorkbenchScreen extends Screen implements GenerationListener {
    private static final int BG = WorkbenchTheme.BACKGROUND, SURFACE = WorkbenchTheme.SURFACE, LINE = WorkbenchTheme.LINE;
    private static final int TEXT = WorkbenchTheme.TEXT, DIM = WorkbenchTheme.DIM, ACCENT = WorkbenchTheme.ACCENT, ROW = 28;
    private final RecipePlan plan;
    private final Screen parent;
    private final List<RecipePlan.Entry> visible = new ArrayList<>();
    private final List<RecipePlan.Entry> matching = new ArrayList<>();
    private final List<DetailLine> details = new ArrayList<>();
    private ItemSearchQuery input = ItemSearchQuery.empty(), output = ItemSearchQuery.empty();
    private RecipeFilters filters = RecipeFilters.none();
    private String inputText = "", outputText = "";
    private EditBox inputBox, outputBox, multiplierBox;
    private String multiplierText = Integer.toString(ProcessingMultiplier.DEFAULT);
    private RecipePlan.Entry focused;
    private int inputMode, outputMode, outputPolicy;
    private Choice choice;
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
        inputBox = addRenderableWidget(new EditBox(font, left + 16, top + searchY + 5, half - 12, 14, tr("input")));
        outputBox = addRenderableWidget(new EditBox(font, left + 24 + half, top + searchY + 5, half - 12, 14, tr("output")));
        inputBox.setMaxLength(160); outputBox.setMaxLength(160);
        inputBox.setBordered(false); outputBox.setBordered(false);
        inputBox.setTextColor(TEXT); outputBox.setTextColor(TEXT);
        inputBox.setValue(inputText); outputBox.setValue(outputText);
        inputBox.setHint(tr("input")); outputBox.setHint(tr("output"));
        inputBox.setResponder(value -> { inputText = value; searchDelay = 3; });
        outputBox.setResponder(value -> { outputText = value; searchDelay = 3; });
        int q = (panelWidth - 32) / 4;
        inputFilter = action(left + 10, top + filterY, q, () -> choose(inputFilter, "input_mode.", 4, inputMode, value -> { inputMode = value; refresh(); }));
        outputFilter = action(left + 14 + q, top + filterY, q, () -> choose(outputFilter, "output_mode.", 6, outputMode, value -> { outputMode = value; refresh(); }));
        tools = action(left + 18 + q * 2, top + filterY, q, () -> { dropTools = !dropTools; recount(); }).toggle(() -> dropTools);
        products = action(left + 22 + q * 3, top + filterY, q, () -> choose(products, "policy.", 3, outputPolicy, value -> { outputPolicy = value; recount(); }));
        int w = Math.min(68, (split - left - 28) / 4);
        action(left + 10, top + toolbarY, w, () -> { if (filtering) return; visible.forEach(e -> e.setSelected(true)); refresh(); }).setMessage(tr("all"));
        action(left + 14 + w, top + toolbarY, w, () -> { if (filtering) return; visible.forEach(e -> e.setSelected(false)); refresh(); }).setMessage(tr("none"));
        action(left + 18 + w * 2, top + toolbarY, w, () -> { if (filtering) return; visible.forEach(RecipePlan.Entry::toggle); refresh(); }).setMessage(tr("invert"));
        selected = action(left + 22 + w * 3, top + toolbarY, w, () -> { selectedOnly = !selectedOnly; refresh(); }).toggle(() -> selectedOnly);
        multiplier = action(left + 10, top + panelHeight - 45, 54, () -> {
            List<Component> labels = new ArrayList<>();
            for (int preset : ProcessingMultiplier.PRESETS) labels.add(tr(preset == 1 ? "default_multiplier" : "multiplier", preset));
            choice = new Choice(multiplier, labels, -1, index -> multiplierBox.setValue(Integer.toString(ProcessingMultiplier.PRESETS[index])));
        });
        multiplierBox = addRenderableWidget(new EditBox(font, left + 72, top + panelHeight - 40, 38, 14, tr("multiplier_input")));
        multiplierBox.setMaxLength(5); multiplierBox.setFilter(ProcessingMultiplier::acceptsText);
        multiplierBox.setBordered(false); multiplierBox.setValue(multiplierText);
        multiplierBox.setResponder(value -> { multiplierText = value; updateButtons(); });
        action(left + panelWidth - 62, top + 9, 52, this::onClose).setMessage(tr("close"));
        generate = action(left + panelWidth - 130, top + panelHeight - 45, 120, this::generate);
        choice = null;
        ClientGenerationFeedback.setListener(this); queryNetwork(); updateButtons();
    }
    private Action action(int x, int y, int w, Runnable click) { return addRenderableWidget(new Action(x, y, w, click)); }
    private void queryNetwork() { ClientNetworkState.reset(); PacketHandler.sendToServer(new C2SQueryNetworkStatusPacket(0)); }
    @Override public void tick() {
        inputBox.tick(); outputBox.tick(); multiplierBox.tick();
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
        selected.setMessage(tr("selected")); multiplier.setMessage(tr("multiplier_menu"));
        multiplierBox.setTextColor(ProcessingMultiplier.parse(multiplierText) > 0 ? TEXT : 0xFFFF9D98);
        generate.setMessage(tr(sending ? "sending" : filtering ? "filtering" : "generate", eligibleCount));
        generate.active = !sending && !filtering && searchDelay == 0 && ProcessingMultiplier.parse(multiplierText) > 0
                && eligibleCount > 0 && ClientNetworkState.hasNetwork() && ClientNetworkState.blankPatterns() >= eligibleCount;
    }
    private void generate() {
        if (!generate.active) return;
        List<PatternSpec> specs = new ArrayList<>(eligibleCount);
        for (RecipePlan.Entry e : visible) if (e.isSelected()) {
            PatternSpec spec = e.specForPolicy(ProcessingMultiplier.parse(multiplierText), outputPolicy, dropTools);
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
        g.fill(0, 0, width, height, 0xCC000000);
        WorkbenchTheme.rounded(g, left, top, panelWidth, panelHeight, BG);
        text(g, tr("title").copy().append(" / ").append(plan.machineName()), left + 10, top + 10, panelWidth - 86, TEXT);
        g.fill(left + 10, top + 27, left + panelWidth - 10, top + 28, LINE);
        searchSurface(g, inputBox); searchSurface(g, outputBox);
        g.fill(split, bodyTop - 27, split + 1, bodyBottom, LINE);
        text(g, tr("detail"), split + 12, bodyTop - 21, left + panelWidth - split - 24, DIM);
        WorkbenchTheme.rounded(g, left + 68, top + panelHeight - 45, 48, 19, SURFACE);
        renderList(g, mx, my); renderDetails(g);
        Component status = !ClientNetworkState.hasStatus() ? tr("querying") : !ClientNetworkState.hasNetwork() ? tr("no_network") : tr("stock", ClientNetworkState.blankPatterns(), eligibleCount);
        if (ProcessingMultiplier.parse(multiplierText) == 0) status = tr("multiplier_invalid", ProcessingMultiplier.MAX);
        g.fill(left + 10, bodyBottom + 3, left + panelWidth - 10, bodyBottom + 4, LINE);
        text(g, status, left + 124, top + panelHeight - 39, panelWidth - 260, ClientNetworkState.hasNetwork() && ClientNetworkState.blankPatterns() >= eligibleCount ? DIM : WorkbenchTheme.WARNING);
        text(g, notice.getString().isEmpty() ? tr("counts", visible.size(), plan.entries().size(), selectedCount, eligibleCount) : notice, left + 10, top + panelHeight - 17, panelWidth - 20, DIM);
        super.render(g, mx, my, partial);
        if (choice != null) { choice.render(g, mx, my); return; }
        if (over(mx, my, left + 124, top + panelHeight - 45, left + panelWidth - 134, top + panelHeight - 22)) g.renderComponentTooltip(font, List.of(status), mx, my);
        if (inputBox.isMouseOver(mx, my) || outputBox.isMouseOver(mx, my)) g.renderComponentTooltip(font, List.of(tr("search_help")), mx, my);
        if (multiplierBox.isMouseOver(mx, my)) g.renderComponentTooltip(font, List.of(tr("multiplier_help", ProcessingMultiplier.MAX)), mx, my);
        for (var child : children()) if (child instanceof Action a && a.isHovered())
            g.renderComponentTooltip(font, List.of(a.getMessage(), tr(a == tools ? "tools_help" : a == multiplier ? "multiplier_help" : "option_help", ProcessingMultiplier.MAX)), mx, my);
        if (over(mx, my, split + 4, bodyTop, left + panelWidth - 18, bodyBottom)) {
            int index = (int) ((my - bodyTop + detailScroll) / 18);
            if (index >= 0 && index < details.size()) {
                DetailLine line = details.get(index);
                if (line.spec != null && line.spec.isItem()) g.renderTooltip(font, line.spec.toItemStack(), mx, my);
                else g.renderComponentTooltip(font, List.of(line.text), mx, my);
            }
        }
    }
    private void searchSurface(GuiGraphics g, EditBox box) {
        WorkbenchTheme.rounded(g, box.getX() - 6, box.getY() - 5, box.getWidth() + 12, 19, box.isFocused() ? LINE : SURFACE);
        WorkbenchTheme.rounded(g, box.getX() - 5, box.getY() - 4, box.getWidth() + 10, 17, SURFACE);
    }
    private void renderList(GuiGraphics g, int mx, int my) {
        int x = left + 10, right = split - 12;
        g.enableScissor(x, bodyTop, right, bodyBottom);
        int first = (int) listScroll / ROW, count = Math.max(1, (bodyBottom - bodyTop) / ROW + 2);
        for (int n = first; n < Math.min(visible.size(), first + count); n++) {
            RecipePlan.Entry e = visible.get(n); int y = bodyTop + n * ROW - (int) listScroll;
            if (e == focused || over(mx, my, x, y, right, y + ROW)) WorkbenchTheme.rounded(g, x, y, right - x, ROW - 1, e == focused ? WorkbenchTheme.HOVER : SURFACE);
            WorkbenchTheme.rounded(g, x + 4, y + 9, 9, 9, e.isSelected() ? ACCENT : LINE);
            if (e.isSelected()) {
                g.fill(x + 6, y + 13, x + 8, y + 15, BG);
                g.fill(x + 8, y + 11, x + 10, y + 15, BG);
            }
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
        else { WorkbenchTheme.rounded(g, x + 2, y + 1, 12, 14, LINE); g.drawString(font, "~", x + 5, y + 4, TEXT, false); }
    }
    private void text(GuiGraphics g, Component value, int x, int y, int room, int color) {
        String s = value.getString(); g.drawString(font, font.width(s) <= room ? s : font.plainSubstrByWidth(s, Math.max(0, room - 6)) + "…", x, y, color, false);
    }
    private int thumbHeight(int content) { int h = Math.max(1, bodyBottom - bodyTop); return Math.min(h, Math.max(16, h * h / Math.max(1, content))); }
    private void scrollbar(GuiGraphics g, int x, double offset, int content) {
        int h = Math.max(1, bodyBottom - bodyTop), thumb = thumbHeight(content);
        int pos = bodyTop + (content <= h ? 0 : (int) ((h - thumb) * offset / (content - h)));
        if (content <= h) return;
        g.fill(x + 1, bodyTop, x + 3, bodyBottom, SURFACE);
        WorkbenchTheme.rounded(g, x, pos, 4, thumb, 0xFF686868);
    }
    private static boolean over(double x, double y, int l, int t, int r, int b) { return x >= l && x < r && y >= t && y < b; }
    private double clamp(double value, int content) { return Math.max(0, Math.min(value, Math.max(0, content - (bodyBottom - bodyTop)))); }
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if (choice != null) return true;
        if (over(mx, my, left + 8, bodyTop, split, bodyBottom)) { listScroll = clamp(listScroll - delta * ROW * 2, visible.size() * ROW); return true; }
        if (over(mx, my, split, bodyTop, left + panelWidth, bodyBottom)) { detailScroll = clamp(detailScroll - delta * 36, details.size() * 18); return true; }
        return super.mouseScrolled(mx, my, delta);
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (choice != null) {
            Choice current = choice; choice = null;
            if (button == 0) current.click(mx, my);
            return true;
        }
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
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (choice != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { choice = null; return true; }
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                choice.cursor = Math.floorMod(choice.cursor + (key == GLFW.GLFW_KEY_UP ? -1 : 1), choice.labels.size());
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                Choice current = choice; choice = null; current.select.accept(current.cursor); return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void removed() { ClientGenerationFeedback.setListener(null); }
    @Override public boolean isPauseScreen() { return false; }
    private record DetailLine(Component text, StackSpec spec, int color) {}
    private void choose(Action anchor, String prefix, int count, int selectedIndex, IntConsumer select) {
        List<Component> labels = new ArrayList<>();
        for (int i = 0; i < count; i++) labels.add(tr(prefix + i));
        choice = new Choice(anchor, labels, selectedIndex, select);
    }

    private final class Choice {
        final List<Component> labels;
        final IntConsumer select;
        final int x, y, w, h, selectedIndex;
        int cursor;
        Choice(Action anchor, List<Component> labels, int selectedIndex, IntConsumer select) {
            this.labels = labels; this.select = select; this.selectedIndex = selectedIndex;
            cursor = Math.max(0, selectedIndex);
            w = Math.min(width - 12, Math.max(anchor.getWidth(), labels.stream().mapToInt(font::width).max().orElse(80) + 28));
            h = labels.size() * 19 + 6;
            x = Math.max(6, Math.min(anchor.getX(), width - w - 6));
            y = Math.max(6, Math.min(anchor.getY() + anchor.getHeight() + 3, height - h - 6));
        }
        void render(GuiGraphics g, int mx, int my) {
            g.pose().pushPose(); g.pose().translate(0, 0, 400);
            WorkbenchTheme.rounded(g, x - 1, y - 1, w + 2, h + 2, LINE);
            WorkbenchTheme.rounded(g, x, y, w, h, SURFACE);
            for (int i = 0; i < labels.size(); i++) {
                int rowY = y + 3 + i * 19;
                if (i == cursor || over(mx, my, x, rowY, x + w, rowY + 19))
                    WorkbenchTheme.rounded(g, x + 3, rowY, w - 6, 19, WorkbenchTheme.HOVER);
                text(g, labels.get(i), x + 8, rowY + 5, w - 26, TEXT);
                if (i == selectedIndex) g.drawString(font, "✓", x + w - 16, rowY + 5, TEXT, false);
            }
            g.pose().popPose();
        }
        void click(double mx, double my) {
            if (!over(mx, my, x, y + 3, x + w, y + h - 3)) return;
            int index = (int) (my - y - 3) / 19;
            if (index < labels.size()) select.accept(index);
        }
    }

    private final class Action extends AbstractButton {
        private final Runnable click;
        private BooleanSupplier state;
        Action(int x, int y, int w, Runnable click) { super(x, y, w, 19, Component.empty()); this.click = click; }
        Action toggle(BooleanSupplier state) { this.state = state; return this; }
        @Override public void onPress() { click.run(); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            boolean primary = this == generate, hovered = isHoveredOrFocused() && active;
            int color = primary && active ? (hovered ? 0xFFD6D6D6 : ACCENT) : hovered ? WorkbenchTheme.HOVER : SURFACE;
            WorkbenchTheme.rounded(g, getX(), getY(), getWidth(), getHeight(), color);
            int textColor = !active ? 0xFF707070 : primary ? BG : TEXT;
            int reserve = state == null ? 10 : 25;
            String label = getMessage().getString();
            String value = font.width(label) <= getWidth() - reserve ? label : font.plainSubstrByWidth(label, Math.max(0, getWidth() - reserve - 6)) + "…";
            if (state == null) g.drawCenteredString(font, value, getX() + getWidth() / 2, getY() + 5, textColor);
            else {
                g.drawString(font, value, getX() + 6, getY() + 5, textColor, false);
                boolean on = state.getAsBoolean();
                int sx = getX() + getWidth() - 19, sy = getY() + 6;
                WorkbenchTheme.rounded(g, sx, sy, 14, 8, on ? 0xFFBFBFBF : 0xFF484848);
                WorkbenchTheme.rounded(g, sx + (on ? 7 : 1), sy + 1, 6, 6, on ? BG : DIM);
            }
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }
}
