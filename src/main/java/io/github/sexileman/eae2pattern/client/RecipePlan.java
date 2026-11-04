package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.pattern.PatternKind;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import net.minecraft.core.registries.BuiltInRegistries;
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

    public List<PatternSpec> selectedSpecs() {
        List<PatternSpec> specs = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.isSelected()) {
                specs.add(entry.spec());
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

    public void selectAll(boolean selected) {
        for (Entry entry : entries) {
            entry.setSelected(selected);
        }
    }

    public void selectOnly(PatternKind kind) {
        for (Entry entry : entries) {
            entry.setSelected(entry.spec().kind() == kind);
        }
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** One row of the preview list. Mutable selection state lives here so the list widget stays dumb. */
    public static final class Entry {
        private final PatternSpec spec;
        private final Component label;
        private final ItemStack icon;
        private boolean selected;

        /** Search haystacks, built on first use because most sessions never type in the search box. */
        @Nullable
        private List<String> inputTexts;
        @Nullable
        private List<String> outputTexts;
        @Nullable
        private List<String> modIds;
        @Nullable
        private List<String> tags;

        public Entry(PatternSpec spec, Component label, ItemStack icon, boolean selected) {
            this.spec = spec;
            this.label = label;
            this.icon = icon;
            this.selected = selected;
        }

        public List<StackSpec> inputs() {
            return spec.inputs();
        }

        public List<StackSpec> outputs() {
            return spec.outputs();
        }

        /** Names, ids and namespaces of everything the recipe consumes. */
        public List<String> inputTexts() {
            if (this.inputTexts == null) {
                this.inputTexts = textsOf(spec.inputs());
            }
            return this.inputTexts;
        }

        /** Names, ids and namespaces of everything the recipe produces. */
        public List<String> outputTexts() {
            if (this.outputTexts == null) {
                this.outputTexts = textsOf(spec.outputs());
            }
            return this.outputTexts;
        }

        public List<String> modIds() {
            if (this.modIds == null) {
                this.modIds = modIdsOf(spec);
            }
            return this.modIds;
        }

        public List<String> tags() {
            if (this.tags == null) {
                this.tags = tagsOf(spec);
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
            List<StackSpec> outputs = spec.outputs();
            if (outputs.isEmpty()) {
                return spec.recipeId().toString();
            }
            StackSpec first = outputs.get(0);
            String tag = first.hasTag() ? "" + first.tag() : "";
            return first.type().name() + '|' + first.id() + '|' + tag;
        }

        public PatternSpec spec() {
            return spec;
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

    private static List<String> textsOf(List<StackSpec> specs) {
        List<String> texts = new ArrayList<>(specs.size() * 4);
        for (StackSpec spec : specs) {
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

    private static List<String> modIdsOf(PatternSpec spec) {
        List<String> mods = new ArrayList<>(2);
        for (StackSpec stack : spec.inputs()) {
            mods.add(stack.id().getNamespace());
        }
        for (StackSpec stack : spec.outputs()) {
            mods.add(stack.id().getNamespace());
        }
        return List.copyOf(mods);
    }

    private static List<String> tagsOf(PatternSpec spec) {
        List<String> tags = new ArrayList<>();
        for (StackSpec stack : spec.inputs()) {
            collectTags(stack, tags);
        }
        for (StackSpec stack : spec.outputs()) {
            collectTags(stack, tags);
        }
        return List.copyOf(tags);
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
