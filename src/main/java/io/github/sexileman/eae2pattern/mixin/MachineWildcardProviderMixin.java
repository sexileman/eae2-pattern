package io.github.sexileman.eae2pattern.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import io.github.sexileman.eae2pattern.api.MachineWildcardApi;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.Set;

/** Add only our independent item after the host's existing logic, including any legacy wildcard hook. */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class MachineWildcardProviderMixin {
    @Shadow @Final private List<IPatternDetails> patterns;
    @Shadow @Final private Set<AEKey> patternInputs;
    @Shadow @Final private AppEngInternalInventory patternInventory;
    @Shadow @Final private PatternProviderLogicHost host;
    @Shadow @Final private IManagedGridNode mainNode;
    @Inject(method = "updatePatterns", at = @At("RETURN"), remap = false)
    private void eae2$expandOwnItems(CallbackInfo ci) {
        var level = host.getBlockEntity().getLevel();
        if (level == null || level.isClientSide) return;
        boolean changed = false;
        for(var stack:patternInventory)if(MachineWildcardApi.isPattern(stack)){
            io.github.sexileman.eae2pattern.wildcard.WildcardProviderRefresh.observe((PatternProviderLogic)(Object)this);break;
        }
        for (var stack : patternInventory) if (MachineWildcardApi.isPattern(stack)) {
            for (var details : MachineWildcardApi.expand(stack, level)) {
                patterns.add(details); changed = true;
                for (var input : details.getInputs()) for (var option : input.getPossibleInputs())
                    patternInputs.add(option.what().dropSecondary());
            }
        }
        // GTL removes the integrated circuit from this secondary input guard. Preserve that rule
        // even if another RETURN injector ran before our independent expansion was appended.
        if (changed && net.minecraftforge.fml.ModList.get().isLoaded("gtlcore")) {
            var circuit = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("gtceu", "programmed_circuit"));
            if (circuit != null) patternInputs.remove(appeng.api.stacks.AEItemKey.of(circuit));
        }
        if (changed) ICraftingProvider.requestUpdate(mainNode);
    }
}
