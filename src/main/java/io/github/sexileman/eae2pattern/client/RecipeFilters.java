package io.github.sexileman.eae2pattern.client;

/**
 * The state of the filter rail: two search boxes plus the condition toggles.
 *
 * <p>Kept separate from the screen so the matching rules are readable on their own and testable without a
 * {@code Minecraft} instance. Every condition is ANDed; a condition that is off does not care either way.</p>
 */
public record RecipeFilters(
        ItemSearchQuery products,
        ItemSearchQuery ingredients,
        boolean onlyNeverConsumed,
        boolean onlyProbabilisticInput,
        boolean onlyProbabilisticOutput,
        boolean onlyProbabilisticByproduct,
        boolean onlySelected
) {

    public static RecipeFilters none() {
        return new RecipeFilters(ItemSearchQuery.empty(), ItemSearchQuery.empty(),
                false, false, false, false, false);
    }

    public boolean isEmpty() {
        return products.isEmpty() && ingredients.isEmpty()
                && !onlyNeverConsumed && !onlyProbabilisticInput && !onlyProbabilisticOutput
                && !onlyProbabilisticByproduct && !onlySelected;
    }

    /** True when the row has to be hidden from the list. */
    public boolean rejects(RecipePlan.Entry entry) {
        if (onlySelected && !entry.isSelected()) {
            return true;
        }
        if (onlyNeverConsumed && !entry.hasNeverConsumedInput()) {
            return true;
        }
        if (onlyProbabilisticInput && !entry.hasProbabilisticInput()) {
            return true;
        }
        if (onlyProbabilisticOutput && !entry.hasProbabilisticOutput()) {
            return true;
        }
        if (onlyProbabilisticByproduct && !entry.hasProbabilisticByproduct()) {
            return true;
        }
        if (!products.isEmpty() && !products.matches(entry, ItemSearchQuery.Side.OUTPUT)) {
            return true;
        }
        return !ingredients.isEmpty() && !ingredients.matches(entry, ItemSearchQuery.Side.INPUT);
    }
}
