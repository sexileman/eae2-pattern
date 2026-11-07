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
public record C2SGeneratePatternsPacket(int requestId, int chunkIndex, int chunkCount, List<PatternSpec> specs, int operation, net.minecraft.nbt.CompoundTag rules) {
    public C2SGeneratePatternsPacket(int requestId, int chunkIndex, int chunkCount, List<PatternSpec> specs, int operation) {
        this(requestId, chunkIndex, chunkCount, specs, operation, new net.minecraft.nbt.CompoundTag());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(operation);
        buf.writeVarInt(requestId);
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(chunkCount);
        buf.writeVarInt(specs.size());
        for (PatternSpec spec : specs) {
            spec.write(buf);
        }
        if (operation == 4) buf.writeNbt(io.github.sexileman.eae2pattern.wildcard.WildcardEditorState.clean(rules));
    }

    public static C2SGeneratePatternsPacket decode(FriendlyByteBuf buf) {
        if (buf.readableBytes() > io.github.sexileman.eae2pattern.config.EAE2PatternConfig.MAX_REQUEST_BYTES.get())
            throw new IllegalArgumentException("Pattern packet exceeds byte limit");
        int operation = buf.readUnsignedByte();
        if (operation > 4) throw new IllegalArgumentException("Unknown pattern operation");
        int requestId = buf.readVarInt();
        int chunkIndex = buf.readVarInt();
        int chunkCount = buf.readVarInt();
        int size = buf.readVarInt();
        if (size < 0 || size > io.github.sexileman.eae2pattern.config.EAE2PatternConfig.MAX_PATTERNS_PER_REQUEST.get())
            throw new IllegalArgumentException("Invalid pattern count");
        List<PatternSpec> specs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            specs.add(PatternSpec.read(buf));
        }
        var rules = operation == 4 ? io.github.sexileman.eae2pattern.wildcard.WildcardEditorState.clean(buf.readNbt()) : new net.minecraft.nbt.CompoundTag();
        return new C2SGeneratePatternsPacket(requestId, chunkIndex, chunkCount, specs, operation, rules);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null) {
                BatchRequestManager.acceptChunk(sender, requestId, chunkIndex, chunkCount, specs, operation, rules);
            }
        });
        context.setPacketHandled(true);
    }
}
