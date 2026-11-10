package io.github.sexileman.eae2pattern.net.server;

import appeng.menu.me.crafting.CraftConfirmMenu;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.cycle.BoundedRateLimiter;
import io.github.sexileman.eae2pattern.cycle.CycleDiagramData;
import io.github.sexileman.eae2pattern.cycle.CycleLimits;
import io.github.sexileman.eae2pattern.cycle.GrowthPlanView;
import io.github.sexileman.eae2pattern.mixin.CycleConfirmMenuAccessor;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import io.github.sexileman.eae2pattern.net.S2CCycleDiagramPacket;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Menu authorization and request lifetime belong to the server, not the wire packet. */
@Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID)
public final class CycleDiagramQueries {
    private static final BoundedRateLimiter<UUID> REQUESTS =
            new BoundedRateLimiter<>(CycleLimits.QUERY_COOLDOWN_NANOS, CycleLimits.QUERY_PLAYERS);

    private CycleDiagramQueries() {}

    public static void query(ServerPlayer player, int menu, int request) {
        if (player == null
                || request < 0
                || player.containerMenu.containerId != menu
                || !(player.containerMenu instanceof CraftConfirmMenu confirm)) return;
        if (!REQUESTS.acquire(player.getUUID(), System.nanoTime())) return;
        var data =
                CycleDiagramData.status(
                        io.github.sexileman.eae2pattern.cycle.CycleDiagramSnapshot.State
                                .UNSUPPORTED);
        if (confirm instanceof CycleConfirmMenuAccessor accessor) {
            var plan = accessor.eae2$currentPlan();
            if (plan == null)
                data =
                        CycleDiagramData.status(
                                io.github.sexileman.eae2pattern.cycle.CycleDiagramSnapshot.State
                                        .LOADING);
            else if ((Object) plan instanceof GrowthPlanView view && view.eae2$diagram() != null)
                data = view.eae2$diagram();
        }
        PacketHandler.sendToPlayer(player, new S2CCycleDiagramPacket(menu, request, data));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        REQUESTS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        REQUESTS.clear();
    }
}
