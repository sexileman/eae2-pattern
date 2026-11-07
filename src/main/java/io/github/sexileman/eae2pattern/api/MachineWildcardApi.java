package io.github.sexileman.eae2pattern.api;

import appeng.api.crafting.IPatternDetails;
import io.github.sexileman.eae2pattern.wildcard.MachineWildcard;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.List;

/** Stable optional integration surface. Consumers may discover it reflectively. */
public final class MachineWildcardApi {
    private MachineWildcardApi() {}
    public static boolean isPattern(ItemStack stack) { return MachineWildcard.isOwnItem(stack); }
    public static List<IPatternDetails> expand(ItemStack stack, Level level) {
        return isPattern(stack) && MachineWildcard.isBound(stack) ? MachineWildcard.expand(stack, level).toList() : List.of();
    }
}
