package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.pattern.PatternKind;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Requirement #6: "let the user preview the patterns that will be generated, and drop the ones they do not want".
 *
 * <p>The plan is built entirely on the client from JEI data, so opening the preview costs nothing and touching
 * a checkbox has no server round trip. Only pressing "generate" ships the surviving entries to the server.</p>
 */
public final class RecipePlan {

    private final ResourceLocation machineId;
    private final Component machineName;
    private final PatternKind preferredKind;
    private final List<Entry> entries;

    public RecipePlan(ResourceLocation machineId, Component machineName, PatternKind preferredKind, List<Entry> entries) {
        this.machineId = machineId;
        this.machineName = machineName;
        this.preferredKind = preferredKind;
        this.entries = new ArrayList<>(entries);
    }

    public ResourceLocation machineId() {
        return machineId;
    }

    public Component machineName() {
        return machineName;
    }

    public PatternKind preferredKind() {
        return preferredKind;
    }

    public List<Entry> entries() {
        return entries;
    }

    /**
     * Turns the selected rows into what the server will encode.
     *
     * @param multiplier        scales every amount of a processing pattern, so one pattern carries the work of
     *                          several crafts; other pattern kinds are bound to the shape of their recipe and are
     *                          sent unscaled
     * @param dropChanceOutputs leaves probabilistic products out entirely, because AE2 would otherwise pretend
     *                          they always drop
     * @param dropNeverConsumed leaves out the inputs that are needed but not used up
     */
    public List<PatternSpec> selectedSpecs(int multiplier, boolean dropChanceOutputs, boolean dropNeverConsumed) {
        return selectedSpecs(entries, multiplier, dropChanceOutputs, dropNeverConsumed);
    }

    /**
     * Same, but only over the rows the player can currently see.
     *
     * <p>The preview filters the list, so what gets generated has to be the filtered list too: generating the
     * whole plan while showing a handful of rows looks like the search box does nothing.</p>
     */
    public List<PatternSpec> selectedSpecs(List<Entry> subset, int multiplier, boolean dropChanceOutputs,
                                           boolean dropNeverConsumed) {
        List<PatternSpec> specs = new ArrayList<>();
        for (Entry entry : subset) {
            if (!entry.isSelected()) {
                continue;
            }
            PatternSpec spec = entry.specFor(multiplier, dropChanceOutputs, dropNeverConsumed);
            if (spec != null) {
                specs.add(spec);
            }
        }
        return specs;
    }

