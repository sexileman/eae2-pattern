package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.pattern.PatternKind;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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

        public Entry(PatternSpec spec, Component label, ItemStack icon, boolean selected) {
            this.spec = spec;
            this.label = label;
            this.icon = icon;
            this.selected = selected;
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
}
