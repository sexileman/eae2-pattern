package io.github.sexileman.eae2pattern.cycle;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.me.service.CraftingService;

import io.github.sexileman.eae2pattern.mixin.GrowthJobAccessor;
import io.github.sexileman.eae2pattern.mixin.GrowthProgressAccessor;
import io.github.sexileman.eae2pattern.mixin.GrowthTrackerInvoker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/** One in-flight phase batch, isolated in the native CPU inventory. Never global stock polling. */
public final class GrowthRun {
    public static final String TAG = "eae2_growth_alpha";
    private final List<IPatternDetails> recipes;
    private final List<TwoRecipeGrowth.Stage> stages;
    private GrowthLedger<AEKey> ledger;
    private String logId = UUID.randomUUID().toString();

    public String logId() {
        return logId;
    }

    public void logState(String event) {
        var logger = io.github.sexileman.eae2pattern.EAE2Pattern.LOGGER;
        boolean detailed =
                event.equals("batch_returned") && reportedBatches > CycleLimits.INFO_BATCHES;
        if (detailed && !logger.isDebugEnabled()) return;
        var state = ledger.snapshot();
        String message =
                "[EAE2/Cycles] event={} run={} stage={} remaining={} inFlight={} awaiting={}"
                        + " batches={}";
        if (detailed)
            logger.debug(
                    message,
                    event,
                    logId,
                    state.stage(),
                    state.remaining(),
                    state.inFlight(),
                    CycleLog.map(state.awaiting()),
                    reportedBatches);
        else
            logger.info(
                    message,
                    event,
                    logId,
                    state.stage(),
                    state.remaining(),
                    state.inFlight(),
                    CycleLog.map(state.awaiting()),
                    reportedBatches);
    }

    private long lastReturnProgress = -1;
    private long nextReturnWarning = -1;

    private void observeWaiting(long tick) {
        if (lastReturnProgress < 0 || tick < lastReturnProgress) {
            lastReturnProgress = tick;
            nextReturnWarning = tick + CycleLimits.WAIT_WARNING_TICKS;
        }
        if (tick >= nextReturnWarning) {
            io.github.sexileman.eae2pattern.EAE2Pattern.LOGGER.warn(
                    "[EAE2/Cycles] event=return_stalled run={} idleTicks={} stage={} awaiting={}; task remains active, check machines or cancel manually",
                    logId,
                    tick - lastReturnProgress,
                    ledger.snapshot().stage(),
                    CycleLog.map(ledger.snapshot().awaiting()));
            nextReturnWarning = tick + CycleLimits.WAIT_WARNING_TICKS;
        }
    }

    private boolean reportedEnergy;
    private int reportedBatches;
    private boolean reportedRejection;
    private final long[] batchLimits;
    private final List<ProviderCursor<appeng.api.networking.crafting.ICraftingProvider>>
            providerCursors;

    public GrowthRun(GrowthCraftingPlan plan) {
        this(plan.recipes(), plan.stages());
    }

    private GrowthRun(List<IPatternDetails> recipes, List<TwoRecipeGrowth.Stage> stages) {
        this.recipes = List.copyOf(recipes);
        this.stages = List.copyOf(stages);
        GrowthDemand.counts(stages, recipes.size());
        ledger = new GrowthLedger<>(stages);
        batchLimits = new long[recipes.size()];
        Arrays.fill(batchLimits, CycleLimits.MAX_QUANTITY);
        providerCursors = new ArrayList<>();
        for (int i = 0; i < recipes.size(); i++) providerCursors.add(new ProviderCursor<>());
    }

    private static GrowthJobAccessor job(GrowthCpuView cpu) {
        return (GrowthJobAccessor) (Object) cpu.eae2$job();
    }

