package io.github.sexileman.eae2pattern.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public interface GrowthProgressAccessor {
    @Accessor("value")
    long eae2$value();

    @Accessor("value")
    void eae2$value(long value);
}
