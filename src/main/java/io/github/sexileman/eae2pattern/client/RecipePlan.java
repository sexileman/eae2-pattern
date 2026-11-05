package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.pattern.PatternKind;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        List<PatternSpec> specs = new ArrayList<>();
        for (Entry entry : entries) {
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
        private List<String> inputTexts;
        @Nullable
        private List<String> outputTexts;
        @Nullable
        private List<String> modIds;
        @Nullable
        private List<String> tags;

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

        /** Names, ids and namespaces of everything the recipe consumes or merely requires. */
        public List<String> inputTexts() {
            if (this.inputTexts == null) {
                List<String> texts = new ArrayList<>(textsOf(inputs));
                texts.addAll(textsOf(catalysts));
                this.inputTexts = List.copyOf(texts);
            }
            return this.inputTexts;
        }

        /** Names, ids and namespaces of everything the recipe produces. */
        public List<String> outputTexts() {
            if (this.outputTexts == null) {
                this.outputTexts = textsOf(outputs);
            }
            return this.outputTexts;
        }

        public List<String> modIds() {
            if (this.modIds == null) {
                List<String> mods = new ArrayList<>(8);
                for (Ingredient ingredient : inputs) {
                    mods.add(ingredient.spec().id().getNamespace());
                }
                for (Ingredient ingredient : catalysts) {
                    mods.add(ingredient.spec().id().getNamespace());
                }
                for (Ingredient ingredient : outputs) {
                    mods.add(ingredient.spec().id().getNamespace());
                }
                this.modIds = List.copyOf(mods);
            }
            return this.modIds;
        }

        public List<String> tags() {
            if (this.tags == null) {
                List<String> tags = new ArrayList<>();
                for (Ingredient ingredient : inputs) {
                    collectTags(ingredient.spec(), tags);
                }
                for (Ingredient ingredient : catalysts) {
                    collectTags(ingredient.spec(), tags);
                }
                for (Ingredient ingredient : outputs) {
                    collectTags(ingredient.spec(), tags);
                }
                this.tags = List.copyOf(tags);
            }
            return this.tags;
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

    private static List<String> textsOf(List<Ingredient> ingredients) {
        List<String> texts = new ArrayList<>(ingredients.size() * 4);
        for (Ingredient ingredient : ingredients) {
            StackSpec spec = ingredient.spec();
            ItemStack stack = spec.toItemStack();
            if (!stack.isEmpty()) {
                texts.add(stack.getHoverName().getString());
            }
            texts.add(spec.id().toString());
            texts.add(spec.id().getNamespace());
            texts.add(spec.id().getPath());
        }
        return List.copyOf(texts);
    }

    private static void collectTags(StackSpec spec, List<String> into) {
        ItemStack stack = spec.toItemStack();
        if (stack.isEmpty()) {
            return;
        }
        for (String tag : tagsOf(stack.getItem())) {
            if (!into.contains(tag)) {
                into.add(tag);
            }
        }
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
        if (TAG_CACHE.size() < TAG_CACHE_LIMIT) {
            TAG_CACHE.put(item, result);
        }
        return result;
    }

    private static final Map<Item, List<String>> TAG_CACHE = new HashMap<>();
    private static final int TAG_CACHE_LIMIT = 4096;
}
