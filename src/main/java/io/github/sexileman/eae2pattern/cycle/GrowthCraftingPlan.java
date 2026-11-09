package io.github.sexileman.eae2pattern.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.*;

import java.util.*;

/**
 * Internal phase metadata, never returned directly to AE2 consumers. Real originals remain in the
 * CPU task table; phase batches are never sent through global plan scaling.
 */
public record GrowthCraftingPlan(
        GenericStack finalOutput,
        long bytes,
        KeyCounter usedItems,
        Map<IPatternDetails, Long> patternTimes,
        List<IPatternDetails> recipes,
        List<TwoRecipeGrowth.Stage> stages)
        implements ICraftingPlan {
    public GrowthCraftingPlan {
        patternTimes = Map.copyOf(patternTimes);
        recipes = List.copyOf(recipes);
        stages = List.copyOf(stages);
    }

    public appeng.crafting.CraftingPlan nativePlan() {
        return new appeng.crafting.CraftingPlan(
                finalOutput,
                bytes,
                false,
                false,
                usedItems,
                new KeyCounter(),
                new KeyCounter(),
                patternTimes);
    }

    public boolean simulation() {
        return false;
    }

    public boolean multiplePaths() {
        return false;
    }

    public KeyCounter emittedItems() {
        return new KeyCounter();
    }

    public KeyCounter missingItems() {
        return new KeyCounter();
    }
}
