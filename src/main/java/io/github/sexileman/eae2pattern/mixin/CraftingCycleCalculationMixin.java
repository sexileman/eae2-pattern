package io.github.sexileman.eae2pattern.mixin;

import appeng.api.networking.crafting.*;
import appeng.crafting.CraftingCalculation;

import io.github.sexileman.eae2pattern.cycle.*;

import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCycleCalculationMixin implements CycleCalculation {
    @Shadow @Final private Level level;
    @Shadow @Final private ICraftingSimulationRequester simRequester;
    @Shadow @Final private appeng.crafting.inv.NetworkCraftingSimulationState networkInv;
    @Unique private CycleCapture eae2$capture;

    public CycleCapture eae2$cycles() {
        if (eae2$capture == null) eae2$capture = new CycleCapture();
        return eae2$capture;
    }

    @Inject(method = "run", at = @At("RETURN"), cancellable = true)
    private void eae2$reportCycles(CallbackInfoReturnable<ICraftingPlan> cir) {
        try {
            var original = cir.getReturnValue();
            CycleLog.detected(original, eae2$capture);
            var replacement =
                    GrowthFallback.replace(level, simRequester, networkInv, original, eae2$capture);
            if (replacement != original) cir.setReturnValue(replacement);
            if ((Object) replacement instanceof GrowthPlanView view)
                view.eae2$diagram(CycleDiagramData.build(replacement, eae2$capture));
            CycleReports.publish(level, simRequester, cir.getReturnValue(), eae2$capture);
        } catch (RuntimeException | LinkageError diagnosticFailure) {
            io.github.sexileman.eae2pattern.EAE2Pattern.LOGGER.warn(
                    "[EAE2/Cycles] event=planning_or_diagnostics_failed", diagnosticFailure);
        } finally {
            eae2$capture = null;
        }
    }
}
