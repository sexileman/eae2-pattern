package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.net.S2CNetworkStatusPacket;

/**
 * Last known state of the player's AE2 network, as reported by the server.
 *
 * <p>The client never inspects storage itself: AE2 storage lives on the server, and a client-side guess would
 * disagree with reality the moment a crafting job drains a cell. The preview screen therefore renders what
 * the server last told it and refreshes on open.</p>
 */
public final class ClientNetworkState {

    private static volatile S2CNetworkStatusPacket lastStatus;

    private ClientNetworkState() {
    }

    public static void accept(S2CNetworkStatusPacket packet) {
        lastStatus = packet;
    }

    public static boolean hasStatus() {
        return lastStatus != null;
    }

    public static boolean hasNetwork() {
        S2CNetworkStatusPacket status = lastStatus;
        return status != null && status.hasNetwork();
    }

    public static long blankPatterns() {
        S2CNetworkStatusPacket status = lastStatus;
        return status == null ? 0L : status.blankPatterns();
    }

    public static String detail() {
        S2CNetworkStatusPacket status = lastStatus;
        return status == null ? "" : status.detail();
    }

    public static void reset() {
        lastStatus = null;
    }
}
