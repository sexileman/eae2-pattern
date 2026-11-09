package io.github.sexileman.eae2pattern.net;

import io.github.sexileman.eae2pattern.net.server.CycleDiagramQueries;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SCycleDiagramPacket(int menu, int request) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(menu);
        buffer.writeVarInt(request);
    }

    public static C2SCycleDiagramPacket decode(FriendlyByteBuf buffer) {
        return new C2SCycleDiagramPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> CycleDiagramQueries.query(context.getSender(), menu, request));
        context.setPacketHandled(true);
    }
}
