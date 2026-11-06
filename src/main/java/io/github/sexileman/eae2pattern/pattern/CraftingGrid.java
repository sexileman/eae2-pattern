package io.github.sexileman.eae2pattern.pattern;

/** Maps a shaped recipe's compact row stride into the fixed three-column AE2 grid. */
public final class CraftingGrid {
    private CraftingGrid() {}
    public static int slot(int index, int recipeWidth) {
        if (recipeWidth < 1 || recipeWidth > 3 || index < 0 || index / recipeWidth >= 3)
            throw new IllegalArgumentException("Recipe does not fit a 3x3 grid");
        return (index / recipeWidth) * 3 + index % recipeWidth;
    }
}
