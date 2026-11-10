package io.github.sexileman.eae2pattern.cycle;

import java.util.*;
import java.util.function.ToLongFunction;

/** Reserve the entire external budget up front; no expected future output counts as stock. */
public final class GrowthMaterials {
    private GrowthMaterials() {}

    public static <K> Map<K, Long> budget(
            List<Map<K, Long>> inputs, long[] times, Set<K> internal, Map<K, Long> seeds) {
        if (inputs.size() != times.length)
            throw new IllegalArgumentException("Invalid recipe budget");
        var result = new LinkedHashMap<K, Long>();
        for (var seed : seeds.entrySet()) {
            if (seed.getValue() <= 0) throw new IllegalArgumentException("Invalid seed");
            result.put(seed.getKey(), seed.getValue());
        }
        for (int i = 0; i < times.length; i++) {
            if (times[i] < 0) throw new IllegalArgumentException("Invalid execution count");
            for (var input : inputs.get(i).entrySet()) {
                if (input.getValue() <= 0)
                    throw new IllegalArgumentException("Invalid recipe amount");
                if (!internal.contains(input.getKey())) {
                    long amount = Math.multiplyExact(input.getValue(), times[i]);
                    if (amount > 0) result.merge(input.getKey(), amount, Math::addExact);
                }
            }
        }
        return Map.copyOf(result);
    }

    public static <K> Map<K, Long> missing(Map<K, Long> budget, ToLongFunction<K> stock) {
        var result = new LinkedHashMap<K, Long>();
        for (var entry : budget.entrySet()) {
            long available = Math.max(0, stock.applyAsLong(entry.getKey()));
            if (available < entry.getValue())
                result.put(entry.getKey(), entry.getValue() - available);
        }
        return Map.copyOf(result);
    }

    public static long startup(long input, long output, long nextInput) {
        if (input <= 0 || output <= 0 || nextInput <= 0)
            throw new IllegalArgumentException("Invalid startup unit");
        return Math.multiplyExact(input, nextInput / TwoRecipeGrowth.gcd(output, nextInput));
    }
}
