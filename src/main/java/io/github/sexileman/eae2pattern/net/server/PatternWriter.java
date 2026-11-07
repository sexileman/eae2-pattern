package io.github.sexileman.eae2pattern.net.server;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.pattern.GenerationReport;
import io.github.sexileman.eae2pattern.pattern.GenerationReport.StopReason;
import io.github.sexileman.eae2pattern.pattern.PatternEncoder;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-thread jobs, round-robin, with a global 4ms / 64 recipe budget per tick. */
@Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID)
public final class PatternWriter {
    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
    private PatternWriter() {}

    public static boolean busy(UUID player) { return JOBS.stream().anyMatch(j -> j.player.getUUID().equals(player)); }
    public static void forget(UUID player) { JOBS.removeIf(j -> j.player.getUUID().equals(player)); }
    public static long countBlankPatterns(ServerPlayer player) {
        NetworkAccess network = NetworkAccess.resolve(player);
        return network == null ? 0 : network.blankPatterns();
    }
    public static boolean hasNetwork(ServerPlayer player) { return NetworkAccess.resolve(player) != null; }

    public static void start(ServerPlayer player, List<PatternSpec> specs, Consumer<GenerationReport> callback) {
        if (busy(player.getUUID())) return;
        NetworkAccess network = NetworkAccess.resolve(player);
        if (specs.isEmpty()) { callback.accept(GenerationReport.nothingToDo()); return; }
        if (network == null) {
            callback.accept(new GenerationReport(specs.size(), 0, 0, 0, 0, StopReason.NO_NETWORK, List.of())); return;
        }
        long available = network.blankPatterns();
        if (available < specs.size()) {
            callback.accept(new GenerationReport(specs.size(), 0, 0, 0, 0, StopReason.NOT_ENOUGH_BLANK_PATTERNS,
                    List.of("need " + specs.size() + " blank patterns, network has " + available))); return;
        }
        JOBS.add(new Job(player, List.copyOf(specs), network, callback));
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long deadline = System.nanoTime() + 4_000_000L;
        int work = 0;
        while (!JOBS.isEmpty() && work++ < 64 && System.nanoTime() < deadline) {
            Job job = JOBS.removeFirst();
            if (job.player.hasDisconnected()) continue;
            boolean done;
            try { done = job.step(); }
            catch (RuntimeException e) {
                EAE2Pattern.LOGGER.error("Pattern job failed for {}", job.player.getUUID(), e);
                job.detail(e.toString()); job.finish(StopReason.ENCODE_FAILED); done = true;
            }
            if (!done) JOBS.addLast(job);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { JOBS.clear(); }

    private static final class Job {
        final ServerPlayer player;
        final List<PatternSpec> specs;
        final NetworkAccess network;
        final Consumer<GenerationReport> callback;
        final List<String> details = new ArrayList<>();
        int cursor, encoded, inserted, skipped;
        Job(ServerPlayer player, List<PatternSpec> specs, NetworkAccess network, Consumer<GenerationReport> callback) {
            this.player = player; this.specs = specs; this.network = network; this.callback = callback;
        }
        void detail(String message) { if (details.size() < 5) details.add(message); }
        void finish(StopReason reason) {
            EAE2Pattern.LOGGER.info("Generation for {}: requested={} inserted={} skipped={} reason={}", player.getUUID(), specs.size(), inserted, skipped, reason);
            callback.accept(new GenerationReport(specs.size(), encoded, inserted, skipped, inserted, reason, details));
        }
        boolean step() {
            NetworkAccess current = NetworkAccess.resolve(player);
            if (current == null || !network.sameNetwork(current)) { finish(StopReason.NO_NETWORK); return true; }
            PatternSpec spec = specs.get(cursor++);
            ItemStack pattern;
            try { pattern = PatternEncoder.encode(player, spec); }
            catch (PatternEncoder.EncodingException | RuntimeException e) {
                skipped++; detail(spec.recipeId() + ": " + e.getMessage());
                EAE2Pattern.LOGGER.debug("Skipping {}", spec.recipeId(), e);
                return endIfFinished();
            }
            MEStorage storage = current.storage();
            IActionSource source = current.actionSource(player);
            AEItemKey blank = NetworkAccess.blankPatternKey();
            if (storage.extract(blank, 1, Actionable.MODULATE, source) < 1) {
                finish(StopReason.NOT_ENOUGH_BLANK_PATTERNS); return true;
            }
            encoded++;
            // Every exit after extraction refunds the blank if the encoded item was not inserted.
            boolean accepted = false;
            try {
                AEItemKey key = AEItemKey.of(pattern);
                accepted = key != null && storage.insert(key, 1, Actionable.MODULATE, source) == 1;
            } finally {
                if (!accepted) refund(storage, blank, source);
            }
            if (!accepted) { finish(StopReason.STORAGE_FULL); return true; }
            inserted++;
            return endIfFinished();
        }
        void refund(MEStorage storage, AEItemKey blank, IActionSource source) {
            long returned = 0;
            try { returned = storage.insert(blank, 1, Actionable.MODULATE, source); }
            catch (RuntimeException e) { EAE2Pattern.LOGGER.warn("Network rejected blank refund", e); }
            if (returned < 1) {
                ItemStack refund = blank.toStack(1);
                if (!player.getInventory().add(refund)) player.drop(refund, false);
            }
        }
        boolean endIfFinished() {
            if (cursor < specs.size()) return false;
            finish(inserted == 0 && skipped > 0 ? StopReason.ENCODE_FAILED : StopReason.COMPLETED); return true;
        }
    }
}
