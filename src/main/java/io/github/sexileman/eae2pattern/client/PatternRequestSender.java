package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.config.EAE2PatternConfig;
import io.github.sexileman.eae2pattern.net.C2SGeneratePatternsPacket;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import io.github.sexileman.eae2pattern.pattern.StackSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Splits a selection into chunks that fit a packet and ships them.
 *
 * <p>The byte estimate is deliberately pessimistic: a spec is charged a fixed overhead plus its serialized
 * stacks, which is enough to keep a 400-recipe machine under the packet cap without measuring the real buffer.</p>
 */
public final class PatternRequestSender {

    private static final int BYTES_PER_SPEC_OVERHEAD = 24;
    private static final int BYTES_PER_STACK = 24;
    private static final int DEFAULT_CHUNK_BYTES = 48_000;

    private static final AtomicInteger REQUEST_IDS = new AtomicInteger();

    private PatternRequestSender() {
    }

    public static int send(List<PatternSpec> specs) {
        if (specs.isEmpty()) {
            return -1;
        }
        int requestId = REQUEST_IDS.incrementAndGet();
        int maxBytes = Math.max(4096, Math.min(EAE2PatternConfig.MAX_REQUEST_BYTES.get(), DEFAULT_CHUNK_BYTES));

        List<List<PatternSpec>> chunks = new ArrayList<>();
        List<PatternSpec> current = new ArrayList<>();
        int currentBytes = 0;
        for (PatternSpec spec : specs) {
            int specBytes = estimate(spec);
            if (!current.isEmpty() && currentBytes + specBytes > maxBytes) {
                chunks.add(current);
                current = new ArrayList<>();
                currentBytes = 0;
            }
            current.add(spec);
            currentBytes += specBytes;
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }

        for (int i = 0; i < chunks.size(); i++) {
            PacketHandler.sendToServer(new C2SGeneratePatternsPacket(requestId, i, chunks.size(), chunks.get(i)));
        }
        EAE2Pattern.LOGGER.info("Sent {} pattern specs in {} chunk(s) (request {}, {} bytes max per chunk)",
                specs.size(), chunks.size(), requestId, maxBytes);
        return requestId;
    }

    private static int estimate(PatternSpec spec) {
        int bytes = BYTES_PER_SPEC_OVERHEAD;
        bytes += spec.recipeId().toString().length();
        bytes += (spec.inputs().size() + spec.outputs().size()) * BYTES_PER_STACK;
        for (StackSpec stack : spec.inputs()) {
            bytes += stack.id().toString().length();
        }
        for (StackSpec stack : spec.outputs()) {
            bytes += stack.id().toString().length();
        }
        return bytes;
    }
}
