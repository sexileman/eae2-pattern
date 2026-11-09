package io.github.sexileman.eae2pattern.cycle;

import appeng.api.stacks.GenericStack;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Immutable client-facing model. NBT field names are confined to this decoding boundary. */
public record CycleDiagramSnapshot(
        State state,
        @org.jetbrains.annotations.Nullable GenericStack target,
        List<List<GenericStack>> paths,
        List<Recipe> recipes,
        List<Phase> phases,
        List<GenericStack> reserved,
        List<GenericStack> materialPath,
        List<Material> materials,
        MaterialReason materialReason) {
    public enum State {
        LOADING,
        NONE,
        PLANNED,
        DETECTED,
        UNSUPPORTED,
        TOO_LARGE,
        TIMEOUT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static State parse(String id) {
            try {
                return valueOf(id.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return UNSUPPORTED;
            }
        }
    }

    public enum MaterialReason {
        UNKNOWN,
        SEED,
        EXTERNAL,
        READY,
        DISABLED,
        CPU,
        RUNTIME;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static MaterialReason parse(String id) {
            try {
                return valueOf(id.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return UNKNOWN;
            }
        }
    }

    public enum MaterialKind {
        NONE,
        CIRCULATING,
        EXTERNAL
    }

    public record Recipe(List<GenericStack> inputs, List<GenericStack> outputs) {
        public Recipe {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
        }
    }

    public record Phase(int recipe, long times) {}

    public record Material(
            GenericStack required, long available, long missing, MaterialKind kind) {}

    public CycleDiagramSnapshot {
        paths = paths.stream().map(List::copyOf).toList();
        recipes = List.copyOf(recipes);
        phases = List.copyOf(phases);
        reserved = List.copyOf(reserved);
        materialPath = List.copyOf(materialPath);
        materials = List.copyOf(materials);
    }

    public static CycleDiagramSnapshot state(State state) {
        return new CycleDiagramSnapshot(
                state,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                MaterialReason.UNKNOWN);
    }

    private static List<GenericStack> stacks(CompoundTag tag, String key, int maximum) {
        var result = new ArrayList<GenericStack>();
        var list = tag.getList(key, 10);
        for (int i = 0; i < Math.min(list.size(), maximum); i++) {
            var stack = GenericStack.readTag(list.getCompound(i));
            if (stack != null && stack.amount() > 0) result.add(stack);
        }
        return List.copyOf(result);
    }

    public static CycleDiagramSnapshot decode(CompoundTag data) {
        var paths = new ArrayList<List<GenericStack>>();
        var pathTags = data.getList("paths", 10);
        for (int i = 0; i < Math.min(pathTags.size(), CycleLimits.DIAGRAM_PATHS); i++)
            paths.add(stacks(pathTags.getCompound(i), "nodes", CycleLimits.DIAGRAM_PATH_NODES));
        var recipes = new ArrayList<Recipe>();
        var recipeTags = data.getList("recipes", 10);
        for (int i = 0; i < Math.min(recipeTags.size(), CycleLimits.DIAGRAM_RECIPES); i++) {
            var row = recipeTags.getCompound(i);
            recipes.add(
                    new Recipe(
                            stacks(row, "in", CycleLimits.MAX_SLOTS),
                            stacks(row, "out", CycleLimits.MAX_SLOTS)));
        }
        var phases = new ArrayList<Phase>();
        var phaseTags = data.getList("phases", 10);
        for (int i = 0; i < Math.min(phaseTags.size(), CycleLimits.MAX_STAGES); i++) {
            var row = phaseTags.getCompound(i);
            int recipe = row.getInt("recipe");
            long times = row.getLong("times");
            if (recipe >= 0
                    && recipe < recipes.size()
                    && times > 0
                    && times <= CycleLimits.MAX_OPERATIONS) phases.add(new Phase(recipe, times));
        }
        var materials = new ArrayList<Material>();
        var materialTags = data.getList("material_needs", 10);
        for (int i = 0; i < Math.min(materialTags.size(), CycleLimits.MATERIAL_TYPES); i++) {
            var row = materialTags.getCompound(i);
            var stack = GenericStack.readTag(row.getCompound("stack"));
            if (stack != null && stack.amount() > 0)
                materials.add(
                        new Material(
                                stack,
                                Math.max(0, row.getLong("available")),
                                Math.max(0, row.getLong("missing")),
                                row.getBoolean("circulating")
                                        ? MaterialKind.CIRCULATING
                                        : MaterialKind.EXTERNAL));
        }
        return new CycleDiagramSnapshot(
                State.parse(data.getString("status")),
                GenericStack.readTag(data.getCompound("target")),
                paths,
                recipes,
                phases,
                stacks(data, "reserved", CycleLimits.MATERIAL_TYPES),
                stacks(data, "material_path", CycleLimits.DIAGRAM_PATH_NODES),
                materials,
                MaterialReason.parse(data.getString("material_reason")));
    }
}
