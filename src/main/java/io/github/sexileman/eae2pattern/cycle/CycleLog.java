package io.github.sexileman.eae2pattern.cycle;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.*;

import io.github.sexileman.eae2pattern.EAE2Pattern;

import java.util.*;

/** Uses the host logger: no separate files, chat dependency, or unbounded per-tick logging. */
public final class CycleLog {
    private CycleLog() {}

    public static String key(AEKey key) {
        try {
            return describeTag(key.toTagGeneric(), key.getId().toString());
        } catch (RuntimeException | LinkageError unavailable) {
            return key.getId() + " [NBT unavailable]";
        }
    }

    static String describeTag(net.minecraft.nbt.CompoundTag tag, String id) {
        if (tag.sizeInBytes() > CycleLimits.LOG_KEY_CHARACTERS * 4L)
            return id + " [NBT omitted: exceeds diagnostic budget]";
        String text = tag.toString().replace('\n', ' ').replace('\r', ' ');
        return text.length() > CycleLimits.LOG_KEY_CHARACTERS
                ? text.substring(0, CycleLimits.LOG_KEY_CHARACTERS) + "...[truncated]"
                : text;
    }

    public static String stacks(List<GenericStack> stacks) {
        return stacks.stream()
                        .limit(CycleLimits.LOG_STACKS)
                        .map(s -> s.amount() + " x " + key(s.what()))
                        .toList()
                + (stacks.size() > CycleLimits.LOG_STACKS ? " [truncated]" : "");
    }

    public static String map(Map<AEKey, Long> stocks) {
        return stacks(
                stocks.entrySet().stream()
                        .limit(CycleLimits.LOG_STACKS + 1)
                        .map(e -> new GenericStack(e.getKey(), e.getValue()))
                        .toList());
    }

    public static String stocks(KeyCounter stocks) {
        var slots = new ArrayList<GenericStack>();
        for (var stack : stocks) {
            if (slots.size() == CycleLimits.LOG_STACKS + 1) break;
            slots.add(new GenericStack(stack.getKey(), stack.getLongValue()));
        }
        return stacks(slots);
    }

    public static void detected(ICraftingPlan plan, CycleCapture capture) {
        if (capture == null || capture.witnesses().isEmpty()) return;
        EAE2Pattern.LOGGER.info(
                "[EAE2/Cycles] event=detected trace={} target={} amount={} nativeMissing={}"
                        + " candidates={} truncated={} experimentalExecution={}",
                capture.id(),
                key(plan.finalOutput().what()),
                plan.finalOutput().amount(),
                plan.simulation(),
                capture.witnesses().size(),
                capture.truncated(),
                io.github.sexileman.eae2pattern.config.EAE2PatternConfig.EXPERIMENTAL_CYCLES.get());
        int index = 0;
        for (var witness : capture.witnesses()) {
            EAE2Pattern.LOGGER.info(
                    "[EAE2/Cycles] event=candidate trace={} index={} path={}",
                    capture.id(),
                    index++,
                    witness.path().stream().map(CycleLog::key).toList());
            for (var pattern : witness.patterns())
                EAE2Pattern.LOGGER.info(
                        "[EAE2/Cycles] event=recipe trace={} inputs={} outputs={} recipeTypes={}",
                        capture.id(),
                        stacks(pattern.inputs()),
                        stacks(pattern.outputs()),
                        pattern.recipeTypes());
        }
    }
}
