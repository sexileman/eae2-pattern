package io.github.sexileman.eae2pattern.mixin;

import appeng.api.crafting.IPatternDetails;
import io.github.sexileman.eae2pattern.compat.gtl.GtlWildcardBridge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import java.util.List;

/** The signature gate applies this optional adapter only to supported GTL integration classes. */
@Pseudo
@Mixin(targets = "org.gtlcore.gtlcore.integration.wildcard.WildcardPatternCompatImpl", remap = false)
public abstract class GtlWildcardExpansionMixin {
    /** @author EAE2 Pattern @reason Route both optional item types without linking missing legacy classes. */
    @Overwrite(remap = false)
    public static boolean isWildcardPattern(ItemStack stack) { return GtlWildcardBridge.accepts(stack); }

    /** @author EAE2 Pattern @reason Expand independent and legacy wildcard items through their own APIs. */
    @Overwrite(remap = false)
    public static List<IPatternDetails> expandPatterns(ItemStack stack, Level level) { return GtlWildcardBridge.expand(stack, level); }
}
