package io.github.sexileman.eae2pattern.cycle;

import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingTreeProcess;

public interface CycleNode {
    AEKey eae2$cycleKey();

    CraftingTreeProcess eae2$cycleParent();
}
