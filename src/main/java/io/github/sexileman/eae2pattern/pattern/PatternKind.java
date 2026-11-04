package io.github.sexileman.eae2pattern.pattern;

/**
 * The four AE2 pattern flavours we can emit. Mirrors AE2's own {@code EncodingMode}, because 1.20.1 AE2
 * can encode crafting, processing, stonecutting and smithing-table patterns.
 */
public enum PatternKind {
    CRAFTING,
    PROCESSING,
    STONECUTTING,
    SMITHING;

    private static final PatternKind[] VALUES = values();

    public static PatternKind byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : PROCESSING;
    }
}
