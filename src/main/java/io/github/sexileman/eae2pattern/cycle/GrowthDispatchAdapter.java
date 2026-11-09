package io.github.sexileman.eae2pattern.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;

/**
 * One transport contract owns descriptor preparation, registration identity and AE energy together.
 */
public interface GrowthDispatchAdapter {
    String id();

    boolean supports(ICraftingProvider provider, IPatternDetails original);

    GrowthBatchBridge.Batch prepare(IPatternDetails original, long requested);

    IPatternDetails providerPattern(IPatternDetails original, GrowthBatchBridge.Batch batch);

    double power(double extractedPower, long crafts);
}
