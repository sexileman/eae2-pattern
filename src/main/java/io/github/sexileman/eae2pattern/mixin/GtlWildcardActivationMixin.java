package io.github.sexileman.eae2pattern.mixin;

import io.github.sexileman.eae2pattern.compat.gtl.GtlWildcardBridge;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enable the existing GTL machine without replacing the GTL jar or impersonating a legacy mod. */
@Pseudo
@Mixin(targets = "org.gtlcore.gtlcore.integration.wildcard.WildcardPatternCompat", remap = false)
public abstract class GtlWildcardActivationMixin {
    @Shadow @Final @Mutable private static boolean IS_LOADED;
    @Inject(method = "<clinit>", at = @At(value = "FIELD",
            target = "Lorg/gtlcore/gtlcore/integration/wildcard/WildcardPatternCompat;IS_LOADED:Z",
            opcode = org.objectweb.asm.Opcodes.PUTSTATIC, shift = At.Shift.AFTER), remap = false)
    private static void eae2$enableAvailableProviders(CallbackInfo ci) {
        IS_LOADED = IS_LOADED || GtlWildcardBridge.available();
    }
}
