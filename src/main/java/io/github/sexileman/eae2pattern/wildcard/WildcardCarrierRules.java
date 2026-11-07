package io.github.sexileman.eae2pattern.wildcard;

/** Pure limits and allocation plan; build a complete plan before changing either inventory slot. */
public final class WildcardCarrierRules {
    private WildcardCarrierRules() {}
    public static boolean isResetRecipe(String recipeId) { return "eae2_pattern:machine_wildcard_pattern_reset".equals(recipeId); }
    public static int maxStackSize(boolean hasNbt) { return hasNbt ? 1 : 64; }
    public static boolean validCount(int count, boolean hasNbt) { return count > 0 && count <= maxStackSize(hasNbt); }
    public record WritePlan(int remainingBlanks, int destination) {
        public boolean splits() { return remainingBlanks > 0; }
    }
    public static WritePlan writePlan(int count, boolean hasNbt, int emptySlot) {
        if (!validCount(count, hasNbt)) throw new IllegalArgumentException("Invalid held wildcard stack");
        if (count == 1) return new WritePlan(0, -1);
        if (emptySlot < 0 || emptySlot >= 36) throw new IllegalArgumentException("No empty inventory slot for encoded wildcard; no blank consumed");
        return new WritePlan(count - 1, emptySlot);
    }
}
