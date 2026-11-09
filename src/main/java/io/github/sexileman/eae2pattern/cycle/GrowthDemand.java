package io.github.sexileman.eae2pattern.cycle;

import java.util.*;

/**
 * Root-consumer demand is additional to protected startup stock, but may use existing normal stock.
 */
public final class GrowthDemand {
    public record Consumer(
            long times,
            long demandA,
            long demandB,
            long initialA,
            long initialB,
            long goalA,
            long goalB) {}

    private GrowthDemand() {}

    public static Consumer consumer(
            long requested,
            long perCraft,
            long inputA,
            long inputB,
            long reserveA,
            long reserveB,
            long availableA,
            long availableB,
            long aIn,
            long bOut,
            long bIn,
            long aOut) {
        return consumerInternal(
                requested,
                perCraft,
                inputA,
                inputB,
                reserveA,
                reserveB,
                availableA,
                availableB,
                aIn,
                bOut,
                bIn,
                aOut,
                false);
    }

    public static Consumer analyzeConsumer(
            long requested,
            long perCraft,
            long inputA,
            long inputB,
            long reserveA,
            long reserveB,
            long availableA,
            long availableB,
            long aIn,
            long bOut,
            long bIn,
            long aOut) {
        return consumerInternal(
                requested,
                perCraft,
                inputA,
                inputB,
                reserveA,
                reserveB,
                availableA,
                availableB,
                aIn,
                bOut,
                bIn,
                aOut,
                true);
    }

    private static Consumer consumerInternal(
            long requested,
            long perCraft,
            long inputA,
            long inputB,
            long reserveA,
            long reserveB,
            long availableA,
            long availableB,
            long aIn,
            long bOut,
            long bIn,
            long aOut,
            boolean analysis) {
        if (requested <= 0
                || perCraft <= 0
                || inputA < 0
                || inputB < 0
                || (inputA == 0 && inputB == 0)
                || reserveA < 0
                || reserveB < 0
                || availableA < reserveA
                || availableB < reserveB
                || aIn <= 0
                || bOut <= 0
                || bIn <= 0
                || aOut <= 0) throw new IllegalArgumentException("Invalid consumer demand");
        long times = ceil(requested, perCraft);
        if (!analysis && times > TwoRecipeGrowth.MAX_OPERATIONS)
            throw new IllegalArgumentException("Consumer operation limit reached");
        long demandA = Math.multiplyExact(inputA, times),
                demandB = Math.multiplyExact(inputB, times);
        long goalA = Math.addExact(reserveA, demandA), goalB = Math.addExact(reserveB, demandB);
        long bForRoot = Math.min(availableB, goalB);
        long initialA =
                Math.min(
                        availableA,
                        Math.addExact(
                                goalA, Math.multiplyExact(ceil(goalB - bForRoot, bOut), aIn)));
        long initialB =
                Math.min(
                        availableB,
                        Math.addExact(
                                goalB,
                                Math.multiplyExact(
                                        ceil(Math.max(0, goalA - initialA), aOut), bIn)));
        return new Consumer(times, demandA, demandB, initialA, initialB, goalA, goalB);
    }

    public static List<TwoRecipeGrowth.Stage> appendConsumer(
            TwoRecipeGrowth.Result loop, long times) {
        return appendConsumerInternal(loop, times, false);
    }

    public static List<TwoRecipeGrowth.Stage> appendAnalyzedConsumer(
            TwoRecipeGrowth.Result loop, long times) {
        return appendConsumerInternal(loop, times, true);
    }

    private static List<TwoRecipeGrowth.Stage> appendConsumerInternal(
            TwoRecipeGrowth.Result loop, long times, boolean analysis) {
        if (times <= 0
                || Math.addExact(loop.operations(), times)
                        > (analysis ? Long.MAX_VALUE : TwoRecipeGrowth.MAX_OPERATIONS)
                || loop.stages().size() >= TwoRecipeGrowth.MAX_STAGES)
            throw new IllegalArgumentException("Combined cycle plan exceeds limits");
        var phases = new ArrayList<>(loop.stages());
        phases.add(new TwoRecipeGrowth.Stage(2, times));
        return List.copyOf(phases);
    }

    public static long[] counts(List<TwoRecipeGrowth.Stage> phases, int recipes) {
        return countsInternal(phases, recipes, false);
    }

    public static long[] analyzeCounts(List<TwoRecipeGrowth.Stage> phases, int recipes) {
        return countsInternal(phases, recipes, true);
    }

    private static long[] countsInternal(
            List<TwoRecipeGrowth.Stage> phases, int recipes, boolean analysis) {
        var result = new long[recipes];
        long total = 0;
        for (var phase : phases) {
            if (phase.recipe() >= recipes)
                throw new IllegalArgumentException("Invalid recipe reference");
            result[phase.recipe()] = Math.addExact(result[phase.recipe()], phase.times());
            total = Math.addExact(total, phase.times());
        }
        if (!analysis && total > TwoRecipeGrowth.MAX_OPERATIONS)
            throw new IllegalArgumentException("Combined operation limit reached");
        return result;
    }

    private static long ceil(long amount, long unit) {
        return amount / unit + (amount % unit == 0 ? 0 : 1);
    }
}
