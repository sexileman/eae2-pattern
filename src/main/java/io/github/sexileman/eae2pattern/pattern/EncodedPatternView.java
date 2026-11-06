package io.github.sexileman.eae2pattern.pattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;

/** Read the actual encoded AE2 result instead of guessing the final slots on the client. */
public final class EncodedPatternView {
    private EncodedPatternView() {}
    public static PatternSpec decode(Level level, PatternSpec request, ItemStack encoded) {
        var details = PatternDetailsHelper.decodePattern(encoded, level);
        if (details == null) throw new IllegalArgumentException("AE2 could not decode its encoded pattern");
        List<StackSpec> inputs = new ArrayList<>(), outputs = new ArrayList<>();
        for (var input : details.getInputs()) {
            var options = input.getPossibleInputs();
            if (options.length > 0) inputs.add(spec(options[0], input.getMultiplier()));
        }
        for (var output : details.getOutputs()) outputs.add(spec(output, 1));
        if (outputs.isEmpty() || outputs.size() > PatternSpec.MAX_OUTPUTS || inputs.size() > PatternSpec.MAX_INPUTS)
            throw new IllegalArgumentException("Encoded pattern cannot be represented in the preview");
        return new PatternSpec(request.kind(), request.recipeId(), inputs, outputs);
    }
    private static StackSpec spec(GenericStack stack, long multiplier) {
        long amount = Math.multiplyExact(stack.amount(), multiplier);
        if (stack.what() instanceof AEItemKey item) return StackSpec.item(item.toStack()).withAmount(amount);
        if (stack.what() instanceof AEFluidKey fluid) return StackSpec.fluid(fluid.toStack(1)).withAmount(amount);
        throw new IllegalArgumentException("Unsupported AE2 key in encoded pattern");
    }
}
