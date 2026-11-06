package io.github.sexileman.eae2pattern.net;

import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record S2CInspectionPacket(int requestId, int operation, boolean success, int checked, int failed,
                                  List<ResourceLocation> existing, List<PatternSpec> preview, List<String> details) {
    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(requestId); b.writeByte(operation); b.writeBoolean(success); b.writeVarInt(checked); b.writeVarInt(failed);
        b.writeVarInt(existing.size()); existing.forEach(b::writeResourceLocation);
        b.writeVarInt(preview.size()); preview.forEach(p -> p.write(b));
        b.writeVarInt(details.size()); details.forEach(b::writeUtf);
    }
    public static S2CInspectionPacket decode(FriendlyByteBuf b) {
        int id = b.readVarInt(), operation = b.readUnsignedByte();
        if (operation < 1 || operation > 2) throw new IllegalArgumentException("Invalid inspection operation");
        boolean success = b.readBoolean(); int checked = b.readVarInt(), failed = b.readVarInt();
        if (checked < 0 || checked > 65536 || failed < 0 || failed > checked) throw new IllegalArgumentException("Invalid inspection counters");
        int count = b.readVarInt();
        if (count < 0 || count > 65536) throw new IllegalArgumentException("Invalid inspection result count");
        List<ResourceLocation> existing = new ArrayList<>(count);
        for (int i = 0; i < count; i++) existing.add(b.readResourceLocation());
        int previews = b.readVarInt();
        if (previews < 0 || previews > 1) throw new IllegalArgumentException("Invalid preview count");
        List<PatternSpec> preview = new ArrayList<>();
        for (int i = 0; i < previews; i++) preview.add(PatternSpec.read(b));
        int lines = b.readVarInt();
        if (lines < 0 || lines > 5) throw new IllegalArgumentException("Invalid detail count");
        List<String> details = new ArrayList<>();
        for (int i = 0; i < lines; i++) details.add(b.readUtf());
        return new S2CInspectionPacket(id, operation, success, checked, failed, existing, preview, details);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> io.github.sexileman.eae2pattern.client.InspectionFeedback.accept(this));
        context.setPacketHandled(true);
    }
}