    public int selectedCount() {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.isSelected()) {
                count++;
            }
        }
        return count;
    }

    public void selectOnly(PatternKind kind) {
        for (Entry entry : entries) {
            entry.setSelected(entry.kind() == kind);
        }
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** One row of the preview list. Mutable selection state lives here so the list widget stays dumb. */
    public static final class Entry {
        private final PatternKind kind;
        private final ResourceLocation recipeId;
        private final Component label;
        private final ItemStack icon;
        private final List<Ingredient> inputs;
        private final List<Ingredient> catalysts;
        private final List<Ingredient> outputs;
        private boolean selected;

        /** Search haystacks, built on first use because most sessions never type in a search box. */
        @Nullable
        private Haystack inputHaystack;
        @Nullable
        private Haystack outputHaystack;

        public Entry(PatternKind kind, ResourceLocation recipeId, Component label, ItemStack icon,
                     List<Ingredient> inputs, List<Ingredient> catalysts, List<Ingredient> outputs, boolean selected) {
            this.kind = kind;
            this.recipeId = recipeId;
            this.label = label;
            this.icon = icon;
            this.inputs = List.copyOf(inputs);
            this.catalysts = List.copyOf(catalysts);
            this.outputs = List.copyOf(outputs);
            this.selected = selected;
        }

        public PatternKind kind() {
            return kind;
        }

        public ResourceLocation recipeId() {
            return recipeId;
        }

        /** Inputs the machine actually uses up, with their consumption chance. */
        public List<Ingredient> inputs() {
            return inputs;
        }

        /** Inputs that are required but not consumed (moulds, lenses, catalysts). */
        public List<Ingredient> catalysts() {
            return catalysts;
        }

        public List<Ingredient> outputs() {
            return outputs;
        }

        public boolean hasNeverConsumedInput() {
            if (!catalysts.isEmpty()) {
                return true;
            }
            for (Ingredient input : inputs) {
                if (input.neverConsumed()) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasProbabilisticInput() {
            for (Ingredient input : inputs) {
                if (input.probabilistic()) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasProbabilisticOutput() {
            for (Ingredient output : outputs) {
                if (!output.guaranteedOutput()) {
                    return true;
                }
            }
            return false;
        }

        /** A probabilistic product other than the main one. */
        public boolean hasProbabilisticByproduct() {
            for (int i = 1; i < outputs.size(); i++) {
                if (!outputs.get(i).guaranteedOutput()) {
                    return true;
                }
            }
            return false;
        }

        /** Everything the recipe consumes or merely requires, as searchable strings. */
        public Haystack inputHaystack() {
            if (this.inputHaystack == null) {
                this.inputHaystack = Haystack.of(this.inputs, this.catalysts);
            }
            return this.inputHaystack;
        }

        /** Everything the recipe produces. */
        public Haystack outputHaystack() {
            if (this.outputHaystack == null) {
                this.outputHaystack = Haystack.of(this.outputs);
            }
            return this.outputHaystack;
        }

        /**
         * Identity of what this recipe makes, used to spot the several ways of making the same thing.
         *
         * <p>Two recipes for the same output - a GregTech bending machine turning either two iron ingots or two
         * iron plates into a double iron plate - share this key but not their inputs, which is exactly the pair of
         * rows a player wants to tell apart.</p>
         */
        public String outputKey() {
            if (outputs.isEmpty()) {
                return recipeId.toString();
            }
            StackSpec first = outputs.get(0).spec();
            return first.type().name() + '|' + first.id() + '|' + (first.hasTag() ? "" + first.tag() : "");
        }

        /**
         * Builds the pattern this row would encode, or {@code null} when the current switches leave nothing to
         * encode (for example every product was probabilistic and the player asked to drop those).
         */
        @Nullable
        public PatternSpec specFor(int multiplier, boolean dropChanceOutputs, boolean dropNeverConsumed) {
            int scale = kind == PatternKind.PROCESSING ? Math.max(1, multiplier) : 1;

            List<StackSpec> patternInputs = new ArrayList<>(inputs.size());
            for (Ingredient input : inputs) {
                if (dropNeverConsumed && input.neverConsumed()) {
                    continue;
                }
                patternInputs.add(scaled(input.spec(), scale));
            }

            List<StackSpec> patternOutputs = new ArrayList<>(outputs.size());
            for (Ingredient output : outputs) {
                if (dropChanceOutputs && !output.guaranteedOutput()) {
                    continue;
                }
                patternOutputs.add(scaled(output.spec(), scale));
            }

            if (patternOutputs.isEmpty()) {
                return null;
            }
            return new PatternSpec(kind, recipeId, patternInputs, patternOutputs);
        }

        private static StackSpec scaled(StackSpec spec, int scale) {
            if (scale <= 1) {
                return spec;
            }
            long amount = spec.amount();
            if (amount > Long.MAX_VALUE / scale) {
                return spec.withAmount(Long.MAX_VALUE);
            }
            return spec.withAmount(amount * scale);
        }

        public Component label() {
            return label;
        }

        public ItemStack icon() {
            return icon;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean value) {
            this.selected = value;
        }

        public void toggle() {
            this.selected = !this.selected;
        }
    }

    /**
     * The strings one half of a recipe can be searched by, split per JEI prefix.
     *
     * <p>The groups are kept apart instead of being concatenated so that {@code @gtceu} only matches when a mod
     * really is involved, not when a name happens to contain that text.</p>
     */
    public static final class Haystack {
        private final List<ItemStack> stacks;
        private final List<String> names;
        private final List<String> ids;
        private final List<String> mods;

        /** The expensive groups: built only when a query actually asks for them. */
        @Nullable
        private List<String> tags;
        @Nullable
        private List<String> tooltips;
        @Nullable
        private List<String> tabs;

        private Haystack(List<ItemStack> stacks, List<String> names, List<String> ids, List<String> mods) {
            this.stacks = stacks;
            this.names = names;
            this.ids = ids;
            this.mods = mods;
        }

        @SafeVarargs
        static Haystack of(List<Ingredient>... groups) {
            List<ItemStack> stacks = new ArrayList<>();
            Set<String> names = new LinkedHashSet<>();
            Set<String> ids = new LinkedHashSet<>();
            Set<String> mods = new LinkedHashSet<>();

            for (List<Ingredient> group : groups) {
                for (Ingredient ingredient : group) {
                    StackSpec spec = ingredient.spec();
                    ids.add(spec.id().toString());
                    ids.add(spec.id().getPath());
                    mods.add(spec.id().getNamespace());
                    mods.addAll(modStringsOf(spec.id().getNamespace()));

                    ItemStack stack = spec.toItemStack();
                    if (!stack.isEmpty()) {
                        stacks.add(stack);
                        names.add(stack.getHoverName().getString());
                    }
                }
            }

            return new Haystack(List.copyOf(stacks), List.copyOf(names), List.copyOf(ids), List.copyOf(mods));
        }

        public List<String> names() {
            return names;
        }

        public List<String> ids() {
            return ids;
        }

        public List<String> mods() {
            return mods;
        }

        public List<String> tags() {
            if (this.tags == null) {
                Set<String> set = new LinkedHashSet<>();
                for (ItemStack stack : this.stacks) {
                    set.addAll(tagsOf(stack.getItem()));
                }
                this.tags = List.copyOf(set);
            }
            return this.tags;
        }

        public List<String> tooltips() {
            if (this.tooltips == null) {
                Set<String> set = new LinkedHashSet<>();
                for (ItemStack stack : this.stacks) {
                    set.addAll(tooltipsOf(stack));
                }
                this.tooltips = List.copyOf(set);
            }
            return this.tooltips;
        }

        public List<String> tabs() {
            if (this.tabs == null) {
                Set<String> set = new LinkedHashSet<>();
                for (ItemStack stack : this.stacks) {
                    set.addAll(tabsOf(stack.getItem()));
                }
                this.tabs = List.copyOf(set);
            }
            return this.tabs;
        }
    }

    /** Best-effort human label for a spec, used when the JEI adapter has nothing better to offer. */
    @Nullable
    public static Component defaultLabel(PatternSpec spec) {
        if (spec.outputs().isEmpty()) {
            return null;
        }
        StackSpec output = spec.outputs().get(0);
        ItemStack stack = output.toItemStack();
        return stack.isEmpty() ? Component.literal(output.id().toString()) : stack.getHoverName();
    }

    /**
     * The mod id plus what JEI also accepts for {@code @}: the mod's display name and its short forms, so
     * {@code @gt} finds GregTech just like it does in the JEI search box.
     */
    private static List<String> modStringsOf(String namespace) {
        List<String> cached = MOD_CACHE.get(namespace);
        if (cached != null) {
            return cached;
        }
        List<String> result = new ArrayList<>(3);
        ModList.get().getModContainerById(namespace).ifPresent(container -> {
            String displayName = container.getModInfo().getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                result.add(displayName);
                String[] words = MOD_NAME_SEPARATOR.split(displayName);
                if (words.length > 1) {
                    result.add(firstLetters(words, 1));
                    result.add(firstLetters(words, 2));
                }
            }
        });
        List<String> copy = List.copyOf(result);
        if (MOD_CACHE.size() < CACHE_LIMIT) {
            MOD_CACHE.put(namespace, copy);
        }
        return copy;
    }

    private static String firstLetters(String[] words, int count) {
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            builder.append(word, 0, Math.min(count, word.length()));
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    /** Item tags are immutable for the lifetime of the client, so one lookup per item is enough. */
    private static List<String> tagsOf(Item item) {
        List<String> cached = TAG_CACHE.get(item);
        if (cached != null) {
            return cached;
        }
        List<String> tags = new ArrayList<>();
        item.builtInRegistryHolder().tags().map(TagKey::location).map(ResourceLocation::toString).forEach(tags::add);
        List<String> result = List.copyOf(tags);
        if (TAG_CACHE.size() < CACHE_LIMIT) {
            TAG_CACHE.put(item, result);
        }
        return result;
    }

    /**
     * What {@code #} searches in JEI. Tooltips depend on the item and the player's world, so they are built once
     * per item and reused by every row that mentions it.
     */
    private static List<String> tooltipsOf(ItemStack stack) {
        Item item = stack.getItem();
        List<String> cached = TOOLTIP_CACHE.get(item);
        if (cached != null) {
            return cached;
        }
        List<String> lines = new ArrayList<>();
        try {
            for (Component line : stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL)) {
                lines.add(line.getString());
            }
        } catch (RuntimeException ignored) {
            // A broken tooltip provider must not take the preview down with it; that item just gets no # text.
        }
        List<String> result = List.copyOf(lines);
        if (TOOLTIP_CACHE.size() < CACHE_LIMIT) {
            TOOLTIP_CACHE.put(item, result);
        }
        return result;
    }

    /** What {@code %} searches in JEI. Built once, because it takes a walk over every creative tab. */
    private static List<String> tabsOf(Item item) {
        Map<Item, List<String>> tabs = CREATIVE_TABS;
        if (tabs == null) {
            tabs = buildCreativeTabs();
            CREATIVE_TABS = tabs;
        }
        return tabs.getOrDefault(item, List.of());
    }

    private static Map<Item, List<String>> buildCreativeTabs() {
        Map<Item, List<String>> map = new HashMap<>();
        try {
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                String tabName = tab.getDisplayName().getString();
                for (ItemStack stack : tab.getDisplayItems()) {
                    map.computeIfAbsent(stack.getItem(), key -> new ArrayList<>()).add(tabName);
                }
            }
        } catch (RuntimeException ignored) {
            // Tabs are not registered on a dedicated server; the preview never runs there anyway.
        }
        return map;
    }

    private static final Pattern MOD_NAME_SEPARATOR = Pattern.compile("(?=[A-Z_-])|\\s+");
    private static final int CACHE_LIMIT = 8192;

    private static final Map<Item, List<String>> TAG_CACHE = new HashMap<>();
    private static final Map<Item, List<String>> TOOLTIP_CACHE = new HashMap<>();
    private static final Map<String, List<String>> MOD_CACHE = new HashMap<>();
    @Nullable
    private static Map<Item, List<String>> CREATIVE_TABS;
}
