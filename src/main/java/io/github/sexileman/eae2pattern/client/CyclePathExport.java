package io.github.sexileman.eae2pattern.client;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;

import com.google.gson.GsonBuilder;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.cycle.CycleDiagramSnapshot;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.BiConsumer;

/** Export only the displayed path rows. Never requests recipes or touches a live ME network. */
public final class CyclePathExport {
    public record Node(String name, String id, String kind, String nbt, List<Integer> recipeRefs) {
        public Node {
            recipeRefs = List.copyOf(recipeRefs);
        }

        public Node(String name, String id, String kind, String nbt) {
            this(name, id, kind, nbt, List.of());
        }
    }

    public record Amount(Node ingredient, String rawAmount, String unit, String displayAmount) {}

    public record Recipe(int id, List<Amount> inputs, List<Amount> outputs) {
        public Recipe {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
        }
    }

    public record Route(String title, List<Node> nodes) {
        public Route {
            nodes = List.copyOf(nodes);
        }
    }

    public record Document(
            int schema,
            String scope,
            String exportedAt,
            String title,
            String notice,
            String target,
            String state,
            List<Route> paths,
            List<Recipe> recipes,
            String unavailableRecipe) {
        public Document {
            paths = List.copyOf(paths);
            recipes = List.copyOf(recipes);
        }
    }

    private CyclePathExport() {}

    private static Node node(GenericStack stack) {
        var key = stack.what();
        var tag =
                key instanceof AEItemKey item
                        ? item.getTag()
                        : key instanceof AEFluidKey fluid ? fluid.getTag() : null;
        return new Node(
                key.getDisplayName().getString(),
                key.getId().toString(),
                key instanceof AEFluidKey ? "fluid" : "item",
                tag == null ? "" : tag.toString());
    }

    private static Amount amount(GenericStack stack) {
        return new Amount(
                node(stack),
                Long.toString(stack.amount()),
                stack.what() instanceof AEFluidKey ? "mB" : "item",
                stack.what().formatAmount(stack.amount(), AmountFormat.FULL));
    }

    private static Node pathNode(GenericStack stack, CycleDiagramSnapshot snapshot) {
        var value = node(stack);
        var references = new java.util.ArrayList<Integer>();
        for (int i = 0; i < snapshot.recipes().size(); i++)
            if (snapshot.recipes().get(i).outputs().stream()
                    .anyMatch(output -> output.what().equals(stack.what()))) references.add(i + 1);
        return new Node(value.name(), value.id(), value.kind(), value.nbt(), references);
    }

    public static Document capture(CycleDiagramSnapshot snapshot) {
        var paths =
                CyclePlanViewModel.rows(snapshot, CyclePlanViewModel.Tab.PATHS).stream()
                        .filter(row -> !row.grid() && !row.inputs().isEmpty())
                        .map(
                                row ->
                                        new Route(
                                                row.title().getString(),
                                                row.inputs().stream()
                                                        .map(stack -> pathNode(stack, snapshot))
                                                        .toList()))
                        .toList();
        var target = snapshot.target();
        var recipes = new java.util.ArrayList<Recipe>();
        for (int i = 0; i < snapshot.recipes().size(); i++) {
            var recipe = snapshot.recipes().get(i);
            recipes.add(
                    new Recipe(
                            i + 1,
                            recipe.inputs().stream().map(CyclePathExport::amount).toList(),
                            recipe.outputs().stream().map(CyclePathExport::amount).toList()));
        }
        return new Document(
                2,
                "current_displayed_paths",
                OffsetDateTime.now().toString(),
                Component.translatable("eae2_pattern.diagram.title").getString(),
                Component.translatable("eae2_pattern.diagram.export_notice").getString(),
                target == null
                        ? ""
                        : target.what().getDisplayName().getString()
                                + " × "
                                + target.what().formatAmount(target.amount(), AmountFormat.FULL),
                Component.translatable("eae2_pattern.diagram.status." + snapshot.state().id())
                        .getString(),
                paths,
                recipes,
                Component.translatable("eae2_pattern.diagram.export_recipe_unavailable")
                        .getString());
    }

