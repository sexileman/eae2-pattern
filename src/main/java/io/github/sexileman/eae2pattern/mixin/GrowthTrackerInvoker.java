package io.github.sexileman.eae2pattern.mixin;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ElapsedTimeTracker.class, remap = false)
public interface GrowthTrackerInvoker {
    @Invoker("decrementItems")
    void eae2$decrement(long amount, AEKeyType type);
}
