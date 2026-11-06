package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.pattern.StackSpec;

/**
 * One ingredient of a recipe row, plus what JEI let us learn about it.
 *
 * <p>Three things a plain {@link StackSpec} cannot express and the preview needs:</p>
 * <ul>
 *   <li>{@code consumed} - JEI's {@code CATALYST} role means "required, but not used up". Excluded by default;
 *       users can explicitly retain them in a processing pattern.</li>
 *   <li>{@code chance} - a GregTech machine can consume an input with a probability, or produce a by-product with
 *       one. AE2 patterns are deterministic, so the preview has to show which rows are affected before the player
 *       encodes them.</li>
 *   <li>the distinction between "always", "sometimes" and "never" for an input, which is what the filter buttons
 *       are about.</li>
 * </ul>
 */
public record Ingredient(StackSpec spec, float chance, boolean consumed) {

    /** Only 100% is guaranteed; 99.99% remains probabilistic. */
    public static final float CERTAIN = 1.0F;

    public Ingredient {
        chance = Math.max(0.0F, Math.min(1.0F, chance));
    }

    public static Ingredient consumed(StackSpec spec) {
        return new Ingredient(spec, 1.0F, true);
    }

    public static Ingredient consumed(StackSpec spec, float chance) {
        return new Ingredient(spec, chance, true);
    }

    public static Ingredient notConsumed(StackSpec spec) {
        return new Ingredient(spec, 0.0F, false);
    }

    /** A product, which is "consumed" in the sense of being a real ingredient of the pattern rather than a catalyst. */
    public static Ingredient produced(StackSpec spec, float chance) {
        return new Ingredient(spec, chance, true);
    }

    /** Consumed every time. */
    public boolean certain() {
        return consumed && chance >= CERTAIN;
    }

    /** Consumed or produced only some of the time. */
    public boolean probabilistic() {
        return consumed && chance > 0.0F && chance < CERTAIN;
    }

    /** Never used up: either JEI reports it as a catalyst, or it is announced as a 0% chance input. */
    public boolean neverConsumed() {
        return !consumed || chance <= 0.0F;
    }

    public boolean guaranteedOutput() {
        return chance >= CERTAIN;
    }

    public Ingredient withSpec(StackSpec replacement) {
        return new Ingredient(replacement, chance, consumed);
    }

    /** Short label for the percentage badges, e.g. {@code 90%}. */
    public String chanceText() {
        if (chance >= CERTAIN) {
            return "100%";
        }
        return new java.math.BigDecimal(Float.toString(chance)).movePointRight(2)
                .stripTrailingZeros().toPlainString() + "%";
    }
}
