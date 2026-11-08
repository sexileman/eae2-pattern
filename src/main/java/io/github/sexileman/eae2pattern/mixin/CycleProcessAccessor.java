package io.github.sexileman.eae2pattern.mixin;
import appeng.api.crafting.IPatternDetails;
import appeng.crafting.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;
@Mixin(value=CraftingTreeProcess.class,remap=false)
public interface CycleProcessAccessor {
    @Accessor("parent") CraftingTreeNode eae2$parent();
    @Accessor("details") IPatternDetails eae2$details();
    @Invoker("notRecursive") boolean eae2$notRecursive(IPatternDetails details);
}
