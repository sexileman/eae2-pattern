package io.github.sexileman.eae2pattern.client;
import io.github.sexileman.eae2pattern.net.S2CInspectionPacket;
import java.util.function.Consumer;

/** Screen lifetime listener; request IDs reject responses from an older focus or policy. */
public final class InspectionFeedback {
    private static Consumer<S2CInspectionPacket> listener;
    private InspectionFeedback() {}
    public static void setListener(Consumer<S2CInspectionPacket> value) { listener = value; }
    public static void accept(S2CInspectionPacket packet) { if (listener != null) listener.accept(packet); }
}
