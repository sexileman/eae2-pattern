package io.github.sexileman.eae2pattern.net.server;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.config.EAE2PatternConfig;
import io.github.sexileman.eae2pattern.net.S2CGenerationResultPacket;
import io.github.sexileman.eae2pattern.net.S2CNetworkStatusPacket;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import io.github.sexileman.eae2pattern.pattern.GenerationReport;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reassembles chunked generation requests per player and runs them once the last chunk arrives.
 *
 * <p>Chunking exists because a single JEI machine can easily have hundreds of recipes, each with up to 81
 * inputs; that does not fit in one packet. Reassembly exists so a dropped chunk can never cause a partial
 * run: nothing is encoded until every chunk is present.</p>
 */
public final class BatchRequestManager {

    private static final long TIMEOUT_MS = 10_000L;
    private static final int MAX_CHUNKS = 256;

    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    private BatchRequestManager() {
    }

    public static void acceptChunk(ServerPlayer player, int requestId, int chunkIndex, int chunkCount, List<PatternSpec> specs) {
        if (chunkCount <= 0 || chunkCount > MAX_CHUNKS || chunkIndex < 0 || chunkIndex >= chunkCount) {
            EAE2Pattern.LOGGER.warn("Dropping malformed pattern request from {}: chunk {}/{}",
                    player.getGameProfile().getName(), chunkIndex, chunkCount);
            return;
        }

        PlayerState state = STATES.computeIfAbsent(player.getUUID(), uuid -> new PlayerState());
        List<PatternSpec> complete = state.addChunk(requestId, chunkIndex, chunkCount, specs);
        if (complete == null) {
            return;
        }
        execute(player, complete);
    }

    public static void sendNetworkStatus(ServerPlayer player, int requestId) {
        long blanks = PatternWriter.countBlankPatterns(player);
        boolean hasNetwork = PatternWriter.hasNetwork(player);
        String detail = hasNetwork ? "" : "player has no addressable AE2 network";
        PacketHandler.sendToPlayer(player, new S2CNetworkStatusPacket(requestId, hasNetwork, blanks, detail));
    }

    private static void execute(ServerPlayer player, List<PatternSpec> specs) {
        String who = player.getGameProfile().getName();
        int max = EAE2PatternConfig.MAX_PATTERNS_PER_REQUEST.get();
        EAE2Pattern.LOGGER.info("Assembled pattern request from {}: {} specs (config maxPatternsPerRequest={})",
                who, specs.size(), max);
        if (specs.size() > max) {
            EAE2Pattern.LOGGER.warn("Rejecting request from {}: {} patterns exceeds maxPatternsPerRequest={}",
                    who, specs.size(), max);
            PacketHandler.sendToPlayer(player, new S2CGenerationResultPacket(GenerationReport.rejected(specs.size(), max)));
            return;
        }

        GenerationReport report;
        try {
            report = PatternWriter.write(player, specs);
        } catch (RuntimeException e) {
            EAE2Pattern.LOGGER.error("Pattern generation failed for {}", who, e);
            report = new GenerationReport(specs.size(), 0, 0, 0, 0, GenerationReport.StopReason.ENCODE_FAILED,
                    List.of(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
        PacketHandler.sendToPlayer(player, new S2CGenerationResultPacket(report));
    }

    /** Called when a player disconnects so a half-transmitted request cannot leak. */
    public static void forget(UUID playerId) {
        STATES.remove(playerId);
    }

    /** One in-flight request for one player. */
    private static final class PlayerState {
        private int requestId = -1;
        private int chunkCount;
        private final Map<Integer, List<PatternSpec>> chunks = new HashMap<>();
        private long lastTouched = System.currentTimeMillis();

        synchronized List<PatternSpec> addChunk(int requestId, int chunkIndex, int chunkCount, List<PatternSpec> specs) {
            long now = System.currentTimeMillis();
            if (this.requestId != requestId || now - lastTouched > TIMEOUT_MS) {
                this.requestId = requestId;
                this.chunkCount = chunkCount;
                this.chunks.clear();
            }
            this.lastTouched = now;
            this.chunkCount = chunkCount;

            if (chunks.putIfAbsent(chunkIndex, specs) != null) {
                EAE2Pattern.LOGGER.warn("Duplicate chunk {} for request {}", chunkIndex, requestId);
                return null;
            }
            if (chunks.size() < chunkCount) {
                return null;
            }

            List<PatternSpec> assembled = new ArrayList<>();
            for (int i = 0; i < chunkCount; i++) {
                List<PatternSpec> chunk = chunks.get(i);
                if (chunk == null) {
                    return null;
                }
                assembled.addAll(chunk);
            }
            chunks.clear();
            this.requestId = -1;
            return assembled;
        }
    }
}
