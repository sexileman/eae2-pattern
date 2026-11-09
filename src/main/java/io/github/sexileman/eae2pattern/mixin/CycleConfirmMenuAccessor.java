package io.github.sexileman.eae2pattern.mixin;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.menu.me.crafting.CraftConfirmMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CraftConfirmMenu.class, remap = false)
public interface CycleConfirmMenuAccessor {
    @Accessor("result")
    ICraftingPlan eae2$currentPlan();
}
