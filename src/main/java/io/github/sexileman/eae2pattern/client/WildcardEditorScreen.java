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
public final class WildcardEditorScreen extends AeToolScreen implements GenerationListener {
    private final RecipePlan plan;
    private final Screen parent;
    private final boolean savedCopy;
    public record HeldTarget(net.minecraft.world.InteractionHand hand, ItemStack snapshot) {
        public HeldTarget { snapshot = snapshot.copy(); }
    }
    private HeldTarget heldTarget;
    private boolean writingHeld;
    private int heldRefreshTicks;
    private final Set<ResourceLocation> storedExclusions=new HashSet<>();
    private final Set<ResourceLocation> selected = new HashSet<>();
    private final List<RecipePlan.Entry> matches = new ArrayList<>(), working = new ArrayList<>();
    private final List<Control> controls = new ArrayList<>();
    private final StackVisuals visuals = new StackVisuals();
    private final List<Hit> hits = new ArrayList<>();
    private EditBox[] boxes;
    private String[] query = {"", "", "", ""};
    private int tab, x, y, w, h, split, listTop, listBottom, cursor, multiplier = 1, outputPolicy;
    private int inScope, outScope, previewRequest = -1, previewDelay;
    private boolean inAll, outAll, searchTools = true, dropTools = true, filtering, sending, sourceView, dragging, detailDragging;
    private double scroll, detailScroll;
    private WildcardRules rules;
    private DynamicRule portableRule;
    private String inForm="",outForm="",material="",auxItem="";
    private final Map<String,StackSpec> inputForms=new TreeMap<>(),outputForms=new TreeMap<>(),auxiliaryItems=new TreeMap<>();
    private boolean dynamic;
    private final boolean dynamicAvailable;
    private Component ruleError;
    private int chosenCount=-1;
    private final Set<ResourceLocation> catalogued=new HashSet<>();
    private final MaterialChoiceCatalog materialCatalog=new MaterialChoiceCatalog();
    private final Map<ResourceLocation,RuleRecipe> sourceRecipes=new HashMap<>();
    private final Set<ResourceLocation> unsupportedSources=new HashSet<>();
    private RecipePlan.Entry focus;
    private PatternSpec preview;
    private Component previewError;
    private final Map<ResourceLocation, Component> previewErrors = new HashMap<>();
    private Component status = Component.empty();
    private final SearchDebounce searchDebounce = new SearchDebounce();
    private CircuitCatalog circuitCatalog;
    private CircuitCondition circuit = CircuitCondition.ALL;
    private CircuitFilterButton circuitButton;
    private final SearchDebounce multiplierDebounce = new SearchDebounce();
    private SettingChoice settingChoice;
    private Control generate;
    private Control generateNetwork;
    private static final int TEXT = WorkbenchTheme.TEXT, DIM = WorkbenchTheme.DIM, ROW = RecipeListRow.HEIGHT;

