package io.github.sexileman.eae2pattern.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Requirement #3: "if the recipe count is larger than the blank patterns in the AE network, warn the player".
 *
 * <p>{@code blankPatterns} is exact: AE2's storage exposes a {@code KeyCounter}, so we can count what is
 * actually stored instead of guessing. {@code hasNetwork} being false means the player has no AE2 network we
 * can address at all, which is a different problem from "not enough blanks" and gets its own message.</p>
 */
public record S2CNetworkStatusPacket(int requestId, boolean hasNetwork, long blankPatterns, String detail) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeBoolean(hasNetwork);
        buf.writeVarLong(blankPatterns);
        buf.writeUtf(detail, 2048);
    }

    public static S2CNetworkStatusPacket decode(FriendlyByteBuf buf) {
        return new S2CNetworkStatusPacket(buf.readVarInt(), buf.readBoolean(), buf.readVarLong(), buf.readUtf(2048));
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> io.github.sexileman.eae2pattern.client.ClientNetworkState.accept(this));
        context.setPacketHandled(true);
    }
}
