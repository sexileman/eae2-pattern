package io.github.sexileman.eae2pattern.client;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Compact slot labels only; tooltips retain the exact original quantity. */
public final class StackAmountLabel {
    private static final String[] FLUID_UNITS = {"mB", "B", "kB", "MB", "GB", "TB", "PB"};
    private static final String[] ITEM_UNITS = {"", "k", "m", "b", "t", "q", "Q"};
    private StackAmountLabel() {}
    public static String format(long amount, boolean fluid) {
        if (!fluid && amount == 1) return "";
        String[] units = fluid ? FLUID_UNITS : ITEM_UNITS;
        BigDecimal value = BigDecimal.valueOf(amount);
        int unit = 0;
        while (value.compareTo(BigDecimal.valueOf(1000)) >= 0 && unit < units.length - 1) {
            value = value.movePointLeft(3); unit++;
        }
        return value.setScale(unit == 0 ? 0 : 1, RoundingMode.DOWN).stripTrailingZeros().toPlainString() + units[unit];
    }
}
