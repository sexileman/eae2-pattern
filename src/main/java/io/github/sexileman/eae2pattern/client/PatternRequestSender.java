package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.config.EAE2PatternConfig;
import io.github.sexileman.eae2pattern.net.C2SGeneratePatternsPacket;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Splits a selection into chunks that fit a packet and ships them.
 *
 * <p>Measures the actual wire size, including item/fluid NBT, with a small allowance for packet headers.</p>
 */
public final class PatternRequestSender {

    private static final int DEFAULT_CHUNK_BYTES = 48_000;

    private static final AtomicInteger REQUEST_IDS = new AtomicInteger();

    private PatternRequestSender() {
    }

    public static int send(List<PatternSpec> specs) {
        return send(specs, 0);
    }

    public static int send(List<PatternSpec> specs, int operation) {
        if (specs.isEmpty()) {
            return -1;
        }
        int requestId = REQUEST_IDS.incrementAndGet();
        if (specs.size() > EAE2PatternConfig.MAX_PATTERNS_PER_REQUEST.get())
            throw new IllegalArgumentException("Too many patterns in one request: " + specs.size());
        int maxBytes = Math.max(4096, Math.min(EAE2PatternConfig.MAX_REQUEST_BYTES.get(), DEFAULT_CHUNK_BYTES));

        List<List<PatternSpec>> chunks = new ArrayList<>();
        List<PatternSpec> current = new ArrayList<>();
        int currentBytes = 32;
        for (PatternSpec spec : specs) {
            int specBytes = serializedSize(spec);
            if (specBytes + 32 > maxBytes) throw new IllegalArgumentException("Recipe NBT exceeds packet limit: " + spec.recipeId());
            if (!current.isEmpty() && currentBytes + specBytes > maxBytes) {
                chunks.add(current);
                current = new ArrayList<>();
                currentBytes = 32;
            }
            current.add(spec);
            currentBytes += specBytes;
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }

        if (chunks.size() > 256) throw new IllegalArgumentException("Pattern request needs more than 256 chunks");
        for (int i = 0; i < chunks.size(); i++) {
            PacketHandler.sendToServer(new C2SGeneratePatternsPacket(requestId, i, chunks.size(), chunks.get(i), operation));
        }
        if (operation == 0) EAE2Pattern.LOGGER.info("Sent {} pattern specs in {} chunk(s) (request {}, {} bytes max per chunk)",
                specs.size(), chunks.size(), requestId, maxBytes);
        else EAE2Pattern.LOGGER.debug("Sent read-only pattern inspection {}: {} specs, operation {}", requestId, specs.size(), operation);
        return requestId;
    }

    private static int serializedSize(PatternSpec spec) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { spec.write(buffer); return buffer.readableBytes(); }
        finally { buffer.release(); }
    }
}
