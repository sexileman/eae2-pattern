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

    public static final class LongConfig {
        public static LongConfig INSTANCE = new LongConfig();
        public long limit = Long.MAX_VALUE;
        public Long boxed = 4_294_967_296L;
    }

    public static int run() throws Exception {
        var all = new ArrayList<Integer>();
        for (int i = 0; i < 600; i++) all.add(i);
        var cursor = new ProviderCursor<Integer>();
        var visited = new ArrayList<Integer>();
        while (visited.size() < all.size()) {
            var pass = cursor.next(all, CycleLimits.PROVIDERS_PER_PASS);
            check(
                    !pass.isEmpty() && pass.size() <= CycleLimits.PROVIDERS_PER_PASS,
                    "provider work remains bounded and progresses each pass");
            visited.addAll(pass);
        }
        check(
                visited.equals(all),
                "bounded scanning reaches every provider without starvation or duplicates");
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
                GrowthBatchBridge.readSingletonLong(singleton, field) == 4,
                "optional scaling reads the current config instance");
        ReloadableConfig.INSTANCE = new ReloadableConfig(32);
        check(
                GrowthBatchBridge.readSingletonLong(singleton, field) == 32,
                "cached reflection preserves replacement-based config reload");
        var longSingleton = LongConfig.class.getField("INSTANCE");
        check(
                GrowthBatchBridge.readSingletonLong(
                                longSingleton, LongConfig.class.getField("limit"))
                        == Long.MAX_VALUE,
                "long global scaling limit is not narrowed or rejected by integer field reflection");
        check(
                GrowthBatchBridge.readSingletonLong(
                                longSingleton, LongConfig.class.getField("boxed"))
                        == 4_294_967_296L,
                "boxed long config retains values beyond the unsigned-int boundary");
        for (long value :
                new long[] {
                    -1,
                    0,
                    1,
                    Integer.MAX_VALUE,
                    (long) Integer.MAX_VALUE + 1,
                    4_294_967_296L,
                    Long.MAX_VALUE
                })
            check(
                    GrowthBatchBridge.readMultiplierLimit(value) == value,
                    "per-pattern long limits preserve exact values including unlimited sentinels");
        check(
                GrowthBatchBridge.readMultiplierLimit(Integer.MAX_VALUE) == Integer.MAX_VALUE,
                "legacy int multiplier API stays compatible");
        boolean invalidLimit = false;
        try {
            GrowthBatchBridge.readMultiplierLimit(1.5d);
        } catch (IllegalArgumentException expected) {
            invalidLimit = true;
        }
        check(
                invalidLimit,
                "changed non-integral optional contract triggers fallback instead of truncation");
        var nodeA =
                new io.github.sexileman.eae2pattern.client.CyclePathExport.Node(
                        "<script>alert(1)</script>", "test:a", "item", "{name:\"<unsafe>\"}");
        var nodeB =
                new io.github.sexileman.eae2pattern.client.CyclePathExport.Node(
                        "Fluid B", "test:b", "fluid", "");
        var route =
                new io.github.sexileman.eae2pattern.client.CyclePathExport.Route(
                        "Path 1", List.of(nodeA, nodeB, nodeA));
        var export =
                new io.github.sexileman.eae2pattern.client.CyclePathExport.Document(
                        1,
                        "current_displayed_paths",
                        "2026-11-14",
                        "Cycles",
                        "Current snapshot",
                        "A x 10",
                        "Detected",
                        List.of(route),
                        List.of(
                                new io.github.sexileman.eae2pattern.client.CyclePathExport.Recipe(
                                        1,
                                        List.of(
                                                new io.github.sexileman.eae2pattern.client
                                                        .CyclePathExport.Amount(
                                                        nodeB,
                                                        "9223372036854775807",
                                                        "mB",
                                                        "9,223,372,036,854,775.807 B")),
                                        List.of(
                                                new io.github.sexileman.eae2pattern.client
                                                        .CyclePathExport.Amount(
                                                        nodeA, "64", "item", "64"),
                                                new io.github.sexileman.eae2pattern.client
                                                        .CyclePathExport.Amount(
                                                        nodeB, "144", "mB", "0.144 B")))),
                        "Recipe not captured");
        String html = io.github.sexileman.eae2pattern.client.CyclePathExport.html(export);
        check(
                !html.contains("<script>")
                        && html.contains("&lt;script&gt;")
                        && html.contains("&lt;unsafe&gt;"),
                "exported names and NBT cannot inject HTML");
        var json =
                com.google.gson.JsonParser.parseString(
                                io.github.sexileman.eae2pattern.client.CyclePathExport.json(export))
                        .getAsJsonObject();
        var exportedRecipe = json.getAsJsonArray("recipes").get(0).getAsJsonObject();
        check(
                exportedRecipe
                        .getAsJsonArray("inputs")
                        .get(0)
                        .getAsJsonObject()
                        .get("rawAmount")
                        .getAsString()
                        .equals("9223372036854775807"),
                "exported recipe quantities retain exact long precision as strings");
        check(
                exportedRecipe.getAsJsonArray("outputs").size() == 2,
                "export includes all declared byproducts instead of only the main output");
        check(
                html.contains("id=\"recipe-1\"") && html.contains("144 mB"),
                "HTML includes linked full recipes and exact fluid units");
        var exportedNodes =
                json.getAsJsonArray("paths").get(0).getAsJsonObject().getAsJsonArray("nodes");
        check(
                exportedNodes.size() == 3
                        && exportedNodes
                                .get(2)
                                .getAsJsonObject()
                                .get("id")
                                .getAsString()
                                .equals("test:a"),
                "JSON preserves exact path order and the repeated closing node");
        check(
                exportedNodes.get(1).getAsJsonObject().get("kind").getAsString().equals("fluid"),
                "export distinguishes fluid and item nodes");
        check(
                io.github.sexileman.eae2pattern.client.CyclePathExport.capture(
                                CycleDiagramSnapshot.state(CycleDiagramSnapshot.State.NONE))
                        .paths()
                        .isEmpty(),
                "empty display exports no invented network paths");
        var exportRoot = java.nio.file.Files.createTempDirectory("eae2-cycle-export-test-");
        var firstExport =
                io.github.sexileman.eae2pattern.client.CyclePathExport.writeFiles(
                        export, exportRoot);
        var secondExport =
                io.github.sexileman.eae2pattern.client.CyclePathExport.writeFiles(
                        export, exportRoot);
        check(
                !firstExport.equals(secondExport),
                "repeated exports create independent reports without overwriting");
        check(
                java.nio.file.Files.readString(
                                firstExport.resolve("paths.html"),
                                java.nio.charset.StandardCharsets.UTF_8)
                        .equals(html),
                "HTML report is written completely as UTF-8");
        check(
                com.google.gson.JsonParser.parseString(
                                java.nio.file.Files.readString(firstExport.resolve("paths.json")))
                        .equals(json),
                "JSON file matches the same exported snapshot as HTML");
        for (var directory : List.of(firstExport, secondExport)) {
            java.nio.file.Files.delete(directory.resolve("paths.html"));
            java.nio.file.Files.delete(directory.resolve("paths.json"));
            java.nio.file.Files.delete(directory);
        }
        java.nio.file.Files.delete(exportRoot);
        var bounds = io.github.sexileman.eae2pattern.client.CyclePanelLayout.fit(600, 400);
        check(
                bounds.width() == 350
                        && bounds.height() == 250
                        && bounds.x() == 125
                        && bounds.y() == 75,
                "cycle panel matches the installed AE2CT native logical size");
        var smallBounds = io.github.sexileman.eae2pattern.client.CyclePanelLayout.fit(320, 240);
        check(
                smallBounds.width() == 280
                        && smallBounds.height() == 200
                        && smallBounds.x() == 20
                        && smallBounds.y() == 20,
                "small GUI viewports retain margins instead of covering the entire screen");
        check(
                !CyclePlanViewModel.tabs(
                                CycleDiagramSnapshot.state(CycleDiagramSnapshot.State.DETECTED))
                        .contains(CyclePlanViewModel.Tab.PHASES),
                "candidate-only plans do not show an empty execution-order tab");
        check(
                CyclePlanViewModel.tabs(snapshot).contains(CyclePlanViewModel.Tab.PHASES),
                "executable plans retain their phase inspection view");

        // Screenshot-sized fluid request: 1,000,000 B = 1,000,000,000 mB.
        var hugePlan = TwoRecipeGrowth.plan(10, 1, 64, 10000, 1440, 48, 1_000_001_440L, 48);
        check(
                hugePlan.operations() > 1_000_000
                        && hugePlan.stages().size() < CycleLimits.MAX_STAGES,
                "large cycle material analysis survives the execution-operation cap without expanding per-item jobs");
        var hugeCounts = GrowthDemand.analyzeCounts(hugePlan.stages(), 2);
        var hugeBudget =
                GrowthMaterials.budget(
                        java.util.List.of(
                                java.util.Map.of("A", 10L),
                                java.util.Map.of(
                                        "B", 64L, "dirt", 64L, "blue", 1000L, "green", 1000L)),
                        hugeCounts,
                        java.util.Set.of("A", "B"),
                        java.util.Map.of("A", 1440L, "B", 48L));
        var hugeNeeds =
                GrowthMaterialNeeds.evaluate(
                        hugeBudget,
                        java.util.Set.of("A", "B"),
                        key ->
                                switch (key) {
                                    case "A" -> 1440L;
                                    case "B" -> 48L;
                                    case "dirt" -> 6_400_000L;
                                    default -> 100_000_000L;
                                });
        check(
                hugeNeeds.stream().anyMatch(n -> n.key().equals("dirt") && n.missing() > 0),
                "large plan reports missing dirt instead of only recursive B");
        check(
                hugeNeeds.stream()
                        .filter(n -> n.key().equals("blue") || n.key().equals("green"))
                        .allMatch(n -> n.missing() > 0),
                "large plan reports both external fluid shortages in raw mB");
        boolean executionRejected = false;
        try {
            GrowthDemand.counts(hugePlan.stages(), 2);
        } catch (IllegalArgumentException expected) {
            executionRejected = true;
        }
        check(!executionRejected, "large executable plans retain exact aggregate counts");
        var largeConsumer =
                GrowthDemand.analyzeConsumer(2_000_000, 1, 1, 0, 1, 0, 1, 0, 1, 2, 1, 2);
        check(
                largeConsumer.times() == 2_000_000,
                "downstream material analysis has its own large-quantity path");
        check(
                GrowthDemand.appendAnalyzedConsumer(hugePlan, largeConsumer.times())
                                .get(hugePlan.stages().size())
                                .recipe()
                        == 2,
                "large downstream stages can be budgeted without enabling execution");
        boolean consumerRejected = false;
        try {
            GrowthDemand.consumer(2_000_000, 1, 1, 0, 1, 0, 1, 0, 1, 2, 1, 2);
        } catch (IllegalArgumentException expected) {
            consumerRejected = true;
        }
        check(!consumerRejected, "large downstream demand is executable without an operation cap");
        check(
                GrowthBatchSizing.affordable(
                                6_500_000L, java.util.Map.of("ore", 2L), key -> 10_000_000L)
                        == 5_000_000L,
                "bulk dispatch above one million is bounded by actual ingredient stock");
        var largeLedger = new GrowthLedger<String>(hugePlan.stages());
        check(
                largeLedger.remainingOperations(0) == hugeCounts[0],
                "large executable ledger preserves full recipe demand without per-operation expansion");
        return checks;
    }
}
