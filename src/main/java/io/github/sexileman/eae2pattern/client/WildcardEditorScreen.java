package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.compat.jei.JeiRuntimeHolder;
import io.github.sexileman.eae2pattern.pattern.*;
import io.github.sexileman.eae2pattern.wildcard.*;
import io.github.sexileman.eae2pattern.net.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Independent wildcard workflow: rules, virtualized recipe preview, encoding settings. */
public final class WildcardEditorScreen extends Screen implements GenerationListener {
    private final RecipePlan plan;
    private final Screen parent;
    private final boolean savedCopy;
    public record HeldTarget(net.minecraft.world.InteractionHand hand, ItemStack snapshot) {
        public HeldTarget { snapshot = snapshot.copy(); }
    }
    private HeldTarget heldTarget;
    private boolean writingHeld;
    private int heldRefreshTicks;
    private final Set<ResourceLocation> selected = new HashSet<>();
    private final List<RecipePlan.Entry> matches = new ArrayList<>(), working = new ArrayList<>();
    private final List<Control> controls = new ArrayList<>();
    private final StackVisuals visuals = new StackVisuals();
    private final List<Hit> hits = new ArrayList<>();
    private EditBox[] boxes;
    private String[] query = {"", "", "", ""};
    private int tab, x, y, w, h, split, listTop, listBottom, cursor, delay, multiplier = 1, outputPolicy;
    private int inScope, outScope, previewRequest = -1, previewDelay;
    private boolean inAll, outAll, searchTools = true, dropTools = true, filtering, sending, sourceView, dragging, detailDragging;
    private double scroll, detailScroll;
    private WildcardRules rules;
    private RecipePlan.Entry focus;
    private PatternSpec preview;
    private Component previewError;
    private final Map<ResourceLocation, Component> previewErrors = new HashMap<>();
    private Component status = Component.empty();
    private Control generate;
    private Control generateNetwork;
    private static final int TEXT = WorkbenchTheme.TEXT, DIM = WorkbenchTheme.DIM, ROW = 42;

