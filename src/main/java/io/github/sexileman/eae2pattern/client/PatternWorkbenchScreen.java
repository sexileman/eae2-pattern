package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.net.C2SQueryNetworkStatusPacket;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import io.github.sexileman.eae2pattern.pattern.GenerationReport;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import io.github.sexileman.eae2pattern.config.WorkbenchPreferences;
import io.github.sexileman.eae2pattern.net.S2CInspectionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import org.lwjgl.glfw.GLFW;

/** Virtual recipe list and independent inspector: work per frame depends on viewport size, not recipe count. */
public final class PatternWorkbenchScreen extends Screen implements GenerationListener {
    private static final int BG = WorkbenchTheme.BACKGROUND, SURFACE = WorkbenchTheme.SURFACE, LINE = WorkbenchTheme.LINE;
    private static final int TEXT = WorkbenchTheme.TEXT, DIM = WorkbenchTheme.DIM, ACCENT = WorkbenchTheme.ACCENT, ROW = 38;
    private final RecipePlan plan;
    private final Screen parent;
    private final RecipeNavigation.ReturnContext jeiReturn;
    private boolean navigatingToJei;
    private final List<RecipePlan.Entry> visible = new ArrayList<>();
    private final List<RecipePlan.Entry> matching = new ArrayList<>();
    private final List<DetailLine> details = new ArrayList<>();
    private final List<DetailLine> referenceDetails = new ArrayList<>();
    private final StackVisuals visuals = new StackVisuals();
    private final List<IconHit> iconHits = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private final Set<net.minecraft.resources.ResourceLocation> existing = new HashSet<>();
    private final Map<net.minecraft.resources.ResourceLocation, Component> previewErrors = new LinkedHashMap<>();
    private final Map<RecipeGroups.Group, Integer> groupSelected = new java.util.IdentityHashMap<>();
    private List<RecipeGroups.Group> groups = List.of();
    private List<RecipeGroups.Row> rows = List.of();
    private RecipeGroups.Builder groupBuilder;
    private int groupingMode, hiddenSelected, skippedCount, previewDelay;
    private int previewRequest = -1, existingRequest = -1;
    private boolean sourceView, checking;
    private RecipePlan.Entry pinned;
    private PatternSpec preview;
    private Component previewNotice = Component.empty();
    private StackSpec exactInput, exactOutput;
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
    private Action generate, inputFilter, outputFilter, tools, products, multiplier, grouping, more, detailMode;

