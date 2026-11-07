package io.github.sexileman.eae2pattern.pattern;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of a batch generation run. Every stop reason maps to one of the user's requirements, so the GUI can
 * explain *why* it stopped instead of silently producing fewer patterns than asked.
 *
 * @param requested        how many patterns the client asked for
 * @param encoded          how many recipe specs were successfully turned into a pattern stack
 * @param inserted         how many encoded patterns the ME network accepted
 * @param skipped          how many recipes could not be encoded at all (bad layout, no result, too many slots)
 * @param blankPatternsUsed blank patterns actually consumed, i.e. spent for good
 * @param reason           why the run ended
 * @param details          human readable lines; the first few are surfaced in chat so failures are diagnosable
 */
public record GenerationReport(
        int requested,
        int encoded,
        int inserted,
        int skipped,
        int blankPatternsUsed,
        StopReason reason,
        List<String> details
) {

    public enum StopReason {
        /** Everything the user asked for was encoded and inserted. */
        COMPLETED,
        /** Requirement #3: fewer blank patterns in the network than recipes to encode. */
        NOT_ENOUGH_BLANK_PATTERNS,
        /** Requirement #4: the ME network could not accept the next pattern, so we stopped instead of losing it. */
        STORAGE_FULL,
        /** Every recipe failed to encode, nothing was written. */
        ENCODE_FAILED,
        /** The request itself was rejected before any work happened (too many entries for the server cap). */
        TOO_MANY_PATTERNS,
        /** Nothing was selected or the request was cancelled. */
        NOTHING_TO_DO,
        /** The player has no AE2 network in reach (no open terminal, no linked wireless terminal). */
        NO_NETWORK,
        NOT_ENOUGH_WILDCARD_PATTERNS;

        public boolean isFailure() {
            return this != COMPLETED;
        }
    }

    public GenerationReport {
        details = List.copyOf(details);
    }

    public static GenerationReport nothingToDo() {
        return new GenerationReport(0, 0, 0, 0, 0, StopReason.NOTHING_TO_DO, List.of());
    }

    public static GenerationReport rejected(int requested, int limit) {
        return new GenerationReport(requested, 0, 0, 0, 0, StopReason.TOO_MANY_PATTERNS,
                List.of("requested " + requested + ", server limit maxPatternsPerRequest=" + limit));
    }

    public boolean isSuccess() {
        return reason == StopReason.COMPLETED;
    }

    public GenerationReport withDetail(String detail) {
        List<String> copy = new ArrayList<>(details);
        copy.add(detail);
        return new GenerationReport(requested, encoded, inserted, skipped, blankPatternsUsed, reason, copy);
    }

    @Nullable
    public String summaryKey() {
        return switch (reason) {
            case COMPLETED -> "eae2_pattern.report.completed";
            case NOT_ENOUGH_WILDCARD_PATTERNS -> "eae2_pattern.report.no_wildcards";
            case NOT_ENOUGH_BLANK_PATTERNS -> "eae2_pattern.report.no_blanks";
            case STORAGE_FULL -> "eae2_pattern.report.storage_full";
            case ENCODE_FAILED -> "eae2_pattern.report.encode_failed";
            case TOO_MANY_PATTERNS -> "eae2_pattern.report.too_many";
            case NOTHING_TO_DO -> "eae2_pattern.report.nothing";
            case NO_NETWORK -> "eae2_pattern.report.no_network";
        };
    }

    public List<String> detailsOrEmpty() {
        return Collections.unmodifiableList(details);
    }
}
