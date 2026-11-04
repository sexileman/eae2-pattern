package io.github.sexileman.eae2pattern.pattern;

import net.minecraft.resources.ResourceLocation;

/**
 * The stack flavours we can move over the wire without depending on AE2 key classes on the client side.
 * Items and fluids cover every JEI recipe we care about in the first version; chemicals can be added later.
 */
public enum StackType {
    ITEM,
    FLUID;

    private static final StackType[] VALUES = values();

    public static StackType byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : ITEM;
    }

    public ResourceLocation id() {
        return new ResourceLocation("eae2_pattern", name().toLowerCase(java.util.Locale.ROOT));
    }
}
