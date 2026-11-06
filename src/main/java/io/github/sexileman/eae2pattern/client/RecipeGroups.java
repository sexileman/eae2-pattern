package io.github.sexileman.eae2pattern.client;

import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Function;

/** Groups contain references to entries; selection and generation always operate on unique source entries. */
public final class RecipeGroups {
    private RecipeGroups() {}
    public record Key(String id, Component label) {}
    public record Group(Key key, List<RecipePlan.Entry> entries) {}
    public record Row(Group group, RecipePlan.Entry entry) {
        public boolean header() { return entry == null; }
    }

    public static List<Key> keys(RecipePlan.Entry e, int mode) {
        if (mode == 1) {
            Component label = e.label();
            if (!e.outputs().isEmpty()) {
                var spec = e.outputs().get(Math.max(0, e.mainOutputIndex())).spec();
                if (spec.tag() != null && spec.tag().contains("Configuration"))
                    label = label.copy().append(" [" + spec.tag().getInt("Configuration") + "]");
            }
            return List.of(new Key("output:" + e.outputKey(), label));
        }
        List<String> tags = (mode == 3 ? e.inputHaystack() : e.outputHaystack()).tags();
        if (tags.isEmpty()) return List.of(new Key("untagged", Component.translatable("eae2_pattern.workbench.untagged")));
        return tags.stream().distinct().sorted().map(tag -> new Key("tag:" + tag, Component.literal(tag))).toList();
    }

    public static final class Builder {
        private final List<RecipePlan.Entry> source;
        private final Function<RecipePlan.Entry, List<Key>> keys;
        private final Map<String, MutableGroup> groups = new LinkedHashMap<>();
        private int cursor;
        public Builder(List<RecipePlan.Entry> source, int mode) { this(source, e -> RecipeGroups.keys(e, mode)); }
        public Builder(List<RecipePlan.Entry> source, Function<RecipePlan.Entry, List<Key>> keys) {
            this.source = source; this.keys = keys;
        }
        public boolean step() {
            long deadline = System.nanoTime() + 4_000_000L;
            int work = 0;
            while (cursor < source.size() && work++ < 1024 && System.nanoTime() < deadline) {
                RecipePlan.Entry entry = source.get(cursor++);
                Set<String> seen = new HashSet<>();
                for (Key key : keys.apply(entry)) if (seen.add(key.id()))
                    groups.computeIfAbsent(key.id(), ignored -> new MutableGroup(key)).entries.add(entry);
            }
            return cursor >= source.size();
        }
        public List<Group> finish() {
            return groups.values().stream().map(g -> new Group(g.key, List.copyOf(g.entries)))
                    .sorted(Comparator.comparing(g -> g.key().id())).toList();
        }
    }
    private static final class MutableGroup {
        final Key key;
        final List<RecipePlan.Entry> entries = new ArrayList<>();
        MutableGroup(Key key) { this.key = key; }
    }
    public static List<Row> rows(List<RecipePlan.Entry> entries, List<Group> groups, Set<String> expanded, int mode) {
        List<Row> rows = new ArrayList<>();
        if (mode == 0) { entries.forEach(e -> rows.add(new Row(null, e))); return rows; }
        for (Group group : groups) {
            rows.add(new Row(group, null));
            if (expanded.contains(group.key().id())) group.entries().forEach(e -> rows.add(new Row(group, e)));
        }
        return rows;
    }
}
