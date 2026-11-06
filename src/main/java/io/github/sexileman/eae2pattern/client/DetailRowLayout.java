package io.github.sexileman.eae2pattern.client;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/** Variable-height detail rows use identical offsets for drawing, scrolling and hit-testing. */
public final class DetailRowLayout {
    public static final int TEXT_HEIGHT = 18, SLOT_HEIGHT = 30;
    private final int[] offsets;
    private DetailRowLayout(int[] offsets) { this.offsets = offsets; }
    public static <T> DetailRowLayout of(List<T> rows, Predicate<T> material) {
        int[] offsets = new int[rows.size() + 1];
        for (int i = 0; i < rows.size(); i++) offsets[i + 1] = offsets[i] + (material.test(rows.get(i)) ? SLOT_HEIGHT : TEXT_HEIGHT);
        return new DetailRowLayout(offsets);
    }
    public int size() { return offsets.length - 1; }
    public int top(int index) { return offsets[index]; }
    public int height() { return offsets[offsets.length - 1]; }
    public int indexAt(double offset) {
        if (offset < 0 || offset >= height()) return -1;
        int index = Arrays.binarySearch(offsets, (int) offset);
        return index >= 0 ? index : -index - 2;
    }
}