    public int step(
            GrowthCpuView cpu,
            int max,
            CraftingService service,
            IEnergyService energy,
            Level level) {
        if (ledger.waiting()) {
            observeWaiting(level.getGameTime());
            return 0;
        }
        lastReturnProgress = -1;
        if (max < 1) return 0;
        ledger.advance();
        if (ledger.done()) {
            deliver(cpu);
            return 0;
        }
        var job = job(cpu);
        var pattern = recipes.get(ledger.recipe());
        var perCraft = new HashMap<AEKey, Long>();
        for (var input : pattern.getInputs()) {
            var options = input.getPossibleInputs();
            if (options.length != 1)
                throw new IllegalStateException("Cycle input alternatives changed");
            perCraft.merge(
                    options[0].what(),
                    Math.multiplyExact(options[0].amount(), input.getMultiplier()),
                    Math::addExact);
        }
        long affordable =
                GrowthBatchSizing.affordable(
                        Math.min(ledger.batch(), batchLimits[ledger.recipe()]),
                        perCraft,
                        key ->
                                cpu.eae2$inventory()
                                        .extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
        if (affordable == 0) return 0;
        var progress = (GrowthProgressAccessor) job.eae2$tasks().get(pattern);
        var providers = providerCursors.get(ledger.recipe());
        for (var provider :
                providers.next(service.getProviders(pattern), CycleLimits.PROVIDERS_PER_PASS)) {
            if (provider.isBusy()) continue;
            var adapter = GrowthDispatchAdapters.select(provider, pattern);
            var dispatch = adapter.prepare(pattern, affordable);
            long batch = dispatch.times();
            if (progress == null || progress.eae2$value() < batch)
                throw new IllegalStateException("Cycle task counts disagree");
            var outputs = new KeyCounter();
            var containers = new KeyCounter();
            var inputs =
                    CraftingCpuHelper.extractPatternInputs(
                            dispatch.pattern(), cpu.eae2$inventory(), level, outputs, containers);
            if (inputs == null) return 0;
            boolean sent = false;
            try {
                double power = CraftingCpuHelper.calculatePatternPower(inputs);
                power = adapter.power(power, batch);
                if (!Double.isFinite(power) || power < 0)
                    throw new IllegalStateException("Invalid cycle energy");
                if (energy.extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                        < power - .01) {
                    if (!reportedEnergy) {
                        reportedEnergy = true;
                        io.github.sexileman.eae2pattern.EAE2Pattern.LOGGER.warn(
                                "[EAE2/Cycles] event=energy_limited run={} requiredAE={} crafts={};"
                                        + " retrying smaller batch",
                                logId,
                                power,
                                batch);
                    }
                    batchLimits[ledger.recipe()] = Math.max(1, batch / 2);
                    continue;
                }
                // GTL's native CPU passes the registered original plus bulk counters to its
                // buffers.
                if (provider.pushPattern(adapter.providerPattern(pattern, dispatch), inputs)) {
                    sent = true;
                    providers.clear();
                    batchLimits[ledger.recipe()] = TwoRecipeGrowth.MAX_QUANTITY;
                    energy.extractAEPower(power, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    var expected = new HashMap<AEKey, Long>();
                    for (var output : outputs) expected.put(output.getKey(), output.getLongValue());
                    ledger.sent(batch, expected);
                    progress.eae2$value(progress.eae2$value() - batch);
                    if (progress.eae2$value() == 0) job.eae2$tasks().remove(pattern);
                    for (var output : outputs)
                        job.eae2$waiting()
                                .insert(
                                        output.getKey(),
                                        output.getLongValue(),
                                        Actionable.MODULATE);
                    var logger = io.github.sexileman.eae2pattern.EAE2Pattern.LOGGER;
                    String message =
                            "[EAE2/Cycles] event=batch_sent run={} recipe={} provider={} route={}"
                                    + " crafts={} affordable={} phaseRemaining={} outputs={}";
                    if (reportedBatches++ < CycleLimits.INFO_BATCHES)
                        logger.info(
                                message,
                                logId,
                                ledger.recipe(),
                                provider.getClass().getName(),
                                adapter.id(),
                                batch,
                                affordable,
                                ledger.snapshot().remaining(),
                                CycleLog.stocks(outputs));
                    else if (logger.isDebugEnabled())
                        logger.debug(
                                message,
                                logId,
                                ledger.recipe(),
                                provider.getClass().getName(),
                                adapter.id(),
                                batch,
                                affordable,
                                ledger.snapshot().remaining(),
                                CycleLog.stocks(outputs));
                    cpu.eae2$cluster().markDirty();
                    return 1;
                }
                batchLimits[ledger.recipe()] = Math.max(1, batch / 2);
                if (!reportedRejection) {
                    reportedRejection = true;
                    io.github.sexileman.eae2pattern.EAE2Pattern.LOGGER.warn(
                            "[EAE2/Cycles] event=batch_rejected run={} provider={} route={}"
                                    + " crafts={}; reducing next attempt",
                            logId,
                            provider.getClass().getName(),
                            adapter.id(),
                            batch);
                }
            } finally {
                if (!sent) CraftingCpuHelper.reinjectPatternInputs(cpu.eae2$inventory(), inputs);
            }
        }
        return 0;
    }

    public long accept(GrowthCpuView cpu, AEKey key, long amount, Actionable action) {
        if (amount <= 0) return 0;
        var job = job(cpu);
        long accepted =
                Math.min(
                        amount,
                        Math.min(
                                ledger.expected(key, amount),
                                job.eae2$waiting().extract(key, amount, Actionable.SIMULATE)));
        if (accepted <= 0) return 0;
        if (action == Actionable.MODULATE) {
            ledger.receive(key, accepted);
            lastReturnProgress = -1;
            job.eae2$waiting().extract(key, accepted, Actionable.MODULATE);
            cpu.eae2$inventory().insert(key, accepted, Actionable.MODULATE);
            ((GrowthTrackerInvoker) (Object) job.eae2$tracker())
                    .eae2$decrement(accepted, key.getType());
            cpu.eae2$cluster().markDirty();
            if (!ledger.waiting()) logState("batch_returned");
        }
        return accepted;
    }

    private void deliver(GrowthCpuView cpu) {
        var job = job(cpu);
        var key = job.eae2$output().what();
        long needed = job.eae2$remaining();
        long held = cpu.eae2$inventory().extract(key, needed, Actionable.MODULATE);
        if (held <= 0) return;
        long inserted = 0;
        try {
            if (job.eae2$link().isStandalone()) {
                var grid = cpu.eae2$cluster().getGrid();
                if (grid == null) return;
                inserted =
                        grid.getStorageService()
                                .getInventory()
                                .insert(
                                        key,
                                        held,
                                        Actionable.MODULATE,
                                        cpu.eae2$cluster().getSrc());
            } else inserted = job.eae2$link().insert(key, held, Actionable.MODULATE);
            if (inserted < 0 || inserted > held)
                throw new IllegalStateException("Invalid output acceptance");
        } finally {
            if (inserted >= 0 && inserted <= held)
                cpu.eae2$inventory().insert(key, held - inserted, Actionable.MODULATE);
        }
        if (cpu.eae2$job() == null) return;
        job.eae2$remaining(needed - inserted);
        cpu.eae2$cluster().markDirty();
        if (job.eae2$remaining() == 0) {
            cpu.eae2$finish(true);
            cpu.eae2$cluster().updateOutput(null);
        } else cpu.eae2$cluster().updateOutput(new GenericStack(key, job.eae2$remaining()));
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("log_id", logId);
        tag.putInt("version", recipes.size() == 2 ? 1 : 2);
        var definitions = new ListTag();
        for (var recipe : recipes) definitions.add(recipe.getDefinition().toTag());
        tag.put("recipes", definitions);
        var phases = new ListTag();
        for (var phase : stages) {
            var row = new CompoundTag();
            row.putInt("recipe", phase.recipe());
            row.putLong("times", phase.times());
            phases.add(row);
        }
        tag.put("stages", phases);
        var state = ledger.snapshot();
        tag.putInt("stage", state.stage());
        tag.putLong("remaining", state.remaining());
        tag.putLong("in_flight", state.inFlight());
        var waiting = new ListTag();
        for (var entry : state.awaiting().entrySet())
            waiting.add(GenericStack.writeTag(new GenericStack(entry.getKey(), entry.getValue())));
        tag.put("awaiting", waiting);
        return tag;
    }

    public static GrowthRun load(CompoundTag tag, GrowthCpuView cpu) {
        int version = tag.getInt("version");
        var definitions = tag.getList("recipes", 10);
        if (!((version == 1 && definitions.size() == 2)
                || (version == 2 && definitions.size() == 3)))
            throw new IllegalArgumentException("Unknown cycle state or invalid recipe count");
        var recipes = new ArrayList<IPatternDetails>();
        for (int i = 0; i < definitions.size(); i++) {
            var key = AEItemKey.fromTag(definitions.getCompound(i));
            var details =
                    key == null
                            ? null
                            : PatternDetailsHelper.decodePattern(
                                    key, cpu.eae2$cluster().getLevel());
            if (!(details instanceof appeng.crafting.pattern.AEProcessingPattern)
                    && !(i == 2 && details instanceof appeng.crafting.pattern.AECraftingPattern))
                throw new IllegalArgumentException("Cycle recipe unavailable");
            recipes.add(details);
        }
        var phases = tag.getList("stages", 10);
        if (phases.isEmpty() || phases.size() > TwoRecipeGrowth.MAX_STAGES)
            throw new IllegalArgumentException("Invalid phase count");
        var stages = new ArrayList<TwoRecipeGrowth.Stage>();
        long count = 0;
        for (int i = 0; i < phases.size(); i++) {
            var row = phases.getCompound(i);
            var phase = new TwoRecipeGrowth.Stage(row.getInt("recipe"), row.getLong("times"));
            count = Math.addExact(count, phase.times());
            stages.add(phase);
        }
        var result = new GrowthRun(recipes, stages);
        if (tag.contains("log_id", 8))
            try {
                result.logId = UUID.fromString(tag.getString("log_id")).toString();
            } catch (IllegalArgumentException ignored) {
            }
        int stage = tag.getInt("stage");
        long remaining = tag.getLong("remaining"), inFlight = tag.getLong("in_flight");
        var waiting = tag.getList("awaiting", 10);
        if (waiting.size() > CycleLimits.MAX_SLOTS
                || stage < 0
                || stage > stages.size()
                || inFlight < 0) throw new IllegalArgumentException("Invalid pending phase");
        var allowed = new KeyCounter();
        if (stage < stages.size())
            for (var output : recipes.get(stages.get(stage).recipe()).getOutputs())
                allowed.add(output.what(), Math.multiplyExact(output.amount(), inFlight));
        var awaiting = new HashMap<AEKey, Long>();
        for (int i = 0; i < waiting.size(); i++) {
            var stack = GenericStack.readTag(waiting.getCompound(i));
            if (stack == null
                    || stack.amount() <= 0
                    || stack.amount() > allowed.get(stack.what())
                    || awaiting.putIfAbsent(stack.what(), stack.amount()) != null)
                throw new IllegalArgumentException("Invalid pending cycle output");
        }
        result.ledger =
                new GrowthLedger<>(
                        stages, new GrowthLedger.Snapshot<>(stage, remaining, inFlight, awaiting));
        for (var entry : awaiting.entrySet())
            if (job(cpu).eae2$waiting().extract(entry.getKey(), Long.MAX_VALUE, Actionable.SIMULATE)
                    != entry.getValue())
                throw new IllegalArgumentException("Cycle wait counts disagree");
        for (var entry : job(cpu).eae2$waiting().list)
            if (entry.getLongValue() > 0
                    && awaiting.getOrDefault(entry.getKey(), 0L) != entry.getLongValue())
                throw new IllegalArgumentException("Unexpected native cycle wait");
        for (int i = 0; i < recipes.size(); i++) {
            var progress = job(cpu).eae2$tasks().get(recipes.get(i));
            long value = progress == null ? 0 : ((GrowthProgressAccessor) progress).eae2$value();
            if (value != result.ledger.remainingOperations(i))
                throw new IllegalArgumentException("Cycle task counts disagree");
        }
        return result;
    }
}
