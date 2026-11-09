package io.github.sexileman.eae2pattern.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.*;
import appeng.crafting.inv.ListCraftingInventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface GrowthJobAccessor {
    @Accessor("waitingFor")
    ListCraftingInventory eae2$waiting();

    @Accessor("tasks")
    Map<IPatternDetails, Object> eae2$tasks();

    @Accessor("timeTracker")
    ElapsedTimeTracker eae2$tracker();

    @Accessor("link")
    CraftingLink eae2$link();

    @Accessor("finalOutput")
    GenericStack eae2$output();

    @Accessor("remainingAmount")
    long eae2$remaining();

    @Accessor("remainingAmount")
    void eae2$remaining(long value);
}
