package io.github.sexileman.eae2pattern.net;

import io.github.sexileman.eae2pattern.net.server.BatchRequestManager;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A batch of recipes the player wants encoded, chunked so a large request never exceeds the packet limit.
 *
 * <p>Chunking happens on the client because only the client knows the full selection. The server reassembles
 * by {@link #requestId} and only starts working once {@link #chunkCount} chunks have arrived, which keeps a
 * partially transmitted request from producing a half-generated network.</p>
 */
public record C2SGeneratePatternsPacket(int requestId, int chunkIndex, int chunkCount, List<PatternSpec> specs) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(chunkCount);
        buf.writeVarInt(specs.size());
        for (PatternSpec spec : specs) {
            spec.write(buf);
        }
    }

    public static C2SGeneratePatternsPacket decode(FriendlyByteBuf buf) {
        int requestId = buf.readVarInt();
        int chunkIndex = buf.readVarInt();
        int chunkCount = buf.readVarInt();
        int size = buf.readVarInt();
        List<PatternSpec> specs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            specs.add(PatternSpec.read(buf));
        }
        return new C2SGeneratePatternsPacket(requestId, chunkIndex, chunkCount, specs);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null) {
                BatchRequestManager.acceptChunk(sender, requestId, chunkIndex, chunkCount, specs);
            }
        });
        context.setPacketHandled(true);
    }
}