    public WildcardEditorScreen(RecipePlan plan, Screen parent, CompoundTag state, Set<ResourceLocation> included, boolean savedCopy) {
        this(plan, parent, state, included, savedCopy, null);
    }
    public WildcardEditorScreen(RecipePlan plan, Screen parent, CompoundTag state, Set<ResourceLocation> included, boolean savedCopy, HeldTarget heldTarget) {
        super(tr("title")); this.plan = plan; this.parent = parent; this.savedCopy = savedCopy; this.heldTarget = heldTarget;
        if (included == null) for (var e : plan.entries()) { if (e.isSelected()) selected.add(e.recipeId()); }
        else selected.addAll(included);
        dynamicAvailable=plan.entries().stream().anyMatch(e->e.jeiRecipe()!=null&&(e.jeiRecipe().getClass().getName().equals("com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper")||e.jeiRecipe() instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe));
        dynamic=dynamicAvailable;
        if (state != null && !state.isEmpty()) {
            circuit=CircuitCondition.read(state);
            if(state.contains("dynamic"))dynamic=state.getBoolean("dynamic");inForm=state.getString("in_form");outForm=state.getString("out_form");material=state.getString("material");auxItem=state.getString("aux_item");
            var excluded=state.getList("exclude_ids",8);for(int i=0;i<excluded.size();i++){var id=ResourceLocation.tryParse(excluded.getString(i));if(id!=null){selected.remove(id);storedExclusions.add(id);}}
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
        if(root.getCompound(MachineWildcardData.KEY).getInt("version")==3)state=root.getCompound(MachineWildcardData.KEY).getCompound("rules").copy();
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
        controls.clear(); var frame=ToolViewport.encoding(width,height);w=frame.width();h=frame.height();x=frame.left();y=frame.top();
        split = x + w * 11 / 20; listTop = y + 76; listBottom = y + h - 36;
        button(x + w - 58, y + 4, 48, -1, () -> onClose(), "back");
        if(circuitCatalog==null)circuitCatalog=new CircuitCatalog(plan);
        circuitButton=null;
        if(circuitCatalog.available())circuitButton=addRenderableWidget(new CircuitFilterButton(x+w-151,y+4,87,18,circuitCatalog,()->circuit,
            ()->minecraft.setScreen(new CircuitPickerScreen(this,circuitCatalog,circuit,c->{circuit=c;changedRules();}))));
        for (int i = 0; i < 4; i++) { final int n = i; int tw=Math.min(94,(w-20)/4); button(x + 10 + i * tw, y + 30, tw-6, -1, () -> { tab = n; sync(); }, "tab_" + i); }
        boxes = new EditBox[4]; int col = (w - 34) / 2, first = h < 220 ? 57 : 70;
        for (int i = 0; i < 4; i++) {
            final int n = i; int side = i / 2;
            boxes[i] = addRenderableWidget(new WorkbenchSearchBox(font, x + 16 + side * (col + 12), y + first + (i % 2) * 26, col - 10, tr("query_" + i), tr("query_" + i)));
            boxes[i].setMaxLength(256); boxes[i].setBordered(false); boxes[i].setValue(query[i]);
            boxes[i].setResponder(v -> { if (v.equals(query[n])) return; query[n] = v; changedRules(); });
        }
        int opts = y + first + 52;
        button(x + 10, opts, col, 3, () -> chooseSetting("input_match"), "input_match");
        button(x + 22 + col, opts, col, 3, () -> chooseSetting("output_match"), "output_match");
        int quickY=y+(h<250?57:70);
        button(x+10,quickY,col,0,()->chooseForm(true),"input_form");
        button(x+22+col,quickY,col,0,()->chooseForm(false),"output_form");
        int quickRoom=w-32, materialWidth=quickRoom*34/100, ruleModeWidth=quickRoom*29/100;
        int ruleModeX=x+16+materialWidth, extractX=ruleModeX+ruleModeWidth+6;
        button(x+10,quickY+25,materialWidth,0,this::chooseMaterial,"material");
        button(ruleModeX,quickY+25,ruleModeWidth,0,()->chooseSetting("mode"),"mode");
        button(extractX,quickY+25,x+w-10-extractX,0,this::extractFocused,"extract");
        button(x+10,quickY+50,w-20,0,this::chooseAuxiliary,"auxiliary");
        button(x + 10, y + 53, 62, 1, () -> { matches.forEach(e -> selected.add(e.recipeId())); chosenCount=-1; updateControls(); }, "select_all");
        button(x + 78, y + 53, 62, 1, () -> { matches.forEach(e -> selected.remove(e.recipeId())); chosenCount=-1; updateControls(); }, "select_none");
        int modeWidth = Math.max(36, Math.min(82, w - (split - x) - 70));
        button(split + 10, y + 53, modeWidth, 1, () -> { sourceView = !sourceView; detailScroll = 0; updateControls(); }, "source");
        button(split + 16 + modeWidth, y + 53, 44, 1, () -> { if (focus != null) RecipeNavigation.show(plan, focus); }, "jei");
        button(x + 10, y + 65, Math.min(240, w / 2 - 15), 2, () -> chooseSetting("drop_tools"), "drop_tools");
        button(x + 10, y + 90, Math.min(240, w / 2 - 15), 2, () -> chooseSetting("output_policy"), "output_policy");
        button(x + 10, y + 115, 32, 2, () -> { changeMultiplier(ProcessingMultiplier.halveAmount(multiplier)); }, "halve");
        button(x + 48, y + 115, 56, 2, () -> { changeMultiplier(1); }, "multiplier");
        button(x + 110, y + 115, 32, 2, () -> { changeMultiplier(ProcessingMultiplier.doubleAmount(multiplier)); }, "double");
        button(x + w / 2 + 5, y + 65, w / 2 - 15, 2, () -> chooseSetting("input_scope"), "input_scope");
        button(x + w / 2 + 5, y + 90, w / 2 - 15, 2, () -> chooseSetting("output_scope"), "output_scope");
        button(x + w / 2 + 5, y + 115, w / 2 - 15, 2, () -> chooseSetting("search_tools"), "search_tools");
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
        settingChoice=null;
        for (int i = 0; i < 4; i++) boxes[i].visible = tab == 3;
        for (var c : controls) c.visible = c.page < 0 || c.page == tab;
        updateControls();
    }
    private int chosen() { if(filtering||searchDebounce.pending())return 0; if(chosenCount>=0)return chosenCount; int n = 0; for (var e : matches) if (selected.contains(e.recipeId()) && (dynamic ? sourceRecipe(e)!=null&&sourceRecipe(e).canEncode(outputPolicy,dropTools) : e.canEncode(outputPolicy, dropTools)) && !previewErrors.containsKey(e.recipeId())) n++; return chosenCount=n; }
    private void updateControls() {
        if(circuitButton!=null)circuitButton.active=!sending;
        int chosen = chosen();
        for (var c : controls) {
            c.active = !sending;
            if(Set.of("input_form","output_form","auxiliary").contains(c.key))c.active=!sending&&!filtering&&!searchDebounce.pending();
            if(c.key.equals("auxiliary"))c.visible=tab==0&&(!auxiliaryItems.isEmpty()||!auxItem.isEmpty());
            c.setMessage(switch (c.key) {
                case "tab_3" -> Arrays.stream(query).anyMatch(q->!q.isBlank())?tr("advanced_active"):tr("tab_3");
                case "auxiliary" -> tr("auxiliary_value",auxItem.isEmpty()?tr("auxiliary_all"):auxiliaryItems.containsKey(auxItem)?auxiliaryItems.get(auxItem).toItemStack().getHoverName():Component.literal(auxItem));
                case "input_form" -> tr("input_form_value", formName(inForm));
                case "output_form" -> tr("output_form_value", formName(outForm));
                case "material" -> tr("material_value", material.isEmpty()?tr("all_materials"):Component.literal(material));
                case "mode" -> tr(dynamic?"mode_dynamic":"mode_fixed");
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
            if (isSetting(c.key)) c.setMessage(c.getMessage().copy().append(" ▾"));
            if (c.key.equals("halve")) c.active &= multiplier > 1;
            if (c.key.equals("double")) c.active &= multiplier < ProcessingMultiplier.MAX;
            if (c.key.equals("material") || c.key.equals("select_all") || c.key.equals("select_none")) c.active &= !filtering && !searchDebounce.pending();
        }
        boolean ready = !multiplierDebounce.pending() && !sending && !filtering && !searchDebounce.pending() && heldRefreshTicks == 0 && chosen > 0 && (!dynamic||chosen<=MachineWildcardData.MAX_RECIPES) && ruleError==null;
        boolean networkReady = ready && ClientNetworkState.hasNetwork() && ClientNetworkState.blankWildcards() >= (dynamic ? 1 : (chosen + 2047) / 2048);
        generate.active = heldTarget == null ? networkReady : ready && chosen <= MachineWildcardData.MAX_RECIPES
                && minecraft.player != null && WildcardItems.writable(minecraft.player.getItemInHand(heldTarget.hand()));
        if (generateNetwork != null) generateNetwork.active = networkReady;
        for (var box : boxes) box.active = !sending;
    }
    private void changedRules() { chosenCount=-1;previewErrors.clear();previewError=null; searchDebounce.changed(); filtering = true; preview = null; previewRequest = -1; updateControls(); }
    private void changeMultiplier(int value) {
        if (multiplier == value) return;
        multiplier = value;
        previewRequest = -1;
        multiplierDebounce.changed();
        updateControls();
    }
    private void changedPolicy() { chosenCount=-1; previewErrors.clear(); previewError = null; preview = null; previewRequest = -1; previewDelay = 4; updateControls(); }
    private void startFilter() {
        ruleError=null;portableRule=null;
        try{if(dynamic)portableRule=new DynamicRule(state());}catch(IllegalArgumentException invalid){ruleError=tr("invalid_rule");}
        rules = new WildcardRules(WildcardRules.Side.of(query[0], query[1], inAll, inScope), WildcardRules.Side.of(query[2], query[3], outAll, outScope));
        working.clear(); cursor = 0; filtering = true;
    }
    @Override public void tick() {
        for (var box : boxes) box.tick();
        if (multiplierDebounce.poll()) { changedPolicy(); previewDelay = 1; }
        if (heldRefreshTicks > 0 && --heldRefreshTicks == 0 && heldTarget != null && minecraft.player != null) {
            var current = minecraft.player.getItemInHand(heldTarget.hand());
            if (MachineWildcard.isOwnItem(current)) heldTarget = new HeldTarget(heldTarget.hand(), current);
        }
        if (searchDebounce.poll()) startFilter();
        if (filtering && !searchDebounce.pending()) {
            long end = System.nanoTime() + 3_000_000; int n = 0;
            while (cursor < plan.entries().size() && n++ < 96 && System.nanoTime() < end) {
                var e = plan.entries().get(cursor++);
                if(catalogued.add(e.recipeId()))catalogue(e);
                if (e.kind() == PatternKind.PROCESSING && circuitCatalog.matches(e,circuit)) {
                    if(dynamic){var raw=sourceRecipe(e);if(portableRule!=null&&raw!=null&&portableRule.matches(raw))working.add(e);}
                    else if(rules.matches(e, searchTools)&&formMatches(e))working.add(e);
                }
            }
            if (cursor == plan.entries().size()) {
                chosenCount=-1; matches.clear(); matches.addAll(working); working.clear(); filtering = false; scroll = 0;
                if (!matches.contains(focus)) focus(matches.isEmpty() ? null : matches.get(0));
                else previewDelay = 4;
            }
        }
        if (!sending && !filtering && !searchDebounce.pending() && !multiplierDebounce.pending() && previewDelay > 0 && --previewDelay == 0 && focus != null) {
            try {
                var spec = specFor(focus);
                if (spec != null) previewRequest = PatternRequestSender.send(List.of(spec), 2);
                else previewError = tr("policy_empty");
            } catch (IllegalArgumentException | ArithmeticException invalid) {
                previewRequest=-1;previewError=EncodingMessages.explain(invalid.getMessage());
            }
        }
        updateControls();
    }
    private void focus(RecipePlan.Entry entry) { focus = entry; detailScroll = 0; renderedDetailHeight = 0; previewError = null; preview = null; previewRequest = -1; previewDelay = 4; }
    private void inspected(S2CInspectionPacket response) {
        if (response.requestId() != previewRequest || response.operation() != 2) return;
        if (response.success() && !response.preview().isEmpty()) preview = response.preview().get(0);
        else {
            io.github.sexileman.eae2pattern.EAE2Pattern.LOGGER.warn("Wildcard preview rejected: {}",response.details());
            status = response.details().isEmpty() ? tr("preview_failed") : EncodingMessages.explain(response.details().get(0));
            chosenCount=-1; previewError = status; if (focus != null) previewErrors.put(focus.recipeId(), status);
        }
        updateControls();
    }
    private CompoundTag state() {
        CompoundTag s = new CompoundTag(); String[] keys = {"in_include", "in_exclude", "out_include", "out_exclude"};
        for (int i = 0; i < 4; i++) s.putString(keys[i], query[i]); s.putString("category", plan.machineId().toString());
        s.putBoolean("in_all", inAll); s.putBoolean("out_all", outAll); s.putBoolean("drop_tools", dropTools); s.putBoolean("search_tools", searchTools);
        s.putInt("in_scope", inScope); s.putInt("out_scope", outScope); s.putInt("output_policy", outputPolicy); s.putInt("multiplier", multiplier);
        s.putString("in_form",inForm);s.putString("out_form",outForm);s.putString("material",material);s.putBoolean("dynamic",dynamic);
        if(!auxItem.isEmpty())s.putString("aux_item",auxItem);
        circuit.write(s);
        return WildcardEditorState.clean(s);
    }
    private void generate() {
        if (!generate.active) return;
        generate(heldTarget != null);
    }
    private void generate(boolean held) {
        if (sending || filtering || searchDebounce.pending()) return;
        if (!held && (generateNetwork != null ? !generateNetwork.active : !generate.active)) return;
        try {
            List<PatternSpec> specs = new ArrayList<>();
            for (var e : matches) if (selected.contains(e.recipeId()) && !previewErrors.containsKey(e.recipeId())) {
                var spec = specFor(e);
                if (spec != null) { specs.add(spec); if(dynamic)break; }
            }
            CompoundTag rules = state();
            if(dynamic){var exceptions=new TreeSet<ResourceLocation>(Comparator.comparing(ResourceLocation::toString));exceptions.addAll(storedExclusions);for(var e:plan.entries())if(e.kind()==PatternKind.PROCESSING){if(!selected.contains(e.recipeId()))exceptions.add(e.recipeId());else exceptions.remove(e.recipeId());}var excluded=new net.minecraft.nbt.ListTag();for(var id:exceptions)excluded.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));rules.put("exclude_ids",excluded);rules=WildcardEditorState.clean(rules);}
            int operation = 4;
            if (held) {
                rules.putByteArray("held_fingerprint", HeldWildcardFingerprint.visible(heldTarget.snapshot()));
                operation = heldTarget.hand() == net.minecraft.world.InteractionHand.MAIN_HAND ? 5 : 6;
            }
            if(dynamic&&specs.size()>1)specs=List.of(specs.get(0));
            PatternRequestSender.send(specs, operation, rules); writingHeld = held; sending = true; status = tr("preparing");
        }
        catch (IllegalArgumentException | ArithmeticException e) { status = EncodingMessages.explain(e.getMessage()); }
        updateControls();
    }
    @Override public void onGenerationFinished(GenerationReport report) {
        sending = false; status = ClientGenerationFeedback.describe(report);
        if (writingHeld && report.isSuccess()) { status = tr(heldTarget.snapshot().getCount()>1?"held_saved_inventory":"held_saved"); heldRefreshTicks = 2; }
        if (!report.details().isEmpty()) status = status.copy().append(" · ").append(EncodingMessages.explain(report.details().get(0)));
        PacketHandler.sendToServer(new C2SQueryNetworkStatusPacket(0)); updateControls();
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        hits.clear();  WorkbenchTheme.panel(g,x,y,w,h);
        label(g, title.copy().append(" / ").append(plan.machineName()), x + 10, y + 4 + (18 - font.lineHeight) / 2, w - (circuitButton==null?80:166), TEXT, 1);
        g.fill(x + 10, y + 26, x + w - 10, y + 27, WorkbenchTheme.LINE);
        if (tab == 0) {
            if(h>=250){label(g,tr("input_rules"),x+12,y+55,w/2-20,TEXT,.85f);label(g,tr("output_rules"),x+w/2+6,y+55,w/2-20,TEXT,.85f);}
            if(ruleError!=null)wrapped(g,ruleError,x+14,y+(h<250?110:123),w-28,WorkbenchTheme.WARNING,1);
        } else if (tab == 3) {
            if (h >= 220) {
                label(g, tr("input_rules"), x + 12, y + 55, w / 2 - 20, TEXT, .85f);
                label(g, tr("output_rules"), x + w / 2 + 6, y + 55, w / 2 - 20, TEXT, .85f);
            }
            for (var box : boxes) WorkbenchTheme.control(g, box.getX() - 5, box.getY() - 4, box.getWidth() + 10, 19, WorkbenchTheme.INPUT, WorkbenchTheme.LINE);
            if (h > 250 && ruleError!=null) wrapped(g,ruleError,x+14,y+160,w-28,WorkbenchTheme.WARNING,1);
        } else if (tab == 1) renderRecipes(g, mx, my);
        else if (h > 210) wrapped(g,encodingSummary(),x+14,y+(heldTarget==null?147:169),w-28,DIM,1);
        g.fill(x + 10, y + h - 34, x + w - 10, y + h - 33, WorkbenchTheme.LINE);
        label(g, filtering ? tr("filtering", cursor, plan.entries().size()) : heldTarget != null ? tr("summary_held", matches.size(), chosen()) : tr("summary", matches.size(), chosen(), ClientNetworkState.blankWildcards()), x + 10, y + h - 24, w - 160, DIM, .8f);
        if (!status.getString().isEmpty()) label(g, status, x + 10, y + h - 13, w - 160, WorkbenchTheme.WARNING, .7f);
        super.render(g, mx, my, partial);
        if(settingChoice!=null){settingChoice.render(g,mx,my);return;}
        for (var hit : hits) if (tab == 1 && my >= listTop && my < listBottom && mx >= hit.x && mx < hit.x + 18 && my >= hit.y && my < hit.y + 18) {
            var lines = new ArrayList<Component>();
            try {
                if (hit.spec.isItem()) lines.addAll(visuals.item(hit.spec).getTooltipLines(minecraft.player,
                        minecraft.options.advancedItemTooltips ? net.minecraft.world.item.TooltipFlag.Default.ADVANCED : net.minecraft.world.item.TooltipFlag.Default.NORMAL));
                else lines.add(visuals.name(hit.spec));
            } catch (RuntimeException malformedTooltip) { lines.add(visuals.name(hit.spec)); }
            lines.add(Component.literal(hit.spec.amount() + (hit.spec.isItem() ? "" : " mB")));
            lines.add(hit.presentation.description()); Tooltips.show(g,font,width, lines, mx, my); break;
        }
        for (int i = 0; i < boxes.length; i++) if (boxes[i].visible && boxes[i].isMouseOver(mx, my))
            Tooltips.show(g,font,width, List.of(tr("syntax_help")), mx, my);
        if (generate.isMouseOver(mx, my)) Tooltips.show(g,font,width, List.of(encodingSummary()), mx, my);
        for (var control : controls) if (control != generate && control.visible && control.isMouseOver(mx, my) && font.width(control.getMessage()) > control.getWidth()-10)
            Tooltips.show(g,font,width, List.of(control.getMessage()), mx, my);
        if (mx >= x + 10 && mx < x + w - 145 && my >= y + h - 30 && my < y + h - 2)
            Tooltips.show(g,font,width, List.of(tr(heldTarget!=null?"summary_held":"summary", matches.size(), chosen(), ClientNetworkState.blankWildcards()), status), mx, my);
    }
    private Component encodingSummary() {
        if(ruleError!=null)return ruleError;
        if(filtering||searchDebounce.pending())return tr("filtering",cursor,plan.entries().size());
        int count=chosen();
        if(count==0)return Component.translatable("eae2_pattern.report.nothing");
        if((dynamic||heldTarget!=null)&&count>MachineWildcardData.MAX_RECIPES)return tr("limit_exceeded",count);
        if(heldTarget!=null)return tr("target_held");
        if(!ClientNetworkState.hasStatus())return Component.translatable("eae2_pattern.workbench.querying");
        if(!ClientNetworkState.hasNetwork())return Component.translatable("eae2_pattern.workbench.no_network");
        return tr(dynamic?"stock_exact":"stock_minimum",ClientNetworkState.blankWildcards(),dynamic?1:(count+2047)/2048);
    }
    private void renderRecipes(GuiGraphics g, int mx, int my) {
        g.enableScissor(x + 10, listTop, split - 5, listBottom);
        int first = (int) scroll / ROW, visible = (listBottom - listTop) / ROW + 2;
        for (int i = first; i < Math.min(matches.size(), first + visible); i++) {
            var e = matches.get(i); int ry = listTop + i * ROW - (int) scroll;
            boolean skipped=previewErrors.containsKey(e.recipeId())||!e.canEncode(outputPolicy,dropTools);
            Component badge=skipped?Component.literal("!"):e.hasProbabilisticInput()||e.hasProbabilisticOutput()?Component.translatable("eae2_pattern.workbench.chance_badge"):Component.empty();
            RecipeListRow.draw(g,font,visuals,e,x+10,ry,split-8,selected.contains(e.recipeId()),e==focus,
                    mx>=x+10&&mx<split-8&&my>=ry&&my<ry+ROW,badge,
                    (hx,hy,ingredient,side)->hits.add(new Hit(hx,hy,ingredient.spec(),IngredientPresentation.of(ingredient,side),side)));
        }
        g.disableScissor();
        scrollbar(g, split - 7, listTop, listBottom, scroll, matches.size() * ROW);
        g.fill(split, listTop, split + 1, listBottom, WorkbenchTheme.LINE);
        if (focus == null) return;
        g.enableScissor(split + 8, listTop, x + w - 10, listBottom);
        int dy = listTop - (int) detailScroll, dx = split + 10, room = x + w - dx - 14;
        label(g, focus.label(), dx, dy + 2, room, TEXT, 1); dy += 18;
        List<Ingredient> in = new ArrayList<>(), out = new ArrayList<>();
        if (sourceView) { in.addAll(focus.inputs()); in.addAll(focus.catalysts()); out.addAll(focus.outputs()); }
        else if (preview != null) {
            for (var s : preview.inputs()) in.add(new Ingredient(s, 1, true));
            for (var s : preview.outputs()) out.add(Ingredient.produced(s, 1));
        } else { dy=detailNotice(g,previewError==null?tr("preview_wait"):previewError,dx,dy,room); }
        if(sourceView||preview!=null){dy = materialRows(g, in, dx, dy, room, 0); dy = materialRows(g, out, dx, dy, room, 1);}
        if(!sourceView&&preview!=null&&focus.hasNeverConsumedInput()) {
            var tools=new ArrayList<Ingredient>();
            for(var ingredient:focus.inputs())if(ingredient.neverConsumed())tools.add(ingredient);
            tools.addAll(focus.catalysts());
            if(!tools.isEmpty())dy=setupRows(g,tools,dx,dy,room);
        }
        if(!sourceView&&preview!=null){
            if(focus.hasUnknownOutputChance())dy=detailNotice(g,Component.translatable("eae2_pattern.workbench.unknown_output_warning"),dx,dy,room);
            if(focus.hasProbabilisticOutput())dy=detailNotice(g,Component.translatable("eae2_pattern.workbench.chance_warning"),dx,dy,room);
            if(focus.hasProbabilisticInput())dy=detailNotice(g,Component.translatable("eae2_pattern.workbench.chance_input_warning"),dx,dy,room);
        }
        renderedDetailHeight=dy-listTop+(int)detailScroll;
        g.disableScissor();
        int content = detailHeight(); scrollbar(g, x + w - 9, listTop, listBottom, detailScroll, content);
    }
    private int setupRows(GuiGraphics g,List<Ingredient> tools,int dx,int dy,int room) {
        label(g,Component.translatable("eae2_pattern.workbench."+(dropTools?"machine_setup":"required_tools")),dx,dy+2,room,WorkbenchTheme.WARNING,1);
        return ingredientRows(g,tools,dx,dy+18,room,0,true);
    }
    private int detailNotice(GuiGraphics g,Component text,int dx,int dy,int room) {
        var lines=font.split(text,Math.max(1,room-16));int height=32+lines.size()*9;
        g.fill(dx,dy+5,dx+room,dy+height-4,WorkbenchTheme.SURFACE);
        g.fill(dx,dy+5,dx+2,dy+height-4,WorkbenchTheme.WARNING);
        label(g,Component.translatable("eae2_pattern.workbench.notice_warning"),dx+8,dy+10,room-16,WorkbenchTheme.WARNING,1);
        int ty=dy+24;for(var line:lines){g.drawString(font,line,dx+8,ty,DIM,false);ty+=9;}
        return dy+height;
    }
    private int materialRows(GuiGraphics g, List<Ingredient> ingredients, int dx, int dy, int room, int side) {
        label(g, sourceView?tr(side == 0 ? "inputs" : "outputs"):Component.translatable("eae2_pattern.workbench."+(side==0?"encoded_inputs":"encoded_outputs")), dx, dy + 4, room, WorkbenchTheme.ACCENT, 1); dy += 18;
        return ingredientRows(g,ingredients,dx,dy,room,side,sourceView);
    }
    private int ingredientRows(GuiGraphics g,List<Ingredient> ingredients,int dx,int dy,int room,int side,boolean original) {
        for (var ingredient : ingredients) {
            var p = original ? IngredientPresentation.of(ingredient, side) : IngredientPresentation.encoded(focus, ingredient.spec(), side, dropTools);
            if (dy + 32 >= listTop && dy < listBottom) {
                WorkbenchTheme.slot(g, dx, dy + 2, p); visuals.draw(g, ingredient.spec(), dx + 1, dy + 3); visuals.drawRule(g, p, dx + 1, dy + 3);
                label(g, ingredientName(ingredient), dx + 24, dy + 3, room - 24, TEXT, 1);
                label(g, Component.translatable("eae2_pattern.workbench."+(ingredient.spec().isItem()?"item_amount":"fluid_amount"),ingredient.spec().amount()), dx + 24, dy + 15, room - 24, DIM, .5f);
                if (p.kind() != IngredientPresentation.Kind.REGULAR) label(g, p.description(), dx + 24, dy + 23, room - 24, p.color(), .5f);
                hits.add(new Hit(dx + 1, dy + 3, ingredient.spec(), p, side));
            }
            dy += p.kind()==IngredientPresentation.Kind.REGULAR?24:32;
        }
        return dy;
    }
    private Component ingredientName(Ingredient ingredient) {
        var name=visuals.name(ingredient.spec());var tag=ingredient.spec().tag();
        return tag!=null&&tag.contains("Configuration")?name.copy().append(" · ").append(Component.translatable("eae2_pattern.workbench.circuit_setting",tag.getInt("Configuration"))):name;
    }
    private int detailHeight() { return renderedDetailHeight; }
    private int renderedDetailHeight;
    private void scrollbar(GuiGraphics g, int sx, int top, int bottom, double amount, int content) {
        int size = Math.max(1, bottom - top); if (content <= size) return;
        int thumb = Math.max(12, size * size / content), sy = top + (int) ((size - thumb) * amount / Math.max(1, content - size));
        g.fill(sx, top, sx + 3, bottom, WorkbenchTheme.LINE); g.fill(sx, sy, sx + 3, sy + thumb, DIM);
    }
    private void label(GuiGraphics g,Component text,int px,int py,int room,int color,float scale) {
        WorkbenchTheme.text(g,font,text,px,py,room,color,scale);
    }
    private void wrapped(GuiGraphics g,Component text,int px,int py,int room,int color,float ignoredScale) {
        for(var part:font.split(text,Math.max(1,room))){
            if(py+font.lineHeight>y+h-36)break;
            g.drawString(font,part,px,py,color,false);py+=font.lineHeight+1;
        }
    }
    private static double clamp(double value, int content, int viewport) { return Math.max(0, Math.min(value, Math.max(0, content - viewport))); }
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if(settingChoice!=null)return true;
        if (!insidePanel(mx,my) || tab != 1 || my < listTop || my > listBottom) return super.mouseScrolled(mx, my, delta);
        if (mx < split) scroll = clamp(scroll - delta * ROW, matches.size() * ROW, listBottom - listTop);
        else detailScroll = clamp(detailScroll - delta * 30, detailHeight(), listBottom - listTop); return true;
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if(settingChoice!=null){var popup=settingChoice;settingChoice=null;int index=popup.bounds.indexAt(mx,my);if(button==0&&index>=0)popup.select(index);if(index>=0||insidePanel(mx,my))return true;return false;}
        if(!insidePanel(mx,my))return false;
        if (super.mouseClicked(mx, my, button)) return true;
        if(tab==1&&button==0&&!sending&&my>=listTop&&my<listBottom)for(var hit:hits)if(mx>=hit.x&&mx<hit.x+18&&my>=hit.y&&my<hit.y+18){chooseIngredient(hit);return true;}
        if (tab != 1 || my < listTop || my >= listBottom || button != 0) return false;
        if (mx >= split - 11 && mx < split) { dragging = true; drag(my, false); return true; }
        if (mx >= x + w - 13 && mx < x + w) { detailDragging = true; drag(my, true); return true; }
        if (mx >= x + 10 && mx < split - 11) {
            int index = (int) ((my - listTop + scroll) / ROW);
            if (index < matches.size()) { var e = matches.get(index); focus(e); if (mx < x + 26 && !sending) { if (!selected.remove(e.recipeId())) selected.add(e.recipeId()); chosenCount=-1; } updateControls(); return true; }
        }
        return false;
    }
    private static Component formName(String value){return MaterialForms.formName(value); }
    private void chooseForm(boolean input){
        var choices=new ArrayList<PickerOption>();choices.add(new PickerOption("",formName("")));
        (input?inputForms:outputForms).forEach((form,example)->choices.add(new PickerOption(form,formName(form),Component.literal(form),example)));
        minecraft.setScreen(new WildcardChoiceScreen(this,tr(input?"input_rules":"output_rules"),choices,v->{if(input)inForm=v;else outForm=v;changedRules();}));
    }
    private void catalogue(RecipePlan.Entry entry){
        if(entry.kind()!=PatternKind.PROCESSING)return;
        var raw=sourceRecipe(entry);
        if(raw!=null){
            for(var slot:raw.inputs())catalogueStack(slot.stack(),true,slot.auxiliary());
            for(var slot:raw.outputs())catalogueStack(slot.stack(),false,false);
        }else{
            for(var ingredient:entry.inputs())catalogueStack(ingredient.spec(),true,ingredient.neverConsumed());
            for(var ingredient:entry.outputs())catalogueStack(ingredient.spec(),false,false);
        }
    }
    private void catalogueStack(StackSpec spec,boolean input,boolean auxiliary){
        materialCatalog.add(spec);
        if(input&&auxiliary){
            if(spec.isItem()&&CircuitCondition.configuration(spec)<0)auxiliaryItems.putIfAbsent(spec.id().toString(),spec.withAmount(1));
            return;
        }
        for(String form:MaterialForms.forms(spec))(input?inputForms:outputForms).putIfAbsent(form,spec.withAmount(1));
    }
    private void chooseAuxiliary(){
        var choices=new ArrayList<PickerOption>();choices.add(new PickerOption("",tr("auxiliary_all")));
        auxiliaryItems.forEach((id,spec)->choices.add(new PickerOption(id,spec.toItemStack().getHoverName(),Component.literal(id),spec)));
        minecraft.setScreen(new WildcardChoiceScreen(this,tr("auxiliary"),choices,v->{auxItem=v;changedRules();}));
    }
    private void chooseMaterial(){
        var choices=new ArrayList<PickerOption>();
        choices.add(new PickerOption("",tr("all_materials")));
        choices.addAll(materialCatalog.options());
        minecraft.setScreen(new WildcardChoiceScreen(this,tr("material"),choices,v->{material=v;changedRules();}));
    }
    private RuleRecipe sourceRecipe(RecipePlan.Entry e){
        var cached=sourceRecipes.get(e.recipeId());if(cached!=null)return cached;if(unsupportedSources.contains(e.recipeId()))return null;
        Object r=e.jeiRecipe();try{if(r!=null&&r.getClass().getName().equals("com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper"))r=ServerRecipeReader.member(r,"recipe");}catch(ReflectiveOperationException ignored){r=null;}
        if(r instanceof net.minecraft.world.item.crafting.Recipe<?> recipe&&minecraft.level!=null){var raw=ServerRecipeReader.read(recipe,minecraft.level);if(raw!=null){sourceRecipes.put(e.recipeId(),raw);return raw;}}
        unsupportedSources.add(e.recipeId());return null;
    }
    private PatternSpec specFor(RecipePlan.Entry e){var raw=dynamic?sourceRecipe(e):null;return dynamic?(raw==null?null:raw.encodeSpec(multiplier,outputPolicy,dropTools)):e.specForPolicy(multiplier,outputPolicy,dropTools);}
    private boolean formMatches(RecipePlan.Entry e){
        if(!auxItem.isEmpty()&&e.inputs().stream().noneMatch(i->i.neverConsumed()&&i.spec().isItem()&&CircuitCondition.configuration(i.spec())<0&&i.spec().id().toString().equals(auxItem)))return false;
        return structural(e.inputs().stream().filter(i->!i.neverConsumed()).map(Ingredient::spec).toList(),inForm)&&structural(e.outputs().stream().map(Ingredient::spec).toList(),outForm);
    }
    private boolean structural(List<StackSpec> slots,String form){return (form.isEmpty()&&material.isEmpty())||slots.stream().anyMatch(s->MaterialForms.matches(s,form,material));}
    private void extractFocused(){
        var e=focus;if(e==null){status=tr("extract_choose");tab=1;sync();return;}
        var inputs=new TreeSet<String>();var outputs=new TreeSet<String>();
        for(var i:e.inputs())if(!i.neverConsumed())inputs.addAll(MaterialForms.forms(i.spec()));for(var i:e.outputs())outputs.addAll(MaterialForms.forms(i.spec()));
        var choices=new ArrayList<PickerOption>();for(String in:inputs)for(String out:outputs)choices.add(new PickerOption(in+">"+out,formName(in).copy().append(" → ").append(formName(out))));
        if(choices.isEmpty()){status=tr("extract_none");return;}
        minecraft.setScreen(new WildcardChoiceScreen(this,tr("extract"),choices,v->{var pair=v.split(">");inForm=pair[0];outForm=pair[1];material="";query[0]="";query[2]="";inAll=false;outAll=false;status=tr("extract_applied");tab=0;changedRules();}));
    }
    private void chooseIngredient(Hit hit){
        int index=hit.side==0?0:2;var choices=new ArrayList<PickerOption>();
        for(String form:MaterialForms.forms(hit.spec))choices.add(new PickerOption("form:"+form,tr("use_form",formName(form))));
        for(String tag:MaterialForms.tags(hit.spec))if(tag.length()<240)choices.add(new PickerOption("tag:"+tag,Component.literal("$"+tag)));
        choices.add(new PickerOption("id:"+hit.spec.id(),tr("use_id",hit.spec.id().toString())));
        minecraft.setScreen(new WildcardChoiceScreen(this,tr("ingredient_rule"),choices,v->{if(v.startsWith("form:")){if(index==0)inForm=v.substring(5);else outForm=v.substring(5);tab=0;}else{query[index]=(v.startsWith("tag:")?"$":"&")+v.substring(v.indexOf(':')+1);tab=3;}changedRules();}));
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
    private static boolean isSetting(String key){return Set.of("drop_tools","output_policy","input_scope","output_scope","search_tools","mode","input_match","output_match").contains(key);}
    private void chooseSetting(String key){
        var anchor=controls.stream().filter(c->c.key.equals(key)).findFirst().orElseThrow();
        List<Component> labels;int current;
        switch(key){
            case "drop_tools" -> {labels=List.of(tr("tools_machine"),tr("tools_pattern"));current=dropTools?0:1;}
            case "output_policy" -> {labels=List.of(tr("policy_0"),tr("policy_1"),tr("policy_2"));current=outputPolicy;}
            case "input_scope","output_scope" -> {labels=List.of(tr("scope_0"),tr("scope_1"),tr("scope_2"));current=key.equals("input_scope")?inScope:outScope;}
            case "input_match" -> {labels=List.of(tr("input_recipe"),tr("input_all"));current=inAll?1:0;}
            case "output_match" -> {labels=List.of(tr("output_recipe"),tr("output_all"));current=outAll?1:0;}
            case "mode" -> {labels=dynamicAvailable?List.of(tr("mode_dynamic"),tr("mode_fixed")):List.of(tr("mode_fixed"));current=dynamicAvailable?(dynamic?0:1):0;}
            case "search_tools" -> {labels=List.of(tr("search_tools_yes"),tr("search_tools_no"));current=searchTools?0:1;}
            default -> throw new IllegalArgumentException("Unknown encoding setting");
        }
        settingChoice=new SettingChoice(anchor,key,labels,current);
    }
    private final class SettingChoice {
        final String key;final List<Component> labels;final int current;final PopupLayout bounds;int cursor;
        SettingChoice(Control anchor,String key,List<Component> labels,int current){
            this.key=key;this.labels=labels;this.current=current;cursor=current;
            bounds=PopupLayout.measure(anchor.getX(),anchor.getY(),anchor.getWidth(),anchor.getHeight(),labels.stream().mapToInt(font::width).max().orElse(80)+28,labels.size(),width,height);
        }
        void select(int value){
            if(value==current)return;
            switch(key){
                case "drop_tools" -> dropTools=value==0;
                case "output_policy" -> outputPolicy=value;
                case "input_scope" -> inScope=value;
                case "output_scope" -> outScope=value;
                case "search_tools" -> searchTools=value==0;
                case "mode" -> dynamic=dynamicAvailable&&value==0;
                case "input_match" -> inAll=value==1;
                case "output_match" -> outAll=value==1;
                default -> throw new IllegalArgumentException("Unknown encoding setting");
            }
            if(key.equals("drop_tools")||key.equals("output_policy"))changedPolicy();else changedRules();
        }
        void render(GuiGraphics g,int mx,int my){
            g.pose().pushPose();g.pose().translate(0,0,400);
            WorkbenchTheme.control(g,bounds.x(),bounds.y(),bounds.width(),bounds.height(),WorkbenchTheme.SURFACE,WorkbenchTheme.LINE);
            for(int i=0;i<labels.size();i++){
                int rowY=bounds.y()+3+i*19;
                if(i==cursor||bounds.indexAt(mx,my)==i)g.fill(bounds.x()+3,rowY,bounds.x()+bounds.width()-3,rowY+19,WorkbenchTheme.HOVER);
                label(g,labels.get(i),bounds.x()+8,rowY+5,bounds.width()-28,TEXT,1);
                if(i==current)g.drawString(font,"✓",bounds.x()+bounds.width()-16,rowY+5,TEXT,false);
            }
            g.pose().popPose();
        }
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(settingChoice!=null){
            if(key==256){settingChoice=null;return true;}
            if(key==264||key==265){settingChoice.cursor=Math.floorMod(settingChoice.cursor+(key==264?1:-1),settingChoice.labels.size());return true;}
            if(key==257||key==335||key==32){var popup=settingChoice;settingChoice=null;popup.select(popup.cursor);return true;}
            return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public List<net.minecraft.client.renderer.Rect2i> jeiExtraAreas(){
        if(settingChoice==null)return List.of();var b=settingChoice.bounds;return List.of(new net.minecraft.client.renderer.Rect2i(b.x(),b.y(),b.width(),b.height()));
    }
    private record Hit(int x, int y, StackSpec spec, IngredientPresentation presentation, int side) {}
    private final class Control extends AbstractButton {
        final int page; final Runnable action; final String key;
        Control(int x, int y, int width, int page, Runnable action, String key) { super(x, y, width, 18, tr(key)); this.page = page; this.action = action; this.key = key; }
        @Override public void onPress() { action.run(); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            WorkbenchTheme.control(g, getX(), getY(), getWidth(), getHeight(), key.equals("tab_"+tab)?WorkbenchTheme.PRIMARY:isHoveredOrFocused() && active ? WorkbenchTheme.HOVER : WorkbenchTheme.SURFACE, WorkbenchTheme.LINE);
            int tw = font.width(getMessage()); label(g, getMessage(), getX() + Math.max(5, (getWidth() - tw) / 2), getY() + (getHeight() - font.lineHeight) / 2, getWidth() - 10, active ? TEXT : DIM, 1);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
    @Override public net.minecraft.client.renderer.Rect2i toolBounds(){return new net.minecraft.client.renderer.Rect2i(x,y,w,h);}

    @Override public java.util.Optional<IngredientArea> jeiIngredientAt(double mx,double my){
        if(settingChoice!=null||tab!=1||my<listTop||my>=listBottom)return java.util.Optional.empty();
        for(var hit:hits)if(mx>=hit.x&&mx<hit.x+18&&my>=hit.y&&my<hit.y+18)return java.util.Optional.of(new IngredientArea(hit.spec,new net.minecraft.client.renderer.Rect2i(hit.x,hit.y,18,18)));
        return java.util.Optional.empty();
    }
    @Override public List<GhostField> jeiGhostFields(){
        if(sending||settingChoice!=null)return List.of();
        var targets=new ArrayList<GhostField>();
        if(circuitButton!=null)targets.add(new GhostField(new net.minecraft.client.renderer.Rect2i(circuitButton.getX(),circuitButton.getY(),circuitButton.getWidth(),circuitButton.getHeight()),s->{int n=CircuitCondition.configuration(s);if(n>=0){circuit=new CircuitCondition(2,Set.of(n));circuitButton.setMessage(CircuitCatalog.label(circuit));changedRules();}}));for(var box:boxes)if(box.visible&&box.active)targets.add(ghostField(box,s->box.setValue("&"+s.id())));return targets;
    }

}
