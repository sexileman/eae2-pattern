package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.pattern.GenerationReport;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Turns a finished run into something the player can actually see: a chat line always, and a callback to the
 * preview screen when it is still open.
 *
 * <p>The chat line alone is not enough to debug a failure ("it failed" tells nobody anything), so up to three
 * detail lines are printed underneath it in grey. The same details always go to the log, unfiltered.</p>
 */
public final class ClientGenerationFeedback {

    /** Detail lines shown in chat; the server already caps what it sends, this is only about not flooding. */
    private static final int MAX_CHAT_DETAILS = 3;

    @Nullable
    private static GenerationListener listener;

    private ClientGenerationFeedback() {
    }

    public static void setListener(@Nullable GenerationListener newListener) {
        listener = newListener;
    }

    public static void accept(GenerationReport report) {
        EAE2Pattern.LOGGER.info(
                "Generation result: reason={} requested={} encoded={} inserted={} skipped={} blanksUsed={} details={}",
                report.reason(), report.requested(), report.encoded(), report.inserted(), report.skipped(),
                report.blankPatternsUsed(), report.detailsOrEmpty());

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(describe(report), false);
            ChatFormatting style = report.isSuccess() ? ChatFormatting.GRAY : ChatFormatting.RED;
            int shown = Math.min(MAX_CHAT_DETAILS, report.details().size());
            for (int i = 0; i < shown; i++) {
                minecraft.player.displayClientMessage(
                        Component.literal("  ").append(EncodingMessages.explain(report.details().get(i))).withStyle(style), false);
            }
        }

        GenerationListener current = listener;
        if (current != null) {
            current.onGenerationFinished(report);
        }
    }

    public static Component describe(GenerationReport report) {
        return switch (report.reason()) {
            case COMPLETED -> {
                Component base = Component.translatable("eae2_pattern.report.completed",
                        report.inserted(), report.blankPatternsUsed());
                if (report.skipped() > 0) {
                    base = base.copy().append(Component.translatable("eae2_pattern.report.skipped_suffix",
                            report.skipped()));
                }
                yield base;
            }
            case NOT_ENOUGH_WILDCARD_PATTERNS -> Component.translatable("eae2_pattern.report.no_wildcards");
            case NOT_ENOUGH_BLANK_PATTERNS -> Component.translatable("eae2_pattern.report.no_blanks",
                    report.requested(), report.blankPatternsUsed());
            case STORAGE_FULL -> Component.translatable("eae2_pattern.report.storage_full",
                    report.inserted(), report.requested());
            case ENCODE_FAILED -> Component.translatable("eae2_pattern.report.encode_failed",
                    report.skipped(), report.requested());
            case TOO_MANY_PATTERNS -> Component.translatable("eae2_pattern.report.too_many",
                    report.requested());
            case NOTHING_TO_DO -> Component.translatable("eae2_pattern.report.nothing");
            case NO_NETWORK -> Component.translatable("eae2_pattern.report.no_network");
        };
    }
}
