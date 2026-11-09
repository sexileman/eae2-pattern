package io.github.sexileman.eae2pattern.cycle;

/** Bound circulating working capital by this request, rather than draining all network stock. */
public final class GrowthWorkingStock {
    private GrowthWorkingStock() {}

    public static long reserve(
            long available, long seed, long requested, long outputPerCraft, long inputPerCraft) {
        if (available < seed
                || seed < 0
                || requested < 0
                || outputPerCraft <= 0
                || inputPerCraft <= 0) throw new IllegalArgumentException("Invalid working stock");
        long crafts = requested / outputPerCraft + (requested % outputPerCraft == 0 ? 0 : 1);
        long useful = Math.addExact(seed, Math.multiplyExact(crafts, inputPerCraft));
        return Math.min(available, useful);
    }
}
