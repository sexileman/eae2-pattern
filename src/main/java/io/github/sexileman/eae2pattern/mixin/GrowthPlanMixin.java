package io.github.sexileman.eae2pattern.mixin;
import appeng.crafting.CraftingPlan;
import io.github.sexileman.eae2pattern.cycle.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
/** Keep the exact concrete AE2 record required by AE2CT and attach phase data separately. */
@Mixin(value=CraftingPlan.class,remap=false)
public abstract class GrowthPlanMixin implements GrowthPlanView {
    @Unique private net.minecraft.nbt.CompoundTag eae2$diagram;
    public net.minecraft.nbt.CompoundTag eae2$diagram(){return eae2$diagram;}
    public void eae2$diagram(net.minecraft.nbt.CompoundTag data){eae2$diagram=data;}
    @Unique private GrowthCraftingPlan eae2$growth;
    public GrowthCraftingPlan eae2$growthPlan(){return eae2$growth;}
    public void eae2$growthPlan(GrowthCraftingPlan details){eae2$growth=details;}
}
