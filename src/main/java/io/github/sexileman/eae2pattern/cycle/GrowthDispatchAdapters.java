package io.github.sexileman.eae2pattern.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.crafting.pattern.AEProcessingPattern;

import java.util.List;

/** Ordered built-in strategies. Optional mods never leak their types into the executor. */
public final class GrowthDispatchAdapters {
    private static final GrowthDispatchAdapter NATIVE = new NativeAdapter();
    private static final List<GrowthDispatchAdapter> ADAPTERS = List.of(new GtlAdapter());

    private GrowthDispatchAdapters() {}

    public static GrowthDispatchAdapter select(
            ICraftingProvider provider, IPatternDetails original) {
        for (var adapter : ADAPTERS) if (adapter.supports(provider, original)) return adapter;
        return NATIVE;
    }

    private static final class NativeAdapter implements GrowthDispatchAdapter {
        public String id() {
            return "scaled";
        }

        public boolean supports(ICraftingProvider provider, IPatternDetails original) {
            return true;
        }

        public GrowthBatchBridge.Batch prepare(IPatternDetails original, long requested) {
            return GrowthBatchBridge.prepare(original, requested);
        }

        public IPatternDetails providerPattern(
                IPatternDetails original, GrowthBatchBridge.Batch batch) {
            return batch.pattern();
        }

        public double power(double extractedPower, long crafts) {
            return extractedPower;
        }
    }

    private static final class GtlAdapter implements GrowthDispatchAdapter {
        public String id() {
            return "gtl-original-bulk";
        }

        public boolean supports(ICraftingProvider provider, IPatternDetails original) {
            return original instanceof AEProcessingPattern
                    && GrowthGtlBatchBridge.supports(provider);
        }

        public GrowthBatchBridge.Batch prepare(IPatternDetails original, long requested) {
            return new GrowthBatchBridge.Batch(
                    requested == 1 ? original : new GrowthBatchPattern(original, requested),
                    requested);
        }

        public IPatternDetails providerPattern(
                IPatternDetails original, GrowthBatchBridge.Batch batch) {
            return original;
        }

        public double power(double extractedPower, long crafts) {
            return GrowthGtlBatchBridge.power(extractedPower, crafts);
        }
    }
}
