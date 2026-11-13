package io.github.sexileman.eae2pattern.cycle;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.*;
import appeng.api.stacks.*;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;

import io.github.sexileman.eae2pattern.config.EAE2PatternConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Failed root request: direct growth material or one native root consumer of a two-material loop.
 */
public final class GrowthFallback {
    private GrowthFallback() {}

    private record Slots(Map<AEKey, Long> in, Map<AEKey, Long> out) {}

    public static ICraftingPlan replace(
            Level level,
            ICraftingSimulationRequester requester,
            ICraftingInventory stock,
            ICraftingPlan original,
            CycleCapture capture,
            boolean cpuSupported) {
        if (!original.simulation() || capture == null) return original;
        boolean enabled = EAE2PatternConfig.EXPERIMENTAL_CYCLES.get();
        String reason = "unsupported";
        String detail = "";
        for (var candidate : capture.candidates())
            try {
                if (candidate.patterns().size() != CycleLimits.LOOP_RECIPES
                        || candidate.path().size() != CycleLimits.LOOP_RECIPES + 1) continue;
                var target = original.finalOutput();
                var keys = new HashSet<>(candidate.path());
                if (keys.size() != CycleLimits.LOOP_RECIPES) continue;
                boolean downstream = !keys.contains(target.what());
                if (downstream && candidate.upstream().size() != 1) continue;
                var a = downstream ? candidate.path().get(0) : target.what();
                keys.remove(a);
                var b = keys.iterator().next();
                var recipes = new ArrayList<IPatternDetails>(candidate.patterns());
                Slots first = read(recipes.get(0), false), second = read(recipes.get(1), false);
                if (first.out.containsKey(a)) {
                    Collections.reverse(recipes);
                    var temp = first;
                    first = second;
                    second = temp;
                }
                if (!first.in.containsKey(a)
                        || first.in.containsKey(b)
                        || !first.out.containsKey(b)
                        || first.out.containsKey(a)
                        || !second.in.containsKey(b)
                        || second.in.containsKey(a)
                        || !second.out.containsKey(a)
                        || second.out.containsKey(b)) continue;
                long seedA = 0, seedB = 0, aIn = first.in.get(a), bIn = second.in.get(b);
                long startA = GrowthMaterials.startup(aIn, first.out.get(b), bIn),
                        startB = GrowthMaterials.startup(bIn, second.out.get(a), aIn);
                if (stock.extract(a, startA, Actionable.SIMULATE) >= startA) seedA = startA;
                else if (stock.extract(b, startB, Actionable.SIMULATE) >= startB) seedB = startB;
                else {
                    reason = "seed";
                    if (capture.materials() == null || capture.materials().alternatives())
                        capture.materials(
                                new CycleCapture.Materials(
                                        candidate.path(),
                                        GrowthMaterialNeeds.evaluate(
                                                Map.of(a, startA, b, startB),
                                                Set.of(a, b),
                                                key ->
                                                        stock.extract(
                                                                key,
                                                                Long.MAX_VALUE,
                                                                Actionable.SIMULATE)),
                                        true,
                                        "seed"));
                    continue;
                }
                var inputMaps = new ArrayList<Map<AEKey, Long>>();
                inputMaps.add(first.in);
                inputMaps.add(second.in);
                List<TwoRecipeGrowth.Stage> phases;
                long initialA = seedA, initialB = seedB;
                if (downstream) {
                    var terminal = candidate.upstream().get(0);
                    if (recipes.contains(terminal)) continue;
                    var root = read(terminal, true);
                    Long output = root.out.get(target.what());
                    if (output == null || root.in.containsKey(target.what())) continue;
                    long availableA = stock.extract(a, Long.MAX_VALUE, Actionable.SIMULATE),
                            availableB = stock.extract(b, Long.MAX_VALUE, Actionable.SIMULATE);
                    var demand =
                            GrowthDemand.analyzeConsumer(
                                    target.amount(),
                                    output,
                                    root.in.getOrDefault(a, 0L),
                                    root.in.getOrDefault(b, 0L),
                                    seedA,
                                    seedB,
                                    availableA,
                                    availableB,
                                    aIn,
                                    first.out.get(b),
                                    bIn,
                                    second.out.get(a));
                    initialA = demand.initialA();
                    initialB = demand.initialB();
                    var loop =
                            TwoRecipeGrowth.analyze(
                                    aIn,
                                    first.out.get(b),
                                    bIn,
                                    second.out.get(a),
                                    initialA,
                                    initialB,
                                    demand.goalA(),
                                    demand.goalB());
                    phases = GrowthDemand.appendAnalyzedConsumer(loop, demand.times());
                    recipes.add(terminal);
                    inputMaps.add(root.in);
                } else {
                    // Reserve useful existing circulating stock, not only the minimum startup seed.
                    // Preserve that working capital at completion; requested A is newly produced
                    // net gain.
                    long availableA = stock.extract(a, Long.MAX_VALUE, Actionable.SIMULATE),
                            availableB = stock.extract(b, Long.MAX_VALUE, Actionable.SIMULATE);
                    initialA = GrowthWorkingStock.reserve(availableA, seedA, target.amount(), 1, 1);
                    initialB =
                            GrowthWorkingStock.reserve(
                                    availableB, seedB, target.amount(), second.out.get(a), bIn);
                    phases =
                            TwoRecipeGrowth.analyze(
                                            aIn,
                                            first.out.get(b),
                                            bIn,
                                            second.out.get(a),
                                            initialA,
                                            initialB,
                                            Math.addExact(initialA, target.amount()),
                                            initialB)
                                    .stages();
                }
                var seeds = new HashMap<AEKey, Long>();
                if (initialA > 0) seeds.put(a, initialA);
                if (initialB > 0) seeds.put(b, initialB);
                var times = GrowthDemand.analyzeCounts(phases, recipes.size());
                var budget = GrowthMaterials.budget(inputMaps, times, Set.of(a, b), seeds);
                var tasks = new LinkedHashMap<IPatternDetails, Long>();
                long operations = 0;
                for (int i = 0; i < recipes.size(); i++) {
                    if (times[i] > 0) tasks.put(recipes.get(i), times[i]);
                    operations = Math.addExact(operations, times[i]);
                    for (var output : recipes.get(i).getOutputs())
                        Math.multiplyExact(
                                Math.multiplyExact(output.amount(), times[i]),
                                output.what().getAmountPerUnit());
                }
                var needs =
                        GrowthMaterialNeeds.evaluate(
                                budget,
                                Set.of(a, b),
                                key -> stock.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
                boolean lacks = needs.stream().anyMatch(n -> n.missing() > 0);
                String materialReason =
                        lacks
                                ? "external"
                                : !enabled
                                        ? "disabled"
                                        : !CycleRuntimeGate.supported
                                                ? "runtime"
                                                : !cpuSupported ? "cpu" : "ready";
                capture.materials(
                        new CycleCapture.Materials(candidate.path(), needs, false, materialReason));
                if (lacks) {
                    var entry =
                            needs.stream().filter(n -> n.missing() > 0).findFirst().orElseThrow();
                    reason = "external";
                    detail = entry.key().getId() + " / " + entry.missing();
                    io.github.sexileman.eae2pattern.EAE2Pattern.LOGGER.info(
                            "[EAE2/Cycles] event=materials_missing trace={} shortages={}",
                            capture.id(),
                            needs.stream()
                                    .filter(n -> n.missing() > 0)
                                    .map(
                                            n ->
                                                    n.key().getId()
                                                            + " required="
                                                            + n.required()
                                                            + " available="
                                                            + n.available()
                                                            + " missing="
                                                            + n.missing())
                                    .toList());
                    continue;
                }
                if (!enabled || !CycleRuntimeGate.supported || !cpuSupported) {
                    io.github.sexileman.eae2pattern.EAE2Pattern.LOGGER.info(
                            "[EAE2/Cycles] event=materials_checked trace={} execution={}"
                                    + " materials=available",
                            capture.id(),
                            materialReason);
                    return original;
                }
                var used = new KeyCounter();
                budget.forEach(used::add);
                long bytes =
                        Math.addExact(
                                1024 + 64L * phases.size() + 64L * used.size(),
                                Math.multiplyExact(operations, 8));
                var growth = new GrowthCraftingPlan(target, bytes, used, tasks, recipes, phases);
                var nativePlan = growth.nativePlan();
                if (!((Object) nativePlan instanceof GrowthPlanView attached))
                    throw new IllegalStateException("Native cycle plan attachment unavailable");
                attached.eae2$growthPlan(growth);
                io.github.sexileman.eae2pattern.EAE2Pattern.LOGGER.info(
                        "[EAE2/Cycles] event=plan_ready trace={} target={} amount={} phases={}"
                                + " operations={} reserved={}",
                        capture.id(),
                        target.what().getId(),
                        target.amount(),
                        phases.size(),
                        operations,
                        CycleLog.stocks(used));
                notify(level, requester, "ready", Component.literal("" + phases.size()));
                return nativePlan;
            } catch (IllegalArgumentException | ArithmeticException unsupported) {
                reason = "unsupported";
                capture.materialFailure("planning_failed");
                io.github.sexileman.eae2pattern.EAE2Pattern.LOGGER.debug(
                        "[EAE2/Cycles] event=candidate_rejected trace=" + capture.id(),
                        unsupported);
            }
        if (!capture.candidates().isEmpty())
            io.github.sexileman.eae2pattern.EAE2Pattern.LOGGER.warn(
                    "[EAE2/Cycles] event=plan_rejected trace={} target={} amount={} reason={}"
                            + " detail={}",
                    capture.id(),
                    original.finalOutput().what().getId(),
                    original.finalOutput().amount(),
                    reason,
                    detail);
        if (!capture.candidates().isEmpty())
            notify(level, requester, reason, Component.literal(detail));
        return original;
    }

    private static Slots read(IPatternDetails details, boolean terminal) {
        if ((!(details instanceof AEProcessingPattern)
                        && !(terminal
                                && details instanceof appeng.crafting.pattern.AECraftingPattern))
                || details.getDefinition() == null
                || (!terminal && !details.supportsPushInputsToExternalInventory()))
            throw new IllegalArgumentException("Unsupported native recipe type");
        if (details.getInputs().length > CycleLimits.MAX_SLOTS
                || details.getOutputs().length > CycleLimits.MAX_SLOTS)
            throw new IllegalArgumentException("Too many slots");
        var in = new HashMap<AEKey, Long>();
        var out = new HashMap<AEKey, Long>();
        for (var input : details.getInputs()) {
            var choices = input.getPossibleInputs();
            if (choices.length != 1
                    || choices[0].amount() <= 0
                    || input.getMultiplier() <= 0
                    || input.getRemainingKey(choices[0].what()) != null)
                throw new IllegalArgumentException("Ambiguous or returning input");
            in.merge(
                    choices[0].what(),
                    Math.multiplyExact(choices[0].amount(), input.getMultiplier()),
                    Math::addExact);
        }
        for (var output : details.getOutputs()) {
            if (output.amount() <= 0) throw new IllegalArgumentException("Invalid output");
            out.merge(output.what(), output.amount(), Math::addExact);
        }
        return new Slots(Map.copyOf(in), Map.copyOf(out));
    }

    public static void notify(
            Level level, ICraftingSimulationRequester requester, String key, Component detail) {
        if (level.getServer() == null || !EAE2PatternConfig.CYCLE_CHAT_DIAGNOSTICS.get()) return;
        var actor = requester.getActionSource().player().orElse(null);
        if (actor instanceof ServerPlayer player) {
            var id = player.getUUID();
            level.getServer()
                    .execute(
                            () -> {
                                var online = level.getServer().getPlayerList().getPlayer(id);
                                if (online != null
                                        && EAE2PatternConfig.CYCLE_CHAT_DIAGNOSTICS.get())
                                    online.displayClientMessage(
                                            Component.translatable(
                                                    "eae2_pattern.cycle.alpha." + key, detail),
                                            false);
                            });
        }
    }
}
