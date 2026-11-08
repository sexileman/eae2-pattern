package io.github.sexileman.eae2pattern.mixin;
import appeng.helpers.patternprovider.PatternProviderLogic;
import io.github.sexileman.eae2pattern.cycle.GrowthBatchPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Only registration checks use the original; transport retains scaled quantities.
 * Match Object by type: EAP redirects contains(Object) to a handler(List,Object), shifting its index.
 */
@Mixin(value=PatternProviderLogic.class,remap=false,priority=1100)
public abstract class GrowthProviderBatchMixin {
    @ModifyArg(method="pushPattern",at=@At(value="INVOKE",target="Ljava/util/List;contains(Ljava/lang/Object;)Z"),index=-1,require=0,remap=false)
    private Object eae2$registeredGrowthPattern(Object pattern){
        return pattern instanceof GrowthBatchPattern batch?batch.original():pattern;
    }
}