    public PatternWorkbenchScreen(RecipePlan plan, Screen parent) {
        super(tr("title")); this.plan = plan; this.parent = parent; jeiReturn = RecipeNavigation.capture(parent);
        dropTools = WorkbenchPreferences.DROP_TOOLS.get(); outputPolicy = WorkbenchPreferences.OUTPUT_POLICY.get();
        groupingMode = WorkbenchPreferences.GROUPING.get(); multiplierText = "" + WorkbenchPreferences.MULTIPLIER.get();
        refresh();
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
        inputBox.setResponder(value -> { inputText = value; exactInput = null; searchDelay = 3; });
        outputBox.setResponder(value -> { outputText = value; exactOutput = null; searchDelay = 3; });
        int q = (panelWidth - 32) / 4;
        inputFilter = action(left + 10, top + filterY, q, () -> choose(inputFilter, "input_mode.", 4, inputMode, value -> { inputMode = value; refresh(); }));
        outputFilter = action(left + 14 + q, top + filterY, q, () -> choose(outputFilter, "output_mode.", 6, outputMode, value -> { outputMode = value; refresh(); }));
        tools = action(left + 18 + q * 2, top + filterY, q, () -> { dropTools = !dropTools; optionsChanged(); }).toggle(() -> dropTools);
        products = action(left + 22 + q * 3, top + filterY, q, () -> choose(products, "policy.", 3, outputPolicy, value -> { outputPolicy = value; optionsChanged(); }));
        int w = (split - left - 30) / 5;
        grouping = action(left + 10, top + toolbarY, w * 2, () -> choose(grouping, "grouping.", 4, groupingMode, value -> {
            groupingMode = value; expanded.clear(); refresh();
        }));
        action(left + 14 + w * 2, top + toolbarY, w, () -> { if (filtering || checking) return; visible.forEach(e -> e.setSelected(true)); refresh(); }).setMessage(tr("all"));
        action(left + 18 + w * 3, top + toolbarY, w, () -> { if (filtering || checking) return; visible.forEach(e -> e.setSelected(false)); refresh(); }).setMessage(tr("none"));
        more = action(left + 22 + w * 4, top + toolbarY, w, this::moreMenu);
        detailMode = action(split + 10, top + toolbarY, Math.max(35, left + panelWidth - split - 65), () -> {
            choice = new Choice(detailMode, List.of(tr("final_view"), tr("source_view")), sourceView ? 1 : 0,
                    index -> { sourceView = index == 1; rebuildDetails(); });
        });
        action(left + panelWidth - 50, top + toolbarY, 40, () -> {
            if (focused != null && focused.jeiRecipe() != null) {
                navigatingToJei = true;
                if (jeiReturn == null) minecraft.setScreen(parent);
                RecipeNavigation.show(plan, focused);
            }
        }).setMessage(Component.literal("JEI ↗"));
        multiplier = action(left + 10, top + panelHeight - 45, 54, () -> {
            List<Component> labels = new ArrayList<>();
            for (int preset : ProcessingMultiplier.PRESETS) labels.add(tr(preset == 1 ? "default_multiplier" : "multiplier", preset));
            choice = new Choice(multiplier, labels, -1, index -> multiplierBox.setValue(Integer.toString(ProcessingMultiplier.PRESETS[index])));
        });
        multiplierBox = addRenderableWidget(new EditBox(font, left + 72, top + panelHeight - 40, 38, 14, tr("multiplier_input")));
        multiplierBox.setMaxLength(5); multiplierBox.setFilter(ProcessingMultiplier::acceptsText);
        multiplierBox.setBordered(false); multiplierBox.setValue(multiplierText);
        multiplierBox.setResponder(value -> { multiplierText = value; optionsChanged(); });
        action(left + panelWidth - 62, top + 9, 52, this::onClose).setMessage(tr("close"));
        generate = action(left + panelWidth - 130, top + panelHeight - 45, 120, this::generate);
        choice = null; navigatingToJei = false;
        ClientGenerationFeedback.setListener(this); InspectionFeedback.setListener(this::inspectionFinished);
        ClientNetworkState.reset(); queryNetwork(); queuePreview(); rebuildDetails(); updateButtons();
    }
    private Action action(int x, int y, int w, Runnable click) { return addRenderableWidget(new Action(x, y, w, click)); }
    private void queryNetwork() { PacketHandler.sendToServer(new C2SQueryNetworkStatusPacket(0)); }
    @Override public void tick() {
        inputBox.tick(); outputBox.tick(); multiplierBox.tick();
        if (searchDelay > 0 && --searchDelay == 0) { input = ItemSearchQuery.parse(inputText); output = ItemSearchQuery.parse(outputText); refresh(); }
        if (filtering && searchDelay == 0) filterStep();
        if (previewDelay > 0 && !sending && !checking && --previewDelay == 0) requestPreview();
        if (++tickCount % 60 == 0 && !sending) queryNetwork();
        updateButtons();
    }
    private void refresh() {
        filters = new RecipeFilters(output, input, inputMode, outputMode, selectedOnly);
        matching.clear(); groupBuilder = null; filterCursor = 0; filtering = true;
        updateButtons();
    }
    private void filterStep() {
        if (groupBuilder != null) {
            if (groupBuilder.step()) { groups = groupBuilder.finish(); finishFilter(); }
            return;
        }
        long deadline = System.nanoTime() + 4_000_000L;
        int work = 0;
        while (filterCursor < plan.entries().size() && work++ < 1024 && System.nanoTime() < deadline) {
            RecipePlan.Entry e = plan.entries().get(filterCursor++);
            if (filters.rejects(e)) continue;
            if (exactInput != null && java.util.stream.Stream.concat(e.inputs().stream(), e.catalysts().stream())
                    .noneMatch(i -> RecipeDiagnostics.sameMaterial(exactInput, i.spec()))) continue;
            if (exactOutput != null && e.outputs().stream().noneMatch(i -> RecipeDiagnostics.sameMaterial(exactOutput, i.spec()))) continue;
            matching.add(e);
        }
        if (filterCursor < plan.entries().size()) return;
        if (groupingMode != 0) { groupBuilder = new RecipeGroups.Builder(matching, groupingMode); return; }
        groups = List.of(); finishFilter();
    }
    private void finishFilter() {
        visible.clear(); visible.addAll(matching); matching.clear(); groupBuilder = null; filtering = false;
        if (expanded.isEmpty() && !groups.isEmpty()) expanded.add(groups.get(0).key().id());
        rebuildRows();
        listScroll = 0;
        if (!visible.contains(focused)) focus(visible.isEmpty() ? null : visible.get(0));
        recount();
    }
    private void recount() {
        selectedCount = 0; eligibleCount = 0;
        for (RecipePlan.Entry e : visible) if (e.isSelected()) { selectedCount++; if (eligible(e)) eligibleCount++; }
        hiddenSelected = plan.selectedCount() - selectedCount;
        skippedCount = selectedCount - eligibleCount;
        groupSelected.clear();
        for (RecipeGroups.Group group : groups) groupSelected.put(group, (int) group.entries().stream().filter(RecipePlan.Entry::isSelected).count());
        updateButtons();
    }
    private void updateButtons() {
        if (generate == null) return;
        inputFilter.setMessage(tr("input_mode." + inputMode)); outputFilter.setMessage(tr("output_mode." + outputMode));
        tools.setMessage(tr(dropTools ? "tools_drop" : "tools_keep")); products.setMessage(tr("policy." + outputPolicy));
        grouping.setMessage(tr("grouping." + groupingMode)); more.setMessage(tr("more"));
        detailMode.setMessage(tr(sourceView ? "source_view" : "final_view")); multiplier.setMessage(tr("multiplier_menu"));
        tools.active = products.active = multiplier.active = multiplierBox.active = !checking && !sending;
        more.active = !checking && !sending;
        multiplierBox.setTextColor(ProcessingMultiplier.parse(multiplierText) > 0 ? TEXT : 0xFFFF9D98);
        generate.setMessage(tr(sending ? "sending" : checking ? "checking" : filtering ? "filtering" : "generate_current", eligibleCount));
        generate.active = !sending && !checking && !filtering && searchDelay == 0 && ProcessingMultiplier.parse(multiplierText) > 0
                && eligibleCount > 0 && ClientNetworkState.hasNetwork() && ClientNetworkState.blankPatterns() >= eligibleCount;
    }
    private void generate() {
        if (!generate.active) return;
        List<PatternSpec> specs = selectedSpecs();
        try { PatternRequestSender.send(specs); sending = true; notice = tr("sending"); }
        catch (IllegalArgumentException e) { notice = Component.literal(e.getMessage()); }
        updateButtons();
    }
    private List<PatternSpec> selectedSpecs() {
        List<PatternSpec> specs = new ArrayList<>(eligibleCount);
        for (RecipePlan.Entry e : visible) if (e.isSelected() && eligible(e)) {
            PatternSpec spec = e.specForPolicy(ProcessingMultiplier.parse(multiplierText), outputPolicy, dropTools);
            if (spec != null) specs.add(spec);
        }
        return specs;
    }
    @Override public void onGenerationFinished(GenerationReport report) {
        sending = false; notice = ClientGenerationFeedback.describe(report); queryNetwork();
    }
    private void focus(RecipePlan.Entry e) {
        focused = e; detailScroll = 0; queuePreview(); rebuildDetails();
    }
    private void addIngredient(Ingredient i, boolean product, boolean byproduct) {
        StackSpec spec = i.spec();
        String state = product ? (byproduct ? "byproduct" : "main") : (i.neverConsumed() ? "not_consumed" : i.probabilistic() ? "chance_consumed" : "consumed");
        details.add(stackLine(spec, product ? 1 : 0));
        if (i.neverConsumed() || i.probabilistic() || product && byproduct)
            details.add(new DetailLine(tr(state).copy().append(" · " + i.chanceText()), null, i.probabilistic() || i.neverConsumed() ? 0xFFEBC078 : DIM));
    }
    private Component name(StackSpec spec) {
        return visuals.name(spec);
    }

