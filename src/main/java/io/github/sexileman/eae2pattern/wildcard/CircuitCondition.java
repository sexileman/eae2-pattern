package io.github.sexileman.eae2pattern.wildcard;

import io.github.sexileman.eae2pattern.pattern.StackSpec;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Optional GT identities only; no GT classes are linked on either logical side. */
public record CircuitCondition(int mode, Set<Integer> configurations) {
    public static final CircuitCondition ALL = new CircuitCondition(0, Set.of());
    public CircuitCondition {
        configurations = Set.copyOf(configurations);
        if (mode < 0 || mode > 2 || configurations.size() > 256 || configurations.stream().anyMatch(n -> n < 0 || n > 32767)
                || (mode == 2 && configurations.isEmpty())) throw new IllegalArgumentException("Invalid circuit condition");
        if (mode != 2) configurations = Set.of();
    }
    public static int configuration(StackSpec stack) {
        if (!stack.isItem()) return -1;
        String id = stack.id().toString();
        if (!id.equals("gtceu:programmed_circuit")) return -1;
        var tag = stack.tag();
        if (tag == null || !tag.contains("Configuration")) return 0;
        if (!tag.contains("Configuration", 99)) return -1;
        int value = tag.getInt("Configuration");
        return value >= 0 && value <= 32767 ? value : -1;
    }
    public boolean matches(Set<Integer> source) {
        return mode == 0 || (mode == 1 ? source.isEmpty() : source.stream().anyMatch(configurations::contains));
    }
    public boolean matches(List<RuleRecipe.Slot> slots) {
        if (mode == 0) return true;
        boolean found = false;
        for (var slot : slots) {
            int n = configuration(slot.stack());
            if (n >= 0) { found = true; if (mode == 2 && configurations.contains(n)) return true; }
        }
        return mode == 1 && !found;
    }

    public static CircuitCondition read(CompoundTag tag) {
        if (tag == null) return ALL;
        if (tag.contains("circuit_mode") && !tag.contains("circuit_mode", 3)) throw new IllegalArgumentException("Invalid circuit mode");
        if (tag.contains("circuit_configs") && !tag.contains("circuit_configs", 11)) throw new IllegalArgumentException("Invalid circuit configurations");
        int[] values = tag.getIntArray("circuit_configs");
        if (values.length > 256) throw new IllegalArgumentException("Too many circuit configurations");
        var selected = new HashSet<Integer>(); for (int n : values) selected.add(n);
        return new CircuitCondition(tag.getInt("circuit_mode"), selected);
    }
    public void write(CompoundTag tag) {
        // Omit defaults so existing rule fingerprints remain stable.
        if (mode != 0) { tag.putInt("circuit_mode", mode); tag.putIntArray("circuit_configs", configurations.stream().sorted().mapToInt(Integer::intValue).toArray()); }
    }
}
