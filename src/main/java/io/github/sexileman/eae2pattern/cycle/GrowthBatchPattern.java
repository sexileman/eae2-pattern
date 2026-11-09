package io.github.sexileman.eae2pattern.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;

import net.minecraft.world.level.Level;

/** Transient phase-local quantities; the registered original remains the provider lookup key. */
public final class GrowthBatchPattern implements IPatternDetails {
    private final IPatternDetails original;
    private final IInput[] inputs;
    private final GenericStack[] outputs;

    public GrowthBatchPattern(IPatternDetails original, long times) {
        if (times < 2 || times > TwoRecipeGrowth.MAX_OPERATIONS)
            throw new IllegalArgumentException("Invalid cycle batch");
        this.original = original;
        var source = original.getInputs();
        inputs = new IInput[source.length];
        for (int i = 0; i < source.length; i++) {
            var input = source[i];
            long multiplier = Math.multiplyExact(input.getMultiplier(), times);
            inputs[i] =
                    new IInput() {
                        public GenericStack[] getPossibleInputs() {
                            return input.getPossibleInputs();
                        }

                        public long getMultiplier() {
                            return multiplier;
                        }

                        public boolean isValid(AEKey key, Level level) {
                            return input.isValid(key, level);
                        }

                        public AEKey getRemainingKey(AEKey key) {
                            return input.getRemainingKey(key);
                        }
                    };
        }
        var sourceOutputs = original.getOutputs();
        outputs = new GenericStack[sourceOutputs.length];
        for (int i = 0; i < outputs.length; i++)
            outputs[i] =
                    new GenericStack(
                            sourceOutputs[i].what(),
                            Math.multiplyExact(sourceOutputs[i].amount(), times));
    }

    public IPatternDetails original() {
        return original;
    }

    public AEItemKey getDefinition() {
        return original.getDefinition();
    }

    public IInput[] getInputs() {
        return inputs;
    }

    public GenericStack[] getOutputs() {
        return outputs;
    }
}
