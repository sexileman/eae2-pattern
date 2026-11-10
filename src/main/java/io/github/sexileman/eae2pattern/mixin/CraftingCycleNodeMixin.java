package io.github.sexileman.eae2pattern.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.*;

import io.github.sexileman.eae2pattern.cycle.*;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = CraftingTreeNode.class, remap = false)
public abstract class CraftingCycleNodeMixin implements CycleNode {
    @Shadow @Final private CraftingCalculation job;
    @Shadow @Final private CraftingTreeProcess parent;
    @Shadow @Final private AEKey what;

    public AEKey eae2$cycleKey() {
        return what;
    }

    public CraftingTreeProcess eae2$cycleParent() {
        return parent;
    }

    @Redirect(
            method = "buildChildPatterns",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/crafting/CraftingTreeProcess;notRecursive(Lappeng/api/crafting/IPatternDetails;)Z"))
    private boolean eae2$observeRecursion(CraftingTreeProcess process, IPatternDetails candidate) {
        boolean accepted = ((CycleProcessAccessor) (Object) process).eae2$notRecursive(candidate);
        if (!accepted)
            ((CycleCalculation) (Object) job)
                    .eae2$cycles()
                    .rejected((CraftingTreeNode) (Object) this, candidate);
        return accepted;
    }
}
