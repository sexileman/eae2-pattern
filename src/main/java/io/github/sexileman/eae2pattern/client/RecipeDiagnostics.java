package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.pattern.PatternKind;

public final class RecipeDiagnostics {
    private RecipeDiagnostics() {}
    public static String skipReason(RecipePlan.Entry entry, int policy, boolean dropTools) {
        if (entry.outputs().isEmpty()) return "skip_no_output";
        if (policy != 0 && entry.mainOutputIndex() < 0) return entry.hasUnknownOutputChance()?"skip_unknown_output":"skip_no_guaranteed";
        if (entry.kind() == PatternKind.PROCESSING) {
            long count = (dropTools ? 0 : entry.catalysts().size())
                    + entry.inputs().stream().filter(i -> !dropTools || !i.neverConsumed()).count();
            if (count == 0) return "skip_no_input";
        }
        if (!entry.canEncode(policy, dropTools)) {
            return "skip_slots";
        }
        return "";
    }
    public static boolean sameMaterial(io.github.sexileman.eae2pattern.pattern.StackSpec a, io.github.sexileman.eae2pattern.pattern.StackSpec b) {
        return a.type() == b.type() && a.id().equals(b.id()) && java.util.Objects.equals(a.tag(), b.tag());
    }
}