    private void rebuildRows() {
        rows = RecipeGroups.rows(visible, groups, expanded, groupingMode);
        listScroll = clamp(listScroll, rows.size() * ROW);
        iconHits.clear();
    }
    private void optionsChanged() {
        existing.clear(); previewErrors.clear(); queuePreview(); recount(); rebuildDetails();
    }
    private boolean eligible(RecipePlan.Entry entry) { return entry.canEncode(outputPolicy, dropTools) && !previewErrors.containsKey(entry.recipeId()); }
    private String skipReason(RecipePlan.Entry entry) {
        return previewErrors.containsKey(entry.recipeId()) ? "skip_server" : RecipeDiagnostics.skipReason(entry, outputPolicy, dropTools);
    }
    private void queuePreview() {
        preview = null; previewRequest = -1; previewDelay = focused == null ? 0 : 4;
        previewNotice = tr("preview_loading");
    }
    private void requestPreview() {
        if (focused == null) return;
        int scale = ProcessingMultiplier.parse(multiplierText);
        PatternSpec spec = scale == 0 ? null : focused.specForPolicy(scale, outputPolicy, dropTools);
        if (spec == null) {
            previewNotice = scale == 0 ? tr("multiplier_invalid", ProcessingMultiplier.MAX) : tr(RecipeDiagnostics.skipReason(focused, outputPolicy, dropTools));
            rebuildDetails(); return;
        }
        try { previewRequest = PatternRequestSender.send(List.of(spec), 2); }
        catch (IllegalArgumentException e) { previewNotice = Component.literal(e.getMessage()); }
        rebuildDetails();
    }
    private void inspectionFinished(S2CInspectionPacket result) {
        if (result.operation() == 2 && result.requestId() == previewRequest) {
            preview = !result.success() || result.preview().isEmpty() ? null : result.preview().get(0);
            previewNotice = preview != null ? tr("server_preview") : result.details().isEmpty() ? tr("preview_failed") : Component.literal(result.details().get(0));
            if (preview == null) previewErrors.put(focused.recipeId(), previewNotice); else previewErrors.remove(focused.recipeId());
            recount(); rebuildDetails();
        } else if (result.operation() == 1 && result.requestId() == existingRequest) {
            checking = false;
            if (result.success()) {
                existing.addAll(result.existing());
                for (RecipePlan.Entry entry : plan.entries()) if (existing.contains(entry.recipeId())) entry.setSelected(false);
                notice = tr("existing_result", result.checked(), result.existing().size(), result.failed());
                refresh();
            } else notice = tr("existing_failed");
            queuePreview(); updateButtons();
        }
    }
    private void checkExisting() {
        if (sending || checking || filtering) return;
        if (!ClientNetworkState.hasNetwork()) { notice = tr("no_network"); return; }
        if (eligibleCount == 0) { notice = Component.translatable("eae2_pattern.report.nothing"); return; }
        if (ProcessingMultiplier.parse(multiplierText) == 0) { notice = tr("multiplier_invalid", ProcessingMultiplier.MAX); return; }
        try {
            existing.clear(); existingRequest = PatternRequestSender.send(selectedSpecs(), 1);
            checking = true; notice = tr("checking"); updateButtons();
        } catch (IllegalArgumentException e) { notice = Component.literal(e.getMessage()); }
    }
    private void rebuildDetails() {
        details.clear(); referenceDetails.clear();
        if (focused == null) return;
        if (sourceView) {
            if (pinned != null && pinned != focused) {
                details.add(new DetailLine(tr("reference"), null, ACCENT));
                sourceDetails(pinned);
                if (left + panelWidth - split >= 300) { referenceDetails.addAll(details); details.clear(); }
                details.add(new DetailLine(tr("current_recipe"), null, ACCENT));
            }
            sourceDetails(focused);
        } else {
            details.add(new DetailLine(focused.label(), null, TEXT));
            details.add(new DetailLine(previewNotice, null, preview == null ? WorkbenchTheme.WARNING : DIM));
            if (preview != null) {
                details.add(new DetailLine(tr("encoded_inputs"), null, ACCENT));
                for (StackSpec input : preview.inputs()) details.add(stackLine(input, 0));
                details.add(new DetailLine(tr("encoded_outputs"), null, ACCENT));
                for (StackSpec output : preview.outputs()) details.add(stackLine(output, 1));
                if (focused.hasProbabilisticOutput()) details.add(new DetailLine(tr("chance_warning"), null, WorkbenchTheme.WARNING));
                if (focused.hasProbabilisticInput()) details.add(new DetailLine(tr("chance_input_warning"), null, WorkbenchTheme.WARNING));
            }
            if (focused.hasNeverConsumedInput()) {
                details.add(new DetailLine(tr(dropTools && focused.kind() == io.github.sexileman.eae2pattern.pattern.PatternKind.PROCESSING ? "machine_setup" : "required_tools"), null, WorkbenchTheme.WARNING));
                for (Ingredient input : focused.inputs()) if (input.neverConsumed()) addIngredient(input, false, false);
                for (Ingredient input : focused.catalysts()) addIngredient(input, false, false);
            }
            details.add(new DetailLine(tr("server_preview_help"), null, DIM));
        }
        detailScroll = clamp(detailScroll, detailContentHeight());
    }
    private DetailLine stackLine(StackSpec spec, int side) {
        Component text = Component.literal(spec.amount() + (spec.isItem() ? " × " : " mB ")).append(name(spec));
        if (spec.tag() != null && spec.tag().contains("Configuration"))
            text = text.copy().append(" · ").append(tr("circuit_setting", spec.tag().getInt("Configuration")));
        return new DetailLine(text, spec, TEXT, side);
    }
    private void sourceDetails(RecipePlan.Entry entry) {
        details.add(new DetailLine(entry.label(), null, TEXT));
        details.add(new DetailLine(Component.literal(entry.recipeId().toString()), null, DIM));
        details.add(new DetailLine(Component.translatable("eae2_pattern.kind." + entry.kind().name().toLowerCase(Locale.ROOT)), null, DIM));
        details.add(new DetailLine(tr("inputs"), null, ACCENT));
        for (Ingredient input : entry.inputs()) addIngredient(input, false, false);
        for (Ingredient input : entry.catalysts()) addIngredient(input, false, false);
        details.add(new DetailLine(tr("outputs"), null, ACCENT));
        for (int i = 0; i < entry.outputs().size(); i++) addIngredient(entry.outputs().get(i), true, i != entry.mainOutputIndex());
        details.add(new DetailLine(tr("semantics"), null, DIM));
    }
    private void moreMenu() {
        if (checking || sending) return;
        List<Component> labels = List.of(tr(selectedOnly ? "show_all" : "selected"), tr("invert"),
                tr(pinned != null ? "unpin" : "pin"), tr("check_stored"),
                tr("skip_summary"), tr("collapse"), tr("clear_search"), tr("reset_settings"));
        choice = new Choice(more, labels, -1, index -> {
            switch (index) {
                case 0 -> { selectedOnly = !selectedOnly; refresh(); }
                case 1 -> { if (!filtering) { visible.forEach(RecipePlan.Entry::toggle); refresh(); } }
                case 2 -> { pinned = pinned != null ? null : focused; sourceView = true; rebuildDetails(); }
                case 3 -> checkExisting();
                case 4 -> {
                    Map<String, Integer> reasons = new LinkedHashMap<>();
                    for (RecipePlan.Entry e : visible) if (e.isSelected()) {
                        String reason = skipReason(e);
                        if (!reason.isEmpty()) reasons.merge(reason, 1, Integer::sum);
                    }
                    notice = tr("skipped_count", skippedCount);
                    for (var reason : reasons.entrySet()) notice = notice.copy().append(" · ").append(tr(reason.getKey())).append(" ×" + reason.getValue());
                }
                case 5 -> { expanded.clear(); rebuildRows(); }
                case 6 -> { inputBox.setValue(""); outputBox.setValue(""); }
                case 7 -> {
                    dropTools = true; outputPolicy = inputMode = outputMode = groupingMode = 0;
                    selectedOnly = false; sourceView = false; pinned = null; expanded.clear(); multiplierBox.setValue("1");
                    inputBox.setValue(""); outputBox.setValue(""); optionsChanged(); refresh();
                }
                default -> { }
            }
        });
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        iconHits.clear();
        g.fill(0, 0, width, height, 0xCC000000);
        WorkbenchTheme.rounded(g, left, top, panelWidth, panelHeight, BG);
        text(g, tr("title").copy().append(" / ").append(plan.machineName()), left + 10, top + 10, panelWidth - 86, TEXT);
        if (panelHeight >= 230) g.fill(left + 10, top + 27, left + panelWidth - 10, top + 28, LINE);
        searchSurface(g, inputBox); searchSurface(g, outputBox);
        g.fill(split, bodyTop - 27, split + 1, bodyBottom, LINE);
        WorkbenchTheme.rounded(g, left + 68, top + panelHeight - 45, 48, 19, SURFACE);
        renderList(g, mx, my); renderDetails(g);
        Component status = !ClientNetworkState.hasStatus() ? tr("querying") : !ClientNetworkState.hasNetwork() ? tr("no_network") : tr("stock", ClientNetworkState.blankPatterns(), eligibleCount);
        if (ProcessingMultiplier.parse(multiplierText) == 0) status = tr("multiplier_invalid", ProcessingMultiplier.MAX);
        g.fill(left + 10, bodyBottom + 3, left + panelWidth - 10, bodyBottom + 4, LINE);
        text(g, status, left + 124, top + panelHeight - 39, panelWidth - 260, ClientNetworkState.hasNetwork() && ClientNetworkState.blankPatterns() >= eligibleCount ? DIM : WorkbenchTheme.WARNING);
        Component summary = notice.getString().isEmpty() ? tr("scope_counts", visible.size(), plan.entries().size(), selectedCount, hiddenSelected, skippedCount) : notice;
        text(g, summary, left + 10, top + panelHeight - 17, panelWidth - 20, DIM);
        super.render(g, mx, my, partial);
        if (choice != null) { choice.render(g, mx, my); return; }
        if (over(mx, my, left + 10, top + panelHeight - 20, left + panelWidth - 10, top + panelHeight - 5))
            g.renderComponentTooltip(font, List.of(summary, tr("scope_help")), mx, my);
        if (over(mx, my, left + 124, top + panelHeight - 45, left + panelWidth - 134, top + panelHeight - 22)) g.renderComponentTooltip(font, List.of(status), mx, my);
        if (inputBox.isMouseOver(mx, my) || outputBox.isMouseOver(mx, my)) {
            boolean exact = inputBox.isMouseOver(mx, my) ? exactInput != null : exactOutput != null;
            g.renderComponentTooltip(font, exact ? List.of(tr("exact_filter"), tr("search_help")) : List.of(tr("search_help")), mx, my);
        }
        if (multiplierBox.isMouseOver(mx, my)) g.renderComponentTooltip(font, List.of(tr("multiplier_help", ProcessingMultiplier.MAX)), mx, my);
        for (var child : children()) if (child instanceof Action a && a.isHovered())
            g.renderComponentTooltip(font, List.of(a.getMessage(), tr(a == tools ? "tools_help" : a == multiplier ? "multiplier_help" : a == more ? "more_help" : a == grouping ? "group_help" : "option_help", ProcessingMultiplier.MAX)), mx, my);
        IconHit hit = iconAt(mx, my);
        if (hit != null) { g.renderComponentTooltip(font, iconTooltip(hit), mx, my); return; }
        if (over(mx, my, left + 10, bodyTop, split - 12, bodyBottom)) {
            int index = (int) ((my - bodyTop + listScroll) / ROW);
            if (index < rows.size()) {
                RecipeGroups.Row row = rows.get(index);
                g.renderComponentTooltip(font, row.header() ? List.of(row.group().key().label(), tr("group_hint", row.group().entries().size())) : rowTooltip(row.entry()), mx, my);
            }
        } else if (over(mx, my, split + 4, bodyTop, left + panelWidth - 18, bodyBottom)) {
            int index = (int) ((my - bodyTop + detailScroll) / 18);
            List<DetailLine> hoveredDetails = !referenceDetails.isEmpty() && mx < (split + left + panelWidth) / 2 ? referenceDetails : details;
            if (index >= 0 && index < hoveredDetails.size()) {
                DetailLine line = hoveredDetails.get(index);
                g.renderComponentTooltip(font, List.of(line.text), mx, my);
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
        for (int n = first; n < Math.min(rows.size(), first + count); n++) {
            RecipeGroups.Row row = rows.get(n);
            int y = bodyTop + n * ROW - (int) listScroll;
            if (row.header()) {
                var group = row.group();
                int chosen = groupSelected.getOrDefault(group, 0);
                boolean all = chosen == group.entries().size();
                WorkbenchTheme.rounded(g, x, y, right - x, ROW - 2, SURFACE);
                checkbox(g, x + 4, y + 13, all);
                if (!all && chosen > 0) g.fill(x + 6, y + 17, x + 11, y + 18, DIM);
                g.drawString(font, expanded.contains(group.key().id()) ? "▾" : "▸", x + 18, y + 13, DIM, false);
                text(g, group.key().label(), x + 29, y + 7, right - x - 34, TEXT);
                text(g, tr("group_size", chosen, group.entries().size()), x + 29, y + 21, right - x - 34, DIM);
                continue;
            }
            RecipePlan.Entry e = row.entry();
            if (e == focused || over(mx, my, x, y, right, y + ROW)) WorkbenchTheme.rounded(g, x, y, right - x, ROW - 1, e == focused ? WorkbenchTheme.HOVER : SURFACE);
            checkbox(g, x + 4, y + 13, e.isSelected());
            String flag = existing.contains(e.recipeId()) ? tr("stored_badge").getString() : !skipReason(e).isEmpty() ? "!" : e == pinned ? tr("reference").getString()
                    : (e.hasNeverConsumedInput() ? "NC " : "") + (e.hasProbabilisticInput() ? "IN% " : "") + (e.hasProbabilisticOutput() ? "OUT%" : "");
            int flagWidth = flag.isEmpty() ? 0 : font.width(flag) + 5;
            text(g, e.label(), x + 19, y + 3, right - x - 23 - flagWidth, e.isSelected() ? TEXT : DIM);
            if (!flag.isEmpty()) g.drawString(font, flag, right - flagWidth, y + 3, DIM, false);
            int inputMax = right - x >= 220 ? 3 : 2, outputMax = right - x >= 220 ? 2 : 1;
            List<Ingredient> inputs = new ArrayList<>(e.inputs()); inputs.addAll(e.catalysts());
            int iconX = drawIngredients(g, inputs, x + 19, y + 17, inputMax, 0);
            g.drawString(font, ">", iconX + 1, y + 22, DIM, false);
            drawIngredients(g, e.outputs(), iconX + 10, y + 17, outputMax, 1);
        }
        if (visible.isEmpty()) text(g, tr("empty"), x + 6, bodyTop + 12, right - x - 12, DIM);
        g.disableScissor(); scrollbar(g, split - 10, listScroll, rows.size() * ROW);
    }
    private void checkbox(GuiGraphics g, int x, int y, boolean checked) {
        WorkbenchTheme.rounded(g, x, y, 9, 9, checked ? ACCENT : LINE);
        if (checked) { g.fill(x + 2, y + 4, x + 4, y + 6, BG); g.fill(x + 4, y + 2, x + 6, y + 6, BG); }
    }
    private int drawIngredients(GuiGraphics g, List<Ingredient> ingredients, int x, int y, int max, int side) {
        for (int i = 0; i < Math.min(max, ingredients.size()); i++) {
            Ingredient ingredient = ingredients.get(i);
            visuals.draw(g, ingredient.spec(), x, y);
            iconHits.add(new IconHit(x, y, ingredient.spec(), side, ingredient)); x += 18;
        }
        if (ingredients.size() > max) { String extra = "+" + (ingredients.size() - max); g.drawString(font, extra, x, y + 5, DIM, false); x += font.width(extra) + 3; }
        return x;
    }
    private void renderDetails(GuiGraphics g) {
        int x = split + 12, right = left + panelWidth - 18;
        if (!referenceDetails.isEmpty()) {
            int middle = (split + left + panelWidth) / 2;
            renderDetailColumn(g, referenceDetails, x, middle - 5);
            g.fill(middle, bodyTop, middle + 1, bodyBottom, LINE);
            renderDetailColumn(g, details, middle + 6, right);
        } else renderDetailColumn(g, details, x, right);
        scrollbar(g, left + panelWidth - 15, detailScroll, detailContentHeight());
    }
    private int detailContentHeight() { return Math.max(details.size(), referenceDetails.size()) * 18; }
    private void renderDetailColumn(GuiGraphics g, List<DetailLine> lines, int x, int right) {
        g.enableScissor(x, bodyTop, right, bodyBottom);
        int first = (int) detailScroll / 18, count = Math.max(1, (bodyBottom - bodyTop) / 18 + 2);
        for (int n = first; n < Math.min(lines.size(), first + count); n++) {
            DetailLine line = lines.get(n); int y = bodyTop + n * 18 - (int) detailScroll;
            if (line.spec != null) {
                visuals.draw(g, line.spec, x, y);
                iconHits.add(new IconHit(x, y, line.spec, line.side, null));
            }
            text(g, line.text, x + (line.spec == null ? 0 : 20), y + 4, right - x - (line.spec == null ? 0 : 20), line.color);
        }
        g.disableScissor();
    }
    private IconHit iconAt(double mx, double my) {
        if (my < bodyTop || my >= bodyBottom) return null;
        for (int i = iconHits.size() - 1; i >= 0; i--) {
            IconHit hit = iconHits.get(i);
            if (over(mx, my, hit.x, hit.y, hit.x + 16, hit.y + 16)) return hit;
        }
        return null;
    }
    private List<Component> iconTooltip(IconHit hit) {
        List<Component> lines = new ArrayList<>();
        if (hit.spec.isItem()) {
            try { lines.addAll(visuals.item(hit.spec).getTooltipLines(minecraft.player, net.minecraft.world.item.TooltipFlag.Default.NORMAL)); }
            catch (RuntimeException ignored) { lines.add(name(hit.spec)); }
        } else { lines.add(name(hit.spec)); lines.add(Component.literal(hit.spec.id().toString())); }
        lines.add(Component.literal(hit.spec.amount() + (hit.spec.isItem() ? " × " : " mB ")).append(name(hit.spec)));
        if (hit.ingredient != null) {
            if (hit.ingredient.neverConsumed()) lines.add(tr("not_consumed"));
            else if (hit.ingredient.probabilistic()) lines.add(tr(hit.side == 0 ? "chance_consumed" : "byproduct").copy().append(" " + hit.ingredient.chanceText()));
        }
        lines.add(tr("click_filter"));
        return lines;
    }
    private List<Component> rowTooltip(RecipePlan.Entry entry) {
        List<Component> lines = new ArrayList<>(List.of(entry.label(), Component.literal(entry.recipeId().toString()), tr("inputs")));
        for (Ingredient i : entry.inputs().stream().limit(4).toList()) lines.add(ingredientSummary(i));
        if (entry.inputs().size() > 4) lines.add(tr("more_slots", entry.inputs().size() - 4));
        if (!entry.catalysts().isEmpty()) lines.add(tr("required_tools"));
        lines.add(tr("outputs"));
        for (Ingredient i : entry.outputs().stream().limit(3).toList()) lines.add(ingredientSummary(i));
        if (entry.outputs().size() > 3) lines.add(tr("more_slots", entry.outputs().size() - 3));
        String reason = skipReason(entry);
        if (!reason.isEmpty()) lines.add(tr(reason));
        lines.add(tr("row_help"));
        return lines;
    }
    private Component ingredientSummary(Ingredient ingredient) {
        return Component.literal(ingredient.spec().amount() + (ingredient.spec().isItem() ? " × " : " mB "))
                .append(name(ingredient.spec())).append(ingredient.neverConsumed() ? " [NC]" : ingredient.probabilistic() ? " [" + ingredient.chanceText() + "]" : "");
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
        if (over(mx, my, left + 8, bodyTop, split, bodyBottom)) { listScroll = clamp(listScroll - delta * ROW * 2, rows.size() * ROW); return true; }
        if (over(mx, my, split, bodyTop, left + panelWidth, bodyBottom)) { detailScroll = clamp(detailScroll - delta * 36, detailContentHeight()); return true; }
        return super.mouseScrolled(mx, my, delta);
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (choice != null) {
            Choice current = choice; choice = null;
            if (button == 0) current.click(mx, my);
            return true;
        }
        if (button == 0 && my >= bodyTop && my < bodyBottom) {
            IconHit hit = iconAt(mx, my);
            if (hit != null && hit.side >= 0) {
                if (hit.side == 0) { inputBox.setValue("&" + hit.spec.id()); exactInput = hit.spec; setFocused(inputBox); }
                else { outputBox.setValue("&" + hit.spec.id()); exactOutput = hit.spec; setFocused(outputBox); }
                searchDelay = 3; return true;
            }
            dragList = mx >= split - 12 && mx < split; dragDetails = mx >= left + panelWidth - 18 && mx < left + panelWidth - 8;
            if (dragList || dragDetails) { dragTo(my); return true; }
            if (!filtering && over(mx, my, left + 10, bodyTop, split - 12, bodyBottom)) {
                int n = (int) ((my - bodyTop + listScroll) / ROW);
                if (n < rows.size()) {
                    RecipeGroups.Row row = rows.get(n);
                    if (row.header()) {
                        if (mx < left + 27 && !checking) {
                            boolean select = groupSelected.getOrDefault(row.group(), 0) < row.group().entries().size();
                            row.group().entries().forEach(e -> e.setSelected(select));
                            if (selectedOnly) refresh(); else recount();
                        } else {
                            String id = row.group().key().id();
                            if (!expanded.remove(id)) { if (groupingMode >= 2) expanded.clear(); expanded.add(id); }
                            rebuildRows();
                        }
                    } else {
                        RecipePlan.Entry e = row.entry(); focus(e);
                        if (mx < left + 27 && !checking) { e.toggle(); if (selectedOnly) refresh(); else recount(); }
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
    private void dragTo(double my) {
        int h = Math.max(1, bodyBottom - bodyTop), content = dragList ? rows.size() * ROW : detailContentHeight(), thumb = thumbHeight(content);
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
    @Override public void onClose() { if (jeiReturn != null) jeiReturn.restore(); minecraft.setScreen(parent); }
    @Override public void removed() {
        ClientGenerationFeedback.setListener(null); InspectionFeedback.setListener(null);
        if (checking && minecraft.getConnection() != null) {
            PacketHandler.sendToServer(new io.github.sexileman.eae2pattern.net.C2SGeneratePatternsPacket(existingRequest, 0, 1, List.of(), 3));
            checking = false; existingRequest = -1; notice = tr("check_cancelled");
        }
        if (!navigatingToJei && jeiReturn != null) jeiReturn.restore();
        WorkbenchPreferences.save(dropTools, outputPolicy, ProcessingMultiplier.parse(multiplierText), groupingMode);
    }
    @Override public boolean isPauseScreen() { return false; }
    private record DetailLine(Component text, StackSpec spec, int color, int side) {
        DetailLine(Component text, StackSpec spec, int color) { this(text, spec, color, -1); }
    }
    private record IconHit(int x, int y, StackSpec spec, int side, Ingredient ingredient) {}
    private void choose(Action anchor, String prefix, int count, int selectedIndex, IntConsumer select) {
        List<Component> labels = new ArrayList<>();
        for (int i = 0; i < count; i++) labels.add(tr(prefix + i));
        choice = new Choice(anchor, labels, selectedIndex, select);
    }

    private final class Choice {
        final List<Component> labels;
        final IntConsumer select;
        final int x, y, w, h, selectedIndex;
        final PopupLayout bounds;
        int cursor;
        Choice(Action anchor, List<Component> labels, int selectedIndex, IntConsumer select) {
            this.labels = labels; this.select = select; this.selectedIndex = selectedIndex;
            cursor = Math.max(0, selectedIndex);
            bounds = PopupLayout.measure(anchor.getX(), anchor.getY(), anchor.getWidth(), anchor.getHeight(),
                    labels.stream().mapToInt(font::width).max().orElse(80) + 28, labels.size(), width, height);
            w = bounds.width(); h = bounds.height(); x = bounds.x(); y = bounds.y();
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
            int index = bounds.indexAt(mx, my);
            if (index >= 0) select.accept(index);
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