    public WildcardEditorScreen(RecipePlan plan, Screen parent, CompoundTag state, Set<ResourceLocation> included, boolean savedCopy) {
        this(plan, parent, state, included, savedCopy, null);
    }
    public WildcardEditorScreen(RecipePlan plan, Screen parent, CompoundTag state, Set<ResourceLocation> included, boolean savedCopy, HeldTarget heldTarget) {
        super(tr("title")); this.plan = plan; this.parent = parent; this.savedCopy = savedCopy; this.heldTarget = heldTarget;
        if (included == null) for (var e : plan.entries()) { if (e.isSelected()) selected.add(e.recipeId()); }
        else selected.addAll(included);
        if (state != null && !state.isEmpty()) {
            query = new String[]{state.getString("in_include"), state.getString("in_exclude"), state.getString("out_include"), state.getString("out_exclude")};
            multiplier = ProcessingMultiplier.normalize(state.getInt("multiplier")); outputPolicy = state.getInt("output_policy");
            inAll = state.getBoolean("in_all"); outAll = state.getBoolean("out_all");
            inScope = state.getInt("in_scope"); outScope = state.getInt("out_scope");
            searchTools = state.getBoolean("search_tools"); dropTools = state.getBoolean("drop_tools");
        }
    }
    public static Component tr(String key, Object... args) { return Component.translatable("eae2_pattern.editor." + key, args); }
    public static void openStored(ItemStack stack) {
        openStored(stack, net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    public static void openStored(ItemStack stack, net.minecraft.world.InteractionHand hand) {
        var runtime = JeiRuntimeHolder.get(); var mc = net.minecraft.client.Minecraft.getInstance();
        if (runtime == null) return;
        CompoundTag root = stack.getTag() == null ? new CompoundTag() : stack.getTag().copy();
        CompoundTag state = root.getCompound("editor");
        try { if (!state.isEmpty()) state = WildcardEditorState.clean(state); } catch (IllegalArgumentException ignored) { state = new CompoundTag(); }
        final CompoundTag savedState = state;
        final HeldTarget target = MachineWildcard.isOwnItem(stack) ? new HeldTarget(hand, stack) : null;
        String category = state.getString("category");
        if (category.isEmpty()) category = root.getCompound(MachineWildcardData.KEY).getString("machine");
        final String id = category;
        var categories = runtime.getRecipeManager().createRecipeCategoryLookup().get().toList();
        var found = categories.stream().filter(c -> c.getRecipeType().getUid().toString().equals(id)).findFirst();
        Set<ResourceLocation> ids = new HashSet<>();
        try {
            var rows = MachineWildcardData.readRecipes(root.getCompound(MachineWildcardData.KEY));
            for (int i = 0; i < rows.size(); i++) ids.add(new ResourceLocation(rows.getCompound(i).getString("recipe")));
        } catch (RuntimeException ignored) { }
        if (found.isPresent()) mc.setScreen(new CollectingScreen(found.get(), mc.screen,
                plan -> mc.setScreen(new WildcardEditorScreen(plan, null, savedState, ids.isEmpty() ? null : ids, true, target))));
        else mc.setScreen(new WildcardMachineScreen(categories, mc.screen, target));
    }
    @Override protected void init() {
        controls.clear(); w = Math.min(900, width - 12); h = Math.min(530, height - 12); x = (width - w) / 2; y = (height - h) / 2;
        split = x + w * 3 / 5; listTop = y + 76; listBottom = y + h - 36;
        button(x + w - 58, y + 7, 48, -1, () -> onClose(), "back");
        for (int i = 0; i < 3; i++) { final int n = i; button(x + 10 + i * 94, y + 30, 88, -1, () -> { tab = n; sync(); }, "tab_" + i); }
        boxes = new EditBox[4]; int col = (w - 34) / 2, first = h < 220 ? 57 : 70;
        for (int i = 0; i < 4; i++) {
            final int n = i; int side = i / 2;
            boxes[i] = addRenderableWidget(new WorkbenchSearchBox(font, x + 16 + side * (col + 12), y + first + (i % 2) * 26, col - 10, tr("query_" + i), tr("query_" + i)));
            boxes[i].setMaxLength(256); boxes[i].setBordered(false); boxes[i].setValue(query[i]);
            boxes[i].setResponder(v -> { query[n] = v; changedRules(); });
        }
        int opts = y + first + 52;
        button(x + 10, opts, col, 0, () -> { inAll = !inAll; changedRules(); }, "input_match");
        button(x + 22 + col, opts, col, 0, () -> { outAll = !outAll; changedRules(); }, "output_match");
        button(x + 10, y + 53, 62, 1, () -> { matches.forEach(e -> selected.add(e.recipeId())); updateControls(); }, "select_all");
        button(x + 78, y + 53, 62, 1, () -> { matches.forEach(e -> selected.remove(e.recipeId())); updateControls(); }, "select_none");
        int modeWidth = Math.max(48, Math.min(82, w - (split - x) - 70));
        button(split + 10, y + 53, modeWidth, 1, () -> { sourceView = !sourceView; detailScroll = 0; updateControls(); }, "source");
        button(split + 16 + modeWidth, y + 53, 44, 1, () -> { if (focus != null) RecipeNavigation.show(plan, focus); }, "jei");
        button(x + 10, y + 65, Math.min(240, w / 2 - 15), 2, () -> { dropTools = !dropTools; changedPolicy(); }, "drop_tools");
        button(x + 10, y + 90, Math.min(240, w / 2 - 15), 2, () -> { outputPolicy = (outputPolicy + 1) % 3; changedPolicy(); }, "output_policy");
        button(x + 10, y + 115, 32, 2, () -> { multiplier = ProcessingMultiplier.halveAmount(multiplier); changedPolicy(); }, "halve");
        button(x + 48, y + 115, 56, 2, () -> { multiplier = 1; changedPolicy(); }, "multiplier");
        button(x + 110, y + 115, 32, 2, () -> { multiplier = ProcessingMultiplier.doubleAmount(multiplier); changedPolicy(); }, "double");
        button(x + w / 2 + 5, y + 65, w / 2 - 15, 2, () -> { inScope = (inScope + 1) % 3; changedRules(); }, "input_scope");
        button(x + w / 2 + 5, y + 90, w / 2 - 15, 2, () -> { outScope = (outScope + 1) % 3; changedRules(); }, "output_scope");
        button(x + w / 2 + 5, y + 115, w / 2 - 15, 2, () -> { searchTools = !searchTools; changedRules(); }, "search_tools");
        generateNetwork = heldTarget != null && h > 210
                ? button(x + 10, y + 140, w - 20, 2, () -> generate(false), "save_copy_network") : null;
        generate = button(x + w - 140, y + h - 27, 130, -1, this::generate, heldTarget != null ? "write_held" : savedCopy ? "save_copy" : "generate");
        ClientGenerationFeedback.setListener(this); InspectionFeedback.setListener(this::inspected);
        ClientNetworkState.reset();
        PacketHandler.sendToServer(new C2SQueryNetworkStatusPacket(0)); startFilter(); sync();
    }
    private Control button(int bx, int by, int width, int page, Runnable action, String key) {
        Control c = new Control(bx, by, width, page, action, key); controls.add(c); return addRenderableWidget(c);
    }
    private void sync() {
        for (int i = 0; i < 4; i++) boxes[i].visible = tab == 0;
        for (var c : controls) c.visible = c.page < 0 || c.page == tab;
        updateControls();
    }
    private int chosen() { int n = 0; for (var e : matches) if (selected.contains(e.recipeId()) && e.canEncode(outputPolicy, dropTools) && !previewErrors.containsKey(e.recipeId())) n++; return n; }
    private void updateControls() {
        int chosen = chosen();
        for (var c : controls) {
            c.active = !sending;
            c.setMessage(switch (c.key) {
                case "input_match" -> tr(inAll ? "input_all" : "input_recipe");
                case "output_match" -> tr(outAll ? "output_all" : "output_recipe");
                case "input_scope" -> tr("input_scope_value", tr("scope_" + inScope));
                case "output_scope" -> tr("output_scope_value", tr("scope_" + outScope));
                case "drop_tools" -> tr(dropTools ? "tools_machine" : "tools_pattern");
                case "search_tools" -> tr(searchTools ? "search_tools_yes" : "search_tools_no");
                case "output_policy" -> tr("policy_value", tr("policy_" + outputPolicy));
                case "multiplier" -> Component.literal("×" + multiplier);
                case "source" -> tr(sourceView ? "source" : "final");
                default -> tr(c.key);
            });
            if (c.key.equals("halve")) c.active &= multiplier > 1;
            if (c.key.equals("double")) c.active &= multiplier < ProcessingMultiplier.MAX;
            if (c.key.equals("select_all") || c.key.equals("select_none")) c.active &= !filtering && delay == 0;
        }
        boolean ready = !sending && !filtering && delay == 0 && heldRefreshTicks == 0 && chosen > 0;
        boolean networkReady = ready && ClientNetworkState.hasNetwork() && ClientNetworkState.blankPatterns() >= (chosen + 2047) / 2048;
        generate.active = heldTarget == null ? networkReady : ready && chosen <= MachineWildcardData.MAX_RECIPES
                && minecraft.player != null && MachineWildcard.isOwnItem(minecraft.player.getItemInHand(heldTarget.hand()));
        if (generateNetwork != null) generateNetwork.active = networkReady;
        for (var box : boxes) box.active = !sending;
    }
    private void changedRules() { delay = 5; filtering = true; preview = null; previewRequest = -1; updateControls(); }
    private void changedPolicy() { previewErrors.clear(); previewError = null; preview = null; previewRequest = -1; previewDelay = 4; updateControls(); }
    private void startFilter() {
        rules = new WildcardRules(WildcardRules.Side.of(query[0], query[1], inAll, inScope), WildcardRules.Side.of(query[2], query[3], outAll, outScope));
        working.clear(); cursor = 0; filtering = true;
    }
    @Override public void tick() {
        for (var box : boxes) box.tick();
        if (heldRefreshTicks > 0 && --heldRefreshTicks == 0 && heldTarget != null && minecraft.player != null) {
            var current = minecraft.player.getItemInHand(heldTarget.hand());
            if (MachineWildcard.isOwnItem(current)) heldTarget = new HeldTarget(heldTarget.hand(), current);
        }
        if (delay > 0 && --delay == 0) startFilter();
        if (filtering && delay == 0) {
            long end = System.nanoTime() + 3_000_000; int n = 0;
            while (cursor < plan.entries().size() && n++ < 96 && System.nanoTime() < end) {
                var e = plan.entries().get(cursor++);
                if (e.kind() == PatternKind.PROCESSING && rules.matches(e, searchTools)) working.add(e);
            }
            if (cursor == plan.entries().size()) {
                matches.clear(); matches.addAll(working); working.clear(); filtering = false; scroll = 0;
                if (!matches.contains(focus)) focus(matches.isEmpty() ? null : matches.get(0));
                else previewDelay = 4;
            }
        }
        if (!sending && !filtering && delay == 0 && previewDelay > 0 && --previewDelay == 0 && focus != null) {
            var spec = focus.specForPolicy(multiplier, outputPolicy, dropTools);
            if (spec != null) previewRequest = PatternRequestSender.send(List.of(spec), 2);
            else previewError = tr("policy_empty");
        }
        updateControls();
    }
    private void focus(RecipePlan.Entry entry) { focus = entry; detailScroll = 0; previewError = null; preview = null; previewRequest = -1; previewDelay = 4; }
    private void inspected(S2CInspectionPacket response) {
        if (response.requestId() != previewRequest || response.operation() != 2) return;
        if (response.success() && !response.preview().isEmpty()) preview = response.preview().get(0);
        else {
            status = response.details().isEmpty() ? tr("preview_failed") : Component.literal(response.details().get(0));
            previewError = status; if (focus != null) previewErrors.put(focus.recipeId(), status);
        }
        updateControls();
    }
    private CompoundTag state() {
        CompoundTag s = new CompoundTag(); String[] keys = {"in_include", "in_exclude", "out_include", "out_exclude"};
        for (int i = 0; i < 4; i++) s.putString(keys[i], query[i]); s.putString("category", plan.machineId().toString());
        s.putBoolean("in_all", inAll); s.putBoolean("out_all", outAll); s.putBoolean("drop_tools", dropTools); s.putBoolean("search_tools", searchTools);
        s.putInt("in_scope", inScope); s.putInt("out_scope", outScope); s.putInt("output_policy", outputPolicy); s.putInt("multiplier", multiplier);
        return WildcardEditorState.clean(s);
    }
    private void generate() {
        if (!generate.active) return;
        generate(heldTarget != null);
    }
    private void generate(boolean held) {
        if (sending || filtering || delay != 0) return;
        if (!held && (generateNetwork != null ? !generateNetwork.active : !generate.active)) return;
        List<PatternSpec> specs = new ArrayList<>();
        for (var e : matches) if (selected.contains(e.recipeId()) && !previewErrors.containsKey(e.recipeId())) { var spec = e.specForPolicy(multiplier, outputPolicy, dropTools); if (spec != null) specs.add(spec); }
        try {
            CompoundTag rules = state();
            int operation = 4;
            if (held) {
                rules.putByteArray("held_fingerprint", HeldWildcardFingerprint.visible(heldTarget.snapshot()));
                operation = heldTarget.hand() == net.minecraft.world.InteractionHand.MAIN_HAND ? 5 : 6;
            }
            PatternRequestSender.send(specs, operation, rules); writingHeld = held; sending = true; status = tr("preparing");
        }
        catch (IllegalArgumentException e) { status = Component.literal(e.getMessage()); }
        updateControls();
    }
    @Override public void onGenerationFinished(GenerationReport report) {
        sending = false; status = ClientGenerationFeedback.describe(report);
        if (writingHeld && report.isSuccess()) { status = tr("held_saved"); heldRefreshTicks = 2; }
        if (!report.details().isEmpty()) status = status.copy().append(" · ").append(report.details().get(0));
        PacketHandler.sendToServer(new C2SQueryNetworkStatusPacket(0)); updateControls();
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        hits.clear(); g.fill(0, 0, width, height, 0xCC000000); g.fill(x, y, x + w, y + h, WorkbenchTheme.BACKGROUND);
        label(g, title.copy().append(" / ").append(plan.machineName()), x + 10, y + 10, w - 80, TEXT, 1);
        g.fill(x + 10, y + 26, x + w - 10, y + 27, WorkbenchTheme.LINE);
        if (tab == 0) {
            if (h >= 220) {
                label(g, tr("input_rules"), x + 12, y + 55, w / 2 - 20, TEXT, .85f);
                label(g, tr("output_rules"), x + w / 2 + 6, y + 55, w / 2 - 20, TEXT, .85f);
            }
            for (var box : boxes) WorkbenchTheme.control(g, box.getX() - 5, box.getY() - 4, box.getWidth() + 10, 19, WorkbenchTheme.INPUT, WorkbenchTheme.LINE);
            if (h > 250) wrapped(g, tr("rule_help"), x + 14, y + 160, w - 28, DIM, .8f);
        } else if (tab == 1) renderRecipes(g, mx, my);
        else if (h > 210) wrapped(g, tr(heldTarget == null ? "settings_help" : "held_settings_help"), x + 14, y + (heldTarget == null ? 147 : 169), w - 28, DIM, .8f);
        g.fill(x + 10, y + h - 34, x + w - 10, y + h - 33, WorkbenchTheme.LINE);
        label(g, filtering ? tr("filtering", cursor, plan.entries().size()) : heldTarget != null ? tr("summary_held", matches.size(), chosen()) : tr("summary", matches.size(), chosen(), ClientNetworkState.blankPatterns()), x + 10, y + h - 24, w - 160, DIM, .8f);
        if (!status.getString().isEmpty()) label(g, status, x + 10, y + h - 13, w - 160, WorkbenchTheme.WARNING, .7f);
        super.render(g, mx, my, partial);
        for (var hit : hits) if (tab == 1 && my >= listTop && my < listBottom && mx >= hit.x && mx < hit.x + 18 && my >= hit.y && my < hit.y + 18) {
            var lines = new ArrayList<Component>();
            try {
                if (hit.spec.isItem()) lines.addAll(visuals.item(hit.spec).getTooltipLines(minecraft.player,
                        minecraft.options.advancedItemTooltips ? net.minecraft.world.item.TooltipFlag.Default.ADVANCED : net.minecraft.world.item.TooltipFlag.Default.NORMAL));
                else lines.add(visuals.name(hit.spec));
            } catch (RuntimeException malformedTooltip) { lines.add(visuals.name(hit.spec)); }
            lines.add(Component.literal(hit.spec.amount() + (hit.spec.isItem() ? "" : " mB")));
            lines.add(hit.presentation.description()); g.renderComponentTooltip(font, lines, mx, my); break;
        }
        for (int i = 0; i < boxes.length; i++) if (boxes[i].visible && boxes[i].isMouseOver(mx, my))
            g.renderComponentTooltip(font, List.of(tr("query_" + i), tr("syntax_help")), mx, my);
        if (generate.isMouseOver(mx, my)) g.renderComponentTooltip(font, List.of(tr(heldTarget == null ? "capacity_help" : "held_capacity_help")), mx, my);
        for (var control : controls) if (control != generate && control.visible && control.isMouseOver(mx, my))
            g.renderComponentTooltip(font, List.of(control.getMessage()), mx, my);
        if (mx >= x + 10 && mx < x + w - 145 && my >= y + h - 30 && my < y + h - 2)
            g.renderComponentTooltip(font, List.of(tr("summary", matches.size(), chosen(), ClientNetworkState.blankPatterns()), status), mx, my);
    }
    private void renderRecipes(GuiGraphics g, int mx, int my) {
        g.enableScissor(x + 10, listTop, split - 5, listBottom);
        int first = (int) scroll / ROW, visible = (listBottom - listTop) / ROW + 2;
        for (int i = first; i < Math.min(matches.size(), first + visible); i++) {
            var e = matches.get(i); int ry = listTop + i * ROW - (int) scroll;
            if (e == focus) g.fill(x + 10, ry, split - 8, ry + ROW - 2, WorkbenchTheme.HOVER);
            g.fill(x + 14, ry + 12, x + 22, ry + 20, selected.contains(e.recipeId()) ? WorkbenchTheme.TEXT : WorkbenchTheme.LINE);
            label(g, e.label(), x + 29, ry + 2, split - x - 47, TEXT, 1);
            var in = new ArrayList<>(e.inputs()); in.addAll(e.catalysts()); int ix = x + 29;
            int maxIn = split - x > 230 ? 3 : 2;
            ix = strip(g, in, ix, ry + 18, maxIn, 0);
            label(g, Component.literal("→"), ix + 4, ry + 22, 14, DIM, 1);
            strip(g, e.outputs(), ix + 26, ry + 18, 1, 1);
        }
        g.disableScissor();
        scrollbar(g, split - 7, listTop, listBottom, scroll, matches.size() * ROW);
        g.fill(split, listTop, split + 1, listBottom, WorkbenchTheme.LINE);
        if (focus == null) return;
        g.enableScissor(split + 8, listTop, x + w - 10, listBottom);
        int dy = listTop - (int) detailScroll, dx = split + 10, room = x + w - dx - 14;
        label(g, focus.label(), dx, dy + 2, room, TEXT, 1); dy += 20;
        List<Ingredient> in = new ArrayList<>(), out = new ArrayList<>();
        if (sourceView) { in.addAll(focus.inputs()); in.addAll(focus.catalysts()); out.addAll(focus.outputs()); }
        else if (preview != null) {
            for (var s : preview.inputs()) in.add(new Ingredient(s, 1, true));
            for (var s : preview.outputs()) out.add(Ingredient.produced(s, 1));
        } else { label(g, previewError == null ? tr("preview_wait") : previewError, dx, dy, room, previewError == null ? DIM : WorkbenchTheme.WARNING, .8f); }
        dy = materialRows(g, in, dx, dy, room, 0); materialRows(g, out, dx, dy, room, 1);
        g.disableScissor();
        int content = detailHeight(); scrollbar(g, x + w - 9, listTop, listBottom, detailScroll, content);
    }
    private int strip(GuiGraphics g, List<Ingredient> slots, int px, int py, int max, int side) {
        for (int i = 0; i < Math.min(max, slots.size()); i++) {
            var item = slots.get(i); var presentation = IngredientPresentation.of(item, side);
            visuals.draw(g, item.spec(), px, py); visuals.drawRule(g, presentation, px, py);
            hits.add(new Hit(px, py, item.spec(), presentation)); px += 22;
        }
        if (slots.size() > max) { String n = "+" + (slots.size() - max); label(g, Component.literal(n), px + 3, py + 5, 35, DIM, .8f); px += font.width(n) + 8; }
        return px;
    }
    private int materialRows(GuiGraphics g, List<Ingredient> ingredients, int dx, int dy, int room, int side) {
        label(g, tr(side == 0 ? "inputs" : "outputs"), dx, dy + 2, room, TEXT, .85f); dy += 17;
        for (var ingredient : ingredients) {
            var p = sourceView ? IngredientPresentation.of(ingredient, side) : IngredientPresentation.encoded(focus, ingredient.spec(), side, dropTools);
            if (dy + 40 >= listTop && dy < listBottom) {
                WorkbenchTheme.slot(g, dx, dy + 2, p); visuals.draw(g, ingredient.spec(), dx + 4, dy + 6); visuals.drawRule(g, p, dx + 4, dy + 6);
                label(g, visuals.name(ingredient.spec()), dx + 30, dy + 3, room - 30, TEXT, 1);
                label(g, Component.literal(ingredient.spec().amount() + (ingredient.spec().isItem() ? "" : " mB")), dx + 30, dy + 16, room - 30, DIM, .8f);
                if (p.kind() != IngredientPresentation.Kind.REGULAR) label(g, p.description(), dx + 30, dy + 27, room - 30, p.color(), .8f);
                hits.add(new Hit(dx + 4, dy + 6, ingredient.spec(), p));
            }
            dy += 40;
        }
        return dy;
    }
    private int detailHeight() { return focus == null ? 0 : 60 + (sourceView ? focus.inputs().size() + focus.catalysts().size() + focus.outputs().size() : preview == null ? 0 : preview.inputs().size() + preview.outputs().size()) * 40; }
    private void scrollbar(GuiGraphics g, int sx, int top, int bottom, double amount, int content) {
        int size = Math.max(1, bottom - top); if (content <= size) return;
        int thumb = Math.max(12, size * size / content), sy = top + (int) ((size - thumb) * amount / Math.max(1, content - size));
        g.fill(sx, top, sx + 3, bottom, WorkbenchTheme.LINE); g.fill(sx, sy, sx + 3, sy + thumb, DIM);
    }
    private void label(GuiGraphics g, Component text, int px, int py, int room, int color, float scale) {
        if (room < 1) return; g.pose().pushPose();
        try { g.pose().translate(px, py, 0); g.pose().scale(scale, scale, 1); g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(text, (int) (room / scale))), 0, 0, color, false); }
        finally { g.pose().popPose(); }
    }
    private void wrapped(GuiGraphics g, Component text, int px, int py, int room, int color, float scale) {
        for (var part : font.split(text, Math.max(1, (int) (room / scale)))) {
            if (py + font.lineHeight * scale > y + h - 36) break;
            g.pose().pushPose(); try { g.pose().translate(px, py, 0); g.pose().scale(scale, scale, 1); g.drawString(font, part, 0, 0, color, false); }
            finally { g.pose().popPose(); } py += 10;
        }
    }
    private static double clamp(double value, int content, int viewport) { return Math.max(0, Math.min(value, Math.max(0, content - viewport))); }
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab != 1 || my < listTop || my > listBottom) return super.mouseScrolled(mx, my, delta);
        if (mx < split) scroll = clamp(scroll - delta * ROW, matches.size() * ROW, listBottom - listTop);
        else detailScroll = clamp(detailScroll - delta * 30, detailHeight(), listBottom - listTop); return true;
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (tab != 1 || my < listTop || my >= listBottom || button != 0) return false;
        if (mx >= split - 11 && mx < split) { dragging = true; drag(my, false); return true; }
        if (mx >= x + w - 13 && mx < x + w) { detailDragging = true; drag(my, true); return true; }
        if (mx >= x + 10 && mx < split - 11) {
            int index = (int) ((my - listTop + scroll) / ROW);
            if (index < matches.size()) { var e = matches.get(index); focus(e); if (mx < x + 26 && !sending) { if (!selected.remove(e.recipeId())) selected.add(e.recipeId()); } updateControls(); return true; }
        }
        return false;
    }
    private void drag(double my, boolean detail) {
        int viewport = listBottom - listTop, content = detail ? detailHeight() : matches.size() * ROW;
        double value = clamp((my - listTop) / Math.max(1, viewport) * content, content, viewport);
        if (detail) detailScroll = value; else scroll = value;
    }
    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (dragging || detailDragging) { drag(my, detailDragging); return true; } return super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) { dragging = detailDragging = false; return super.mouseReleased(mx, my, b); }
    @Override public void removed() { ClientGenerationFeedback.setListener(null); InspectionFeedback.setListener(null); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    private record Hit(int x, int y, StackSpec spec, IngredientPresentation presentation) {}
    private final class Control extends AbstractButton {
        final int page; final Runnable action; final String key;
        Control(int x, int y, int width, int page, Runnable action, String key) { super(x, y, width, 18, tr(key)); this.page = page; this.action = action; this.key = key; }
        @Override public void onPress() { action.run(); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            WorkbenchTheme.control(g, getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused() && active ? WorkbenchTheme.HOVER : WorkbenchTheme.SURFACE, WorkbenchTheme.LINE);
            int tw = font.width(getMessage()); label(g, getMessage(), getX() + Math.max(5, (getWidth() - tw) / 2), getY() + 5, getWidth() - 10, active ? TEXT : DIM, 1);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
