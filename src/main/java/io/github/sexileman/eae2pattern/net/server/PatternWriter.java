package io.github.sexileman.eae2pattern.net.server;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.pattern.GenerationReport;
import io.github.sexileman.eae2pattern.pattern.PatternEncoder;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The transaction. This is the only place that touches the ME network, and it is deliberately sequential.
 *
 * <p>The order per pattern is: encode first (pure, no side effects), then take one blank pattern, then insert the
 * encoded one. Encoding before extraction means a recipe AE2 refuses to encode costs the player nothing. If the
 * insert is refused, the blank pattern goes straight back, so a failed run never eats items.</p>
 *
 * <p>Requirement #4 says "stop if the network has no room for the patterns". AE2 offers no cheap "how much can you
 * take" query, so we treat the first refused insert as the stop signal rather than predicting it. That is both
 * cheaper and more accurate than an estimate, because cells can report room and still reject on commit.</p>
 *
 * <p>A recipe that cannot be encoded is <em>skipped</em>, not fatal: a modded machine with 800 recipes almost always
 * has a few layouts AE2 cannot express (hidden outputs, fluid-only results, more than 81 input slots), and aborting
 * the whole run on the first one would leave the player with nothing. Recipes are only skipped before any item is
 * spent, so skipping never costs anything.</p>
 */
public final class PatternWriter {

    /** How many per-recipe failure lines travel back to the client; the rest go to the log only. */
    private static final int MAX_DETAILS_SENT = 5;

    private PatternWriter() {
    }

    @Nullable
    public static NetworkAccess resolve(ServerPlayer player) {
        return NetworkAccess.resolve(player);
    }

    public static long countBlankPatterns(ServerPlayer player) {
        NetworkAccess network = NetworkAccess.resolve(player);
        return network == null ? 0L : network.blankPatterns();
    }

    public static boolean hasNetwork(ServerPlayer player) {
        return NetworkAccess.resolve(player) != null;
    }

    public static GenerationReport write(ServerPlayer player, List<PatternSpec> specs) {
        String who = player.getGameProfile().getName();
        if (specs.isEmpty()) {
            return GenerationReport.nothingToDo();
        }

        NetworkAccess network = NetworkAccess.resolve(player);
        if (network == null) {
            EAE2Pattern.LOGGER.warn("Generation for {} aborted: no AE2 network reachable", who);
            return new GenerationReport(specs.size(), 0, 0, 0, 0, GenerationReport.StopReason.NO_NETWORK,
                    List.of("no AE2 network reachable (open an ME terminal or hold a linked wireless terminal)"));
        }

        MEStorage storage = network.storage();
        IActionSource source = network.actionSource(player);
        AEItemKey blankKey = NetworkAccess.blankPatternKey();

        long available = network.blankPatterns();
        EAE2Pattern.LOGGER.info("Generation for {}: {} patterns requested, {} blank patterns available",
                who, specs.size(), available);
        if (available < specs.size()) {
            // Requirement #3: refuse before spending anything rather than running out halfway through.
            EAE2Pattern.LOGGER.warn("Generation for {} refused: need {} blank patterns, network has {}",
                    who, specs.size(), available);
            return new GenerationReport(specs.size(), 0, 0, 0, 0,
                    GenerationReport.StopReason.NOT_ENOUGH_BLANK_PATTERNS,
                    List.of("need " + specs.size() + " blank patterns, network has " + available));
        }

        int encoded = 0;
        int inserted = 0;
        int skipped = 0;
        int used = 0;
        List<String> failures = new ArrayList<>();

        for (PatternSpec spec : specs) {
            ItemStack pattern;
            try {
                pattern = PatternEncoder.encode(player.serverLevel(), spec);
                if (pattern.isEmpty()) {
                    throw new PatternEncoder.EncodingException("encoder produced an empty stack");
                }
            } catch (PatternEncoder.EncodingException | RuntimeException e) {
                // Nothing has been spent yet for this recipe, so skipping it is free.
                skipped++;
                String detail = spec.recipeId() + " [" + spec.kind() + "]: " + describe(e);
                EAE2Pattern.LOGGER.warn("Skipping recipe {} for {}: {}", spec.recipeId(), who, describe(e));
                if (failures.size() < MAX_DETAILS_SENT) {
                    failures.add(detail);
                }
                continue;
            }

            long taken = storage.extract(blankKey, 1, Actionable.MODULATE, source);
            if (taken < 1) {
                // Someone drained the blanks between our count and now.
                EAE2Pattern.LOGGER.warn("Generation for {} stopped: blank pattern vanished after {} of {}",
                        who, inserted, specs.size());
                return new GenerationReport(specs.size(), encoded, inserted, skipped, used,
                        GenerationReport.StopReason.NOT_ENOUGH_BLANK_PATTERNS,
                        append(failures, "blank pattern disappeared from the network"));
            }
            used++;
            encoded++;

            AEItemKey encodedKey = AEItemKey.of(pattern);
            long accepted = encodedKey == null ? 0 : storage.insert(encodedKey, 1, Actionable.MODULATE, source);
            if (accepted < 1) {
                // Requirement #4: put the blank back and stop. The player keeps every item they brought.
                storage.insert(blankKey, 1, Actionable.MODULATE, source);
                used--;
                EAE2Pattern.LOGGER.warn("Generation for {} stopped: network refused {} after {} inserts",
                        who, spec.recipeId(), inserted);
                return new GenerationReport(specs.size(), encoded, inserted, skipped, used,
                        GenerationReport.StopReason.STORAGE_FULL,
                        append(failures, "network refused " + spec.recipeId()));
            }
            inserted++;
        }

        if (inserted == 0 && skipped > 0) {
            EAE2Pattern.LOGGER.warn("Generation for {} failed: all {} recipes were unencodable", who, skipped);
            return new GenerationReport(specs.size(), encoded, inserted, skipped, used,
                    GenerationReport.StopReason.ENCODE_FAILED, failures);
        }

        EAE2Pattern.LOGGER.info("Generation for {} finished: requested={} encoded={} inserted={} skipped={} blanksUsed={}",
                who, specs.size(), encoded, inserted, skipped, used);
        return new GenerationReport(specs.size(), encoded, inserted, skipped, used,
                GenerationReport.StopReason.COMPLETED, failures);
    }

    private static List<String> append(List<String> details, String extra) {
        List<String> copy = new ArrayList<>(details);
        copy.add(extra);
        return copy;
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
