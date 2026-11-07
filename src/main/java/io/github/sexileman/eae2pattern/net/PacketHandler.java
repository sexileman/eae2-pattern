package io.github.sexileman.eae2pattern.net;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Single channel for everything the mod talks about. Versioned so a mismatched client is rejected with a clear
 * message instead of deserialising garbage.
 */
public final class PacketHandler {

    private static final String PROTOCOL = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(EAE2Pattern.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private static int nextId = 0;

    private PacketHandler() {
    }

    public static void register() {
        CHANNEL.messageBuilder(S2CInspectionPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CInspectionPacket::encode).decoder(S2CInspectionPacket::decode)
                .consumerMainThread(S2CInspectionPacket::handle).add();
        CHANNEL.messageBuilder(C2SGeneratePatternsPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SGeneratePatternsPacket::encode)
                .decoder(C2SGeneratePatternsPacket::decode)
                .consumerMainThread(C2SGeneratePatternsPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SQueryNetworkStatusPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SQueryNetworkStatusPacket::encode)
                .decoder(C2SQueryNetworkStatusPacket::decode)
                .consumerMainThread(C2SQueryNetworkStatusPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CNetworkStatusPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CNetworkStatusPacket::encode)
                .decoder(S2CNetworkStatusPacket::decode)
                .consumerMainThread(S2CNetworkStatusPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CGenerationResultPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CGenerationResultPacket::encode)
                .decoder(S2CGenerationResultPacket::decode)
                .consumerMainThread(S2CGenerationResultPacket::handle)
                .add();
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
