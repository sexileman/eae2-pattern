package io.github.sexileman.eae2pattern.client;
import io.github.sexileman.eae2pattern.wildcard.CircuitCondition;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import java.util.*;
/** One scan of original slots per screen; no NBT reads in subsequent filtering passes. */
final class CircuitCatalog {
    final SortedMap<Integer, StackSpec> icons = new TreeMap<>();
    final Map<Integer,Integer> counts = new HashMap<>();
    private final Map<RecipePlan.Entry,Set<Integer>> entries = new IdentityHashMap<>();
    CircuitCatalog(RecipePlan plan) {
        for (var e : plan.entries()) {
            var numbers = new HashSet<Integer>();
            for (var slots : List.of(e.inputs(), e.catalysts())) for (var slot : slots) {
                int n = CircuitCondition.configuration(slot.spec());
                if (n >= 0) { numbers.add(n); icons.putIfAbsent(n, slot.spec().withAmount(1)); }
            }
            var values = Set.copyOf(numbers); if(!values.isEmpty())entries.put(e, values);
            for (int n : values) counts.merge(n, 1, Integer::sum);
        }
    }
    boolean available() { return !icons.isEmpty(); }
    boolean matches(RecipePlan.Entry entry, CircuitCondition condition) { return condition.matches(entries.getOrDefault(entry,Set.of())); }
    static net.minecraft.network.chat.Component label(CircuitCondition condition) {
        String value = condition.mode() == 0 ? "all" : condition.mode() == 1 ? "none" : "selected";
        return net.minecraft.network.chat.Component.translatable("eae2_pattern.circuit."+value,
            condition.configurations().stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")));
    }
}
