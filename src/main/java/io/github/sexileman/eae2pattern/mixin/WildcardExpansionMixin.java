package io.github.sexileman.eae2pattern.mixin;

import appeng.api.crafting.IPatternDetails;
import io.github.sexileman.eae2pattern.wildcard.MachineWildcard;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.stream.Stream;

@Pseudo
@Mixin(targets = "org.leodreamer.wildcard_pattern.wildcard.WildcardPatternLogic", remap = false)
public abstract class WildcardExpansionMixin {
    @Inject(method = "decodePatterns", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void eae2$machineRecipes(ItemStack stack, Level level, CallbackInfoReturnable<Stream<IPatternDetails>> cir) {
        if (MachineWildcard.isBound(stack)) cir.setReturnValue(MachineWildcard.expand(stack, level));
    }
}
