package io.github.sexileman.eae2pattern.client;

/** Immutable snapshot used for a time-sliced filtering pass. Conditions combine with AND. */
public record RecipeFilters(ItemSearchQuery products, ItemSearchQuery ingredients,
                            int inputMode, int outputMode, boolean onlySelected) {
    public static RecipeFilters none() {
        return new RecipeFilters(ItemSearchQuery.empty(), ItemSearchQuery.empty(), 0, 0, false);
    }
    public boolean rejects(RecipePlan.Entry e) {
        if (onlySelected && !e.isSelected()) return true;
        if (!ingredients.isEmpty() && !ingredients.matches(e, ItemSearchQuery.Side.INPUT)) return true;
        if (!products.isEmpty() && !products.matches(e, ItemSearchQuery.Side.OUTPUT)) return true;
        boolean inputMatches = switch (inputMode) {
            case 1 -> e.hasNeverConsumedInput();
            case 2 -> e.hasProbabilisticInput();
            case 3 -> !e.hasNeverConsumedInput() && !e.hasProbabilisticInput();
            default -> true;
        };
        boolean outputMatches = switch (outputMode) {
            case 1 -> !e.hasProbabilisticOutput() && !e.hasUnknownOutputChance();
            case 2 -> e.hasByproduct();
            case 3 -> e.hasProbabilisticByproduct();
            case 4 -> e.allOutputsProbabilistic();
            case 5 -> e.hasProbabilisticOutput();
            default -> true;
        };
        return !inputMatches || !outputMatches;
    }
}
