package io.github.sexileman.eae2pattern.net;

import io.github.sexileman.eae2pattern.pattern.GenerationReport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server tells the client how a generation run ended, including the reason it stopped. */
public record S2CGenerationResultPacket(GenerationReport report) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(report.requested());
        buf.writeVarInt(report.encoded());
        buf.writeVarInt(report.inserted());
        buf.writeVarInt(report.skipped());
        buf.writeVarInt(report.blankPatternsUsed());
        buf.writeByte(report.reason().ordinal());
        buf.writeVarInt(report.details().size());
        for (String detail : report.details()) {
            buf.writeUtf(detail, 1024);
        }
    }

    public static S2CGenerationResultPacket decode(FriendlyByteBuf buf) {
        int requested = buf.readVarInt();
        int encoded = buf.readVarInt();
        int inserted = buf.readVarInt();
        int skipped = buf.readVarInt();
        int blanksUsed = buf.readVarInt();
        GenerationReport.StopReason reason = GenerationReport.StopReason.values()[buf.readByte()];
        int detailCount = buf.readVarInt();
        List<String> details = new ArrayList<>(detailCount);
        for (int i = 0; i < detailCount; i++) {
            details.add(buf.readUtf(1024));
        }
        return new S2CGenerationResultPacket(
                new GenerationReport(requested, encoded, inserted, skipped, blanksUsed, reason, details));
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> io.github.sexileman.eae2pattern.client.ClientGenerationFeedback.accept(report));
        context.setPacketHandled(true);
    }
}