    public static String json(Document document) {
        return new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create()
                .toJson(document);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static String html(Document document) {
        var out =
                new StringBuilder(
                        "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><title>");
        out.append(escape(document.title()))
                .append(
                        "</title><style>body{font:15px system-ui,sans-serif;background:#eee;color:#282830;max-width:1200px;margin:32px auto;padding:0 20px}header p{color:#666}section{background:white;border:1px solid #ccc;border-radius:6px;padding:18px;margin:18px 0}h1{font-size:23px}h2{font-size:17px}.route{display:flex;align-items:center;flex-wrap:wrap;gap:10px}.node{border:1px solid #bcb6c8;padding:12px;border-radius:4px;max-width:260px;overflow-wrap:anywhere}.number{color:#72568c;margin-right:8px}.id,summary{font-size:12px;color:#666}.arrow{color:#72568c}pre{white-space:pre-wrap;overflow-wrap:anywhere}footer{color:#777;font-size:12px}</style></head><body><header><h1>")
                .append(escape(document.title()))
                .append("</h1><p>")
                .append(escape(document.target()))
                .append("</p><p>")
                .append(escape(document.state()))
                .append("</p><p>")
                .append(escape(document.notice()))
                .append("</p></header>");
        for (var path : document.paths()) {
            out.append("<section><h2>")
                    .append(escape(path.title()))
                    .append("</h2><div class=\"route\">");
            for (int i = 0; i < path.nodes().size(); i++) {
                var node = path.nodes().get(i);
                if (i > 0) out.append("<span class=\"arrow\">→</span>");
                out.append("<div class=\"node\"><span class=\"number\">")
                        .append(i + 1)
                        .append(".</span>")
                        .append(escape(node.name()))
                        .append("<div class=\"id\">")
                        .append(escape(node.id()))
                        .append("</div>");
                if (!node.nbt().isEmpty())
                    out.append("<details><summary>NBT</summary><pre>")
                            .append(escape(node.nbt()))
                            .append("</pre></details>");
                if (node.recipeRefs().isEmpty())
                    out.append("<div class=\"id\">")
                            .append(escape(document.unavailableRecipe()))
                            .append("</div>");
                for (int reference : node.recipeRefs())
                    out.append(" <a href=\"#recipe-")
                            .append(reference)
                            .append("\">R")
                            .append(reference)
                            .append("</a>");
                out.append("</div>");
            }
            out.append("</div></section>");
        }
        for (var recipe : document.recipes()) {
            out.append("<section id=\"recipe-")
                    .append(recipe.id())
                    .append("\"><h2>R")
                    .append(recipe.id())
                    .append(" · ");
            out.append(
                    escape(
                            recipe.outputs().stream()
                                    .map(value -> value.ingredient().name())
                                    .distinct()
                                    .collect(java.util.stream.Collectors.joining(" + "))));
            out.append("</h2><div class=\"route\"><div>");
            appendAmounts(out, recipe.inputs());
            out.append("</div><span class=\"arrow\">→</span><div>");
            appendAmounts(out, recipe.outputs());
            out.append("</div></div></section>");
        }
        return out.append("<footer>")
                .append(escape(document.exportedAt()))
                .append("</footer></body></html>")
                .toString();
    }

    private static void appendAmounts(StringBuilder out, List<Amount> amounts) {
        out.append("<ul>");
        for (var value : amounts) {
            out.append("<li>")
                    .append(escape(value.displayAmount()))
                    .append(" × ")
                    .append(escape(value.ingredient().name()))
                    .append("<div class=\"id\">")
                    .append(escape(value.ingredient().id()))
                    .append(" · ")
                    .append(escape(value.rawAmount()))
                    .append(" ")
                    .append(escape(value.unit()))
                    .append("</div>");
            if (!value.ingredient().nbt().isEmpty())
                out.append("<details><summary>NBT</summary><pre>")
                        .append(escape(value.ingredient().nbt()))
                        .append("</pre></details>");
            out.append("</li>");
        }
        out.append("</ul>");
    }

    public static Path writeFiles(Document document, Path root) throws java.io.IOException {
        Files.createDirectories(root);
        var directory =
                Files.createTempDirectory(root, "cycle-paths-" + java.time.LocalDate.now() + "-");
        Files.writeString(directory.resolve("paths.json"), json(document), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("paths.html"), html(document), StandardCharsets.UTF_8);
        return directory;
    }

    public static void write(Document document, BiConsumer<Path, Throwable> completed) {
        var mc = Minecraft.getInstance();
        Path root = mc.gameDirectory.toPath().resolve("eae2-pattern").resolve("exports");
        Util.ioPool()
                .execute(
                        () -> {
                            Path directory = null;
                            Throwable failure = null;
                            try {
                                directory = writeFiles(document, root);
                            } catch (Exception error) {
                                failure = error;
                                EAE2Pattern.LOGGER.error(
                                        "Failed to export displayed cycle paths", error);
                            }
                            Path result = directory;
                            Throwable error = failure;
                            mc.execute(() -> completed.accept(result, error));
                        });
    }
}
