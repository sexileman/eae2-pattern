package io.github.sexileman.eae2pattern.client;

import java.util.ArrayList;
import java.util.List;

/** Two independently scoped rule sets. Exclusions always win and sides combine with AND. */
public record WildcardRules(Side inputs, Side outputs) {
    public static <T> boolean evaluate(List<T> slots, boolean every,
                                      java.util.function.Predicate<List<T>> include,
                                      java.util.function.Predicate<List<T>> exclude) {
        if (exclude != null && exclude.test(slots)) return false;
        if (slots.isEmpty()) return include == null;
        if (include == null) return true;
        return every ? slots.stream().allMatch(slot -> include.test(List.of(slot))) : include.test(slots);
    }
    public record Side(ItemSearchQuery include, ItemSearchQuery exclude, boolean everySlot, int scope) {
        public static Side of(String include, String exclude, boolean everySlot, int scope) {
            return new Side(ItemSearchQuery.parse(include), ItemSearchQuery.parse(exclude), everySlot, scope);
        }
        boolean matches(List<Ingredient> source) {
            if (include.isEmpty() && exclude.isEmpty()) return true;
            var slots = source.stream().filter(i -> scope == 0 || (scope == 1) == i.spec().isItem()).toList();
            return evaluate(slots, everySlot,
                    include.isEmpty() ? null : group -> include.matches(RecipePlan.Haystack.of(group)),
                    exclude.isEmpty() ? null : group -> exclude.matches(RecipePlan.Haystack.of(group)));
        }
    }
    public boolean matches(RecipePlan.Entry entry, boolean searchTools) {
        var in = new ArrayList<>(entry.inputs()); if (searchTools) in.addAll(entry.catalysts());
        if (!searchTools) in.removeIf(Ingredient::neverConsumed);
        return inputs.matches(in) && outputs.matches(entry.outputs());
    }
}
