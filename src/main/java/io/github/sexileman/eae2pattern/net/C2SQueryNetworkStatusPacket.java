package io.github.sexileman.eae2pattern.net;

import io.github.sexileman.eae2pattern.net.server.BatchRequestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client asks "how many blank patterns does my network have?" so the preview can warn before anything is spent. */
public record C2SQueryNetworkStatusPacket(int requestId) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
    }

    public static C2SQueryNetworkStatusPacket decode(FriendlyByteBuf buf) {
        return new C2SQueryNetworkStatusPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null) {
                BatchRequestManager.sendNetworkStatus(sender, requestId);
            }
        });
        context.setPacketHandled(true);
    }
}
