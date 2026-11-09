package io.github.sexileman.eae2pattern.cycle;

import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;

public interface GrowthCpuView {
    ExecutingCraftingJob eae2$job();

    ListCraftingInventory eae2$inventory();

    CraftingCPUCluster eae2$cluster();

    void eae2$finish(boolean success);
}
