package io.github.sexileman.eae2pattern.wildcard;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** One physical item advertises multiple concrete patterns through the provider expansion hook. */
public final class MachineWildcardDecoder implements IPatternDetailsDecoder {
    @Override public boolean isEncodedPattern(ItemStack stack) { return MachineWildcard.isOwnItem(stack); }
    @Override public IPatternDetails decodePattern(AEItemKey key, Level level) { return null; }
    @Override public IPatternDetails decodePattern(ItemStack stack, Level level, boolean recover) { return null; }
}
