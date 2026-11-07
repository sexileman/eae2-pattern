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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Reassembles chunked generation requests per player and runs them once the last chunk arrives.
 *
 * <p>Chunking exists because a single JEI machine can easily have hundreds of recipes, each with up to 81
 * inputs; that does not fit in one packet. Reassembly exists so a dropped chunk can never cause a partial
 * run: nothing is encoded until every chunk is present.</p>
 */
@Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID)
public final class BatchRequestManager {

    private static final long TIMEOUT_MS = 10_000L;
    private static final int MAX_CHUNKS = 256;

    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    private BatchRequestManager() {
    }

    public static void acceptChunk(ServerPlayer player, int requestId, int chunkIndex, int chunkCount, List<PatternSpec> specs, int operation) {
        if (operation == 3) {
            if (specs.isEmpty() && chunkIndex == 0 && chunkCount == 1) {
                PatternInspection.cancel(player.getUUID(), requestId);
                PlayerState pending = STATES.get(player.getUUID());
                if (pending != null && pending.requestId == requestId) STATES.remove(player.getUUID());
            }
            return;
        }
        if ((PatternWriter.busy(player.getUUID()) || PatternInspection.busy(player.getUUID())) && operation != 2) {
            if (operation == 1) PacketHandler.sendToPlayer(player, new io.github.sexileman.eae2pattern.net.S2CInspectionPacket(
                    requestId, operation, false, 0, 0, List.of(), List.of(), List.of("Another pattern job is running")));
            else PacketHandler.sendToPlayer(player, new S2CGenerationResultPacket(
                    new GenerationReport(0, 0, 0, 0, 0, GenerationReport.StopReason.ENCODE_FAILED, List.of("Another pattern job is running"))));
            return;
        }
        if (chunkCount <= 0 || chunkCount > MAX_CHUNKS || chunkIndex < 0 || chunkIndex >= chunkCount) {
            EAE2Pattern.LOGGER.warn("Dropping malformed pattern request from {}: chunk {}/{}",
                    player.getGameProfile().getName(), chunkIndex, chunkCount);
            return;
        }

        PlayerState state = STATES.computeIfAbsent(player.getUUID(), uuid -> new PlayerState());
        if (state.requestId == requestId && state.operation != operation) return;
        state.operation = operation;
        List<PatternSpec> complete;
        try { complete = state.addChunk(requestId, chunkIndex, chunkCount, specs); }
        catch (IllegalArgumentException e) {
            STATES.remove(player.getUUID());
            if (operation == 0 || operation == 4) PacketHandler.sendToPlayer(player, new S2CGenerationResultPacket(
                    new GenerationReport(specs.size(), 0, 0, 0, 0, GenerationReport.StopReason.TOO_MANY_PATTERNS, List.of(e.getMessage()))));
            else PacketHandler.sendToPlayer(player, new io.github.sexileman.eae2pattern.net.S2CInspectionPacket(
                    requestId, operation, false, 0, 0, List.of(), List.of(), List.of(e.getMessage())));
            return;
        }
        if (complete == null) {
            return;
        }
        if (operation == 4) MachineWildcardWriter.start(player, complete,
                report -> PacketHandler.sendToPlayer(player, new S2CGenerationResultPacket(report)));
        else if (operation == 0) execute(player, complete);
        else if (!complete.isEmpty()) PatternInspection.start(player, complete, requestId, operation);
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

        PatternWriter.start(player, specs, report -> PacketHandler.sendToPlayer(player, new S2CGenerationResultPacket(report)));
    }

    /** Called when a player disconnects so a half-transmitted request cannot leak. */
    public static void forget(UUID playerId) {
        STATES.remove(playerId);
        PatternWriter.forget(playerId);
        PatternInspection.forget(playerId);
    }

    @SubscribeEvent public static void expire(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) STATES.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().lastTouched > TIMEOUT_MS);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { STATES.clear(); }
    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) { forget(event.getEntity().getUUID()); }

    /** One in-flight request for one player. */
    private static final class PlayerState {
        private int requestId = -1;
        private int operation;
        private int chunkCount;
        private int totalSpecs;
        private long totalBytes;
        private final Map<Integer, List<PatternSpec>> chunks = new HashMap<>();
        private long lastTouched = System.currentTimeMillis();

        synchronized List<PatternSpec> addChunk(int requestId, int chunkIndex, int chunkCount, List<PatternSpec> specs) {
            long now = System.currentTimeMillis();
            if (this.requestId != requestId || now - lastTouched > TIMEOUT_MS) {
                this.requestId = requestId;
                this.chunkCount = chunkCount;
                this.chunks.clear();
                this.totalSpecs = 0;
                this.totalBytes = 0;
            }
            if (this.chunkCount != chunkCount) return null;
            this.lastTouched = now;
            this.chunkCount = chunkCount;

            if (chunks.putIfAbsent(chunkIndex, specs) != null) {
                EAE2Pattern.LOGGER.warn("Duplicate chunk {} for request {}", chunkIndex, requestId);
                return null;
            }
            totalSpecs += specs.size();
            net.minecraft.network.FriendlyByteBuf buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try { for (PatternSpec spec : specs) spec.write(buffer); totalBytes += buffer.readableBytes(); }
            finally { buffer.release(); }
            if (totalSpecs > EAE2PatternConfig.MAX_PATTERNS_PER_REQUEST.get() || totalBytes > 16_000_000L) {
                chunks.clear(); this.requestId = -1; totalSpecs = 0; totalBytes = 0;
                throw new IllegalArgumentException("Request exceeds recipe or 16 MB assembly limit");
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
