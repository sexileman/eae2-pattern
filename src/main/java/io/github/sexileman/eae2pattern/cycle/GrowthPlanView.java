package io.github.sexileman.eae2pattern.cycle;
/** Per-plan transient attachment; no global map, identity/hash cache or lifetime limit. */
public interface GrowthPlanView {
    net.minecraft.nbt.CompoundTag eae2$diagram();
    void eae2$diagram(net.minecraft.nbt.CompoundTag data);
    GrowthCraftingPlan eae2$growthPlan();
    void eae2$growthPlan(GrowthCraftingPlan details);
}
