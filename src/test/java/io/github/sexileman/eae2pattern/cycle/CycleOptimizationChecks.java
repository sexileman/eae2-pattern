package io.github.sexileman.eae2pattern.cycle;

import io.github.sexileman.eae2pattern.client.CycleDiagramSession;
import io.github.sexileman.eae2pattern.client.CyclePlanViewModel;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

public final class CycleOptimizationChecks {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static final class ReloadableConfig {
        public static ReloadableConfig INSTANCE = new ReloadableConfig(4);
        public int limit;

        public ReloadableConfig(int limit) {
            this.limit = limit;
        }
    }

    public static int run() throws Exception {
        var all = new ArrayList<Integer>();
        for (int i = 0; i < 600; i++) all.add(i);
        var cursor = new ProviderCursor<Integer>();
        var first = cursor.next(all, CycleLimits.PROVIDERS_PER_PASS);
        var second = cursor.next(all, CycleLimits.PROVIDERS_PER_PASS);
        var third = cursor.next(all, CycleLimits.PROVIDERS_PER_PASS);
        check(
                first.size() == 256 && second.size() == 256 && third.size() == 88,
                "provider work remains bounded per pass");
        check(
                !first.contains(300) && second.contains(300),
                "an idle provider beyond the old 256 cap is reachable next pass");
        var visited = new ArrayList<Integer>();
        visited.addAll(first);
        visited.addAll(second);
        visited.addAll(third);
        check(
                visited.equals(all),
                "stable provider view is visited completely without starving or duplicating its"
                        + " tail");
        check(cursor.next(all, 256).get(0) == 0, "exhausted scan restarts normally");
        check(
                cursor.next(List.of(999), 256).equals(List.of(999)),
                "replaced provider collection invalidates a stale cursor");
        cursor.next(all, 256);
        all.add(600);
        check(
                cursor.next(all, 256).isEmpty(),
                "network mutation invalidates iterator without cancelling crafting");
        check(cursor.next(all, 256).get(0) == 0, "scan recovers from a changed network");

        var limiter = new BoundedRateLimiter<String>(10, 2);
        check(limiter.acquire("a", -1000), "first request works with a negative nanoTime origin");
        check(
                !limiter.acquire("a", -995) && limiter.acquire("a", -990),
                "request interval is relative to a real sample");
        limiter.acquire("b", 1);
        limiter.acquire("c", 1);
        check(limiter.acquire("a", 2), "old player entries are evicted at capacity");
        limiter.remove("a");
        check(limiter.acquire("a", 3), "logout removes cooldown state");
        limiter.clear();
        check(limiter.acquire("a", 4), "server stop clears cooldown state");

        var sent = new ArrayList<int[]>();
        var session =
                new CycleDiagramSession(7, (menu, nonce) -> sent.add(new int[] {menu, nonce}));
        int oldRequest = sent.get(0)[1];
        check(
                !session.accept(8, oldRequest, CycleDiagramData.status("planned")),
                "another menu cannot replace this snapshot");
        for (int i = 0; i < CycleLimits.QUERY_INTERVAL_TICKS; i++) session.tick();
        check(
                sent.size() == 2
                        && !session.accept(7, oldRequest, CycleDiagramData.status("planned")),
                "a late response from an older loading attempt is ignored");
        check(
                session.accept(7, sent.get(1)[1], CycleDiagramData.status("planned")),
                "current response completes loading");
        for (int i = 0; i < 100; i++) session.tick();
        check(
                sent.size() == 2
                        && session.snapshot().state() == CycleDiagramSnapshot.State.PLANNED,
                "loaded snapshots do not poll or silently refresh stock");
        var retries = new ArrayList<Integer>();
        var timed = new CycleDiagramSession(7, (menu, nonce) -> retries.add(nonce));
        for (int i = 0; i < CycleLimits.QUERY_INTERVAL_TICKS * CycleLimits.QUERY_ATTEMPTS; i++)
            timed.tick();
        check(
                retries.size() == CycleLimits.QUERY_ATTEMPTS
                        && timed.snapshot().state() == CycleDiagramSnapshot.State.TIMEOUT,
                "loading retries stop at the existing attempt limit");
        check(
                CycleDiagramSnapshot.decode(CycleDiagramData.status("unknown-future-state")).state()
                        == CycleDiagramSnapshot.State.UNSUPPORTED,
                "unknown wire state fails closed as an unavailable view");

        var recipe = new CycleDiagramSnapshot.Recipe(List.of(), List.of());
        var snapshot =
                new CycleDiagramSnapshot(
                        CycleDiagramSnapshot.State.PLANNED,
                        null,
                        List.of(),
                        List.of(recipe),
                        List.of(new CycleDiagramSnapshot.Phase(0, 10000)),
                        List.of(),
                        List.of(),
                        List.of(),
                        CycleDiagramSnapshot.MaterialReason.READY);
        var rows = CyclePlanViewModel.rows(snapshot, CyclePlanViewModel.Tab.PHASES);
        check(
                rows.size() == 1
                        && rows.get(0).times() == 10000
                        && rows.get(0).inputs() == recipe.inputs(),
                "phase view reuses immutable recipe inputs instead of expanding quantity or NBT");

        var large =
                new CompoundTag() {
                    @Override
                    public String toString() {
                        throw new AssertionError("oversized NBT must not be rendered");
                    }
                };
        large.putByteArray("large", new byte[10000]);
        check(
                CycleLog.describeTag(large, "test:item").contains("NBT omitted"),
                "log budget is enforced before rendering a large NBT string");
        var singleton = ReloadableConfig.class.getField("INSTANCE");
        var field = ReloadableConfig.class.getField("limit");
        check(
                GrowthBatchBridge.readSingletonInt(singleton, field) == 4,
                "optional scaling reads the current config instance");
        ReloadableConfig.INSTANCE = new ReloadableConfig(32);
        check(
                GrowthBatchBridge.readSingletonInt(singleton, field) == 32,
                "cached reflection preserves replacement-based config reload");
        return checks;
    }
}
