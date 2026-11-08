package io.github.sexileman.eae2pattern.mixin;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.*;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import io.github.sexileman.eae2pattern.cycle.*;
import io.github.sexileman.eae2pattern.EAE2Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=CraftingCpuLogic.class,remap=false)
public abstract class GrowthCpuMixin implements GrowthCpuView {
    @Shadow @Final private ListCraftingInventory inventory;
    @Shadow @Final private CraftingCPUCluster cluster;
    @Shadow private ExecutingCraftingJob job;
    @Shadow public abstract void cancel();
    @Unique private GrowthRun eae2$growth;
    public ExecutingCraftingJob eae2$job(){return job;}
    public ListCraftingInventory eae2$inventory(){return inventory;}
    public CraftingCPUCluster eae2$cluster(){return cluster;}
    @Invoker("finishJob") public abstract void eae2$finish(boolean success);
    @Inject(method="trySubmitJob",at=@At("RETURN"))
    private void eae2$startGrowth(IGrid grid,ICraftingPlan plan,IActionSource source,ICraftingRequester requester,CallbackInfoReturnable<ICraftingSubmitResult> ci){
        if(plan instanceof GrowthCraftingPlan growth&&ci.getReturnValue().successful()&&job!=null){eae2$growth=new GrowthRun(growth);cluster.markDirty();}
    }
    @Inject(method="executeCrafting",at=@At("HEAD"),cancellable=true)
    private void eae2$stepGrowth(int max,CraftingService service,IEnergyService energy,Level level,CallbackInfoReturnable<Integer> ci){
        if(eae2$growth!=null&&job!=null)try{ci.setReturnValue(eae2$growth.step(this,max,service,energy,level));}
        catch(RuntimeException failure){EAE2Pattern.LOGGER.error("Experimental cycle execution cancelled; native inventory return retained",failure);cancel();ci.setReturnValue(0);}
    }
    @Inject(method="insert",at=@At("HEAD"),cancellable=true)
    private void eae2$receiveGrowth(AEKey key,long amount,Actionable action,CallbackInfoReturnable<Long> ci){if(eae2$growth!=null&&job!=null)ci.setReturnValue(eae2$growth.accept(this,key,amount,action));}
    @Inject(method="finishJob",at=@At("HEAD"))
    private void eae2$clearGrowth(boolean success,CallbackInfo ci){eae2$growth=null;}
    @Inject(method="writeToNBT",at=@At("RETURN"))
    private void eae2$saveGrowth(CompoundTag tag,CallbackInfo ci){if(eae2$growth!=null&&job!=null)tag.put(GrowthRun.TAG,eae2$growth.save());else tag.remove(GrowthRun.TAG);}
    @Inject(method="readFromNBT",at=@At("RETURN"))
    private void eae2$loadGrowth(CompoundTag tag,CallbackInfo ci){
        eae2$growth=null;if(job!=null&&tag.contains(GrowthRun.TAG,10))try{eae2$growth=GrowthRun.load(tag.getCompound(GrowthRun.TAG),this);}
        catch(RuntimeException failure){EAE2Pattern.LOGGER.error("Experimental cycle state invalid; cancelling to return stored inventory",failure);cancel();}
    }
}
