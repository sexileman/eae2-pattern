package io.github.sexileman.eae2pattern.net.server;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.api.util.DimensionalBlockPos;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the AE2 network a player is allowed to write to.
 *
 * <p>Two sources, in order of certainty:</p>
 * <ol>
 *     <li>The AE2 terminal the player currently has open. This is unambiguous: the player deliberately opened a
 *     terminal, so the network it is attached to is the one they mean.</li>
 *     <li>A linked wireless terminal in the player's inventory. This is what makes the JEI button usable without
 *     first opening a terminal, which is the whole point of the feature.</li>
 * </ol>
 *
 * <p>Nothing else is guessed. Walking up to a random cable and picking its grid would let a player write into
 * networks they cannot otherwise reach, which is a griefing vector on servers.</p>
 */
public final class NetworkAccess {

    private final IGrid grid;

    @Nullable
    private final IActionHost actionHost;

    private NetworkAccess(IGrid grid, @Nullable IActionHost actionHost) {
        this.grid = grid;
        this.actionHost = actionHost;
    }

    @Nullable
    public static NetworkAccess resolve(ServerPlayer player) {
        NetworkAccess fromTerminal = fromOpenTerminal(player);
        if (fromTerminal != null) {
            return fromTerminal;
        }
        IGrid grid = fromWirelessTerminal(player);
        return grid == null ? null : new NetworkAccess(grid, null);
    }

    @Nullable
    private static NetworkAccess fromOpenTerminal(ServerPlayer player) {
        if (!(player.containerMenu instanceof MEStorageMenu menu)) {
            return null;
        }
        ITerminalHost host = menu.getHost();
        if (!(host instanceof IActionHost actionHost)) {
            return null;
        }
        IGridNode node = actionHost.getActionableNode();
        return node == null ? null : new NetworkAccess(node.getGrid(), actionHost);
    }

    @Nullable
    private static IGrid fromWirelessTerminal(ServerPlayer player) {
        IGrid grid = linkedGrid(player, player.getMainHandItem());
        if (grid != null) {
            return grid;
        }
        grid = linkedGrid(player, player.getOffhandItem());
        if (grid != null) {
            return grid;
        }
        for (ItemStack stack : player.getInventory().items) {
            grid = linkedGrid(player, stack);
            if (grid != null) {
                return grid;
            }
        }
        return null;
    }

    @Nullable
    private static IGrid linkedGrid(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof WirelessTerminalItem terminal)) {
            return null;
        }
        if (!terminal.hasPower(player, 0.5, stack)) {
            return null;
        }
        IGrid grid = terminal.getLinkedGrid(stack, player.level(), null);
        if (grid == null) {
            return null;
        }
        // getLinkedGrid itself does not check range: AE2 only does that in WirelessTerminalMenuHost.rangeCheck().
        // Without this gate a player could link a terminal once and then drain a network from across the world.
        return inRangeOfAccessPoint(grid, player) ? grid : null;
    }

    private static boolean inRangeOfAccessPoint(IGrid grid, ServerPlayer player) {
        for (WirelessAccessPointBlockEntity accessPoint : grid.getMachines(WirelessAccessPointBlockEntity.class)) {
            IWirelessAccessPoint wap = accessPoint;
            if (!wap.isActive()) {
                continue;
            }
            DimensionalBlockPos location = wap.getLocation();
            if (location.getLevel().dimension() != player.level().dimension()) {
                continue;
            }
            double range = wap.getRange();
            if (player.distanceToSqr(Vec3.atCenterOf(location.getPos())) <= range * range) {
                return true;
            }
        }
        return false;
    }

    public MEStorage storage() {
        return grid.getStorageService().getInventory();
    }

    public boolean sameNetwork(NetworkAccess other) { return grid == other.grid; }
    public long storedAmount(AEItemKey key) {
        return key == null ? 0 : grid.getStorageService().getCachedInventory().get(key);
    }

    /** Exact count of blank patterns; the cached inventory is refreshed by AE2 at most once per tick. */
    public long blankPatterns() {
        return grid.getStorageService().getCachedInventory().get(blankPatternKey());
    }

    public IActionSource actionSource(ServerPlayer player) {
        return this.actionHost == null ? IActionSource.ofPlayer(player) : IActionSource.ofPlayer(player, this.actionHost);
    }

    public static AEItemKey blankPatternKey() {
        return AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
    }
}
