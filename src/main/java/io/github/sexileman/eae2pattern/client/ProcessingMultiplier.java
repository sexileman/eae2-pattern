package io.github.sexileman.eae2pattern.client;

/** Shared validation for the editable processing amount. Empty/invalid text never silently becomes one. */
public final class ProcessingMultiplier {
    public static final int DEFAULT = 1;
    public static final int MAX = 65_536;
    public static final int[] PRESETS = {1, 2, 4, 8, 16, 32, 64};
    private ProcessingMultiplier() {}

    public static boolean acceptsText(String value) {
        return value.length() <= 5 && value.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /** Zero is the invalid sentinel and can never be sent to an encoder. */
    public static int parse(String value) {
        if (value.isEmpty() || !acceptsText(value)) return 0;
        int result = Integer.parseInt(value);
        return result >= 1 && result <= MAX ? result : 0;
    }
}
