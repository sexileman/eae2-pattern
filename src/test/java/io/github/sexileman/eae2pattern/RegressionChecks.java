package io.github.sexileman.eae2pattern;

import io.github.sexileman.eae2pattern.client.Ingredient;
import io.github.sexileman.eae2pattern.client.ItemSearchQuery;
import io.github.sexileman.eae2pattern.client.RecipePlan;
import io.github.sexileman.eae2pattern.client.RecipeFilters;
import io.github.sexileman.eae2pattern.client.ProcessingMultiplier;
import io.github.sexileman.eae2pattern.client.RecipeGroups;
import io.github.sexileman.eae2pattern.client.RecipeDiagnostics;
import io.github.sexileman.eae2pattern.client.IngredientPresentation;
import io.github.sexileman.eae2pattern.client.DetailRowLayout;
import io.github.sexileman.eae2pattern.net.S2CInspectionPacket;
import io.github.sexileman.eae2pattern.net.C2SGeneratePatternsPacket;
import io.github.sexileman.eae2pattern.compat.jei.GtChanceReader;
import io.github.sexileman.eae2pattern.pattern.*;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/** Boundary and semantic regressions; run with gradlew regressionTest. */
public final class RegressionChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        policies(); wireFormat(); searches(); gtVersions(); layoutAndGrid(); manualMultiplier(); grouping(); inspectionPackets(); slotPresentation();
        System.out.println("Passed " + checks + " regression checks");
    }
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static StackSpec stack(String id, long amount) { return StackSpec.of(StackType.ITEM, new ResourceLocation(id), amount); }
    private static RecipePlan.Entry entry(List<Ingredient> inputs, List<Ingredient> tools, List<Ingredient> outputs) {
        // The icon is deliberately absent: these domain tests do not start Forge or initialize item registries.
        return new RecipePlan.Entry(PatternKind.PROCESSING, new ResourceLocation("test:recipe"), Component.literal("test"), null, inputs, tools, outputs, true);
    }
    private static void policies() {
        CompoundTag tag = new CompoundTag(); tag.putInt("Configuration", 24);
        StackSpec circuit = new StackSpec(StackType.ITEM, new ResourceLocation("gtceu:programmed_circuit"), tag, 1);
        StackSpec ingot = stack("test:ingot", 2), plate = stack("test:plate", 1);
        RecipePlan.Entry e = entry(List.of(Ingredient.consumed(ingot), Ingredient.notConsumed(circuit)),
                List.of(Ingredient.notConsumed(stack("test:mold", 1))),
                List.of(Ingredient.produced(stack("test:dust", 1), .25f), Ingredient.produced(plate, 1)));
        check(e.mainOutputIndex() == 1, "first guaranteed output, even after chance output");
        check(e.hasProbabilisticByproduct(), "chance output before main is a byproduct");
        PatternSpec filtered = e.specForPolicy(8, 1, true);
        check(filtered.inputs().size() == 1 && filtered.inputs().get(0).amount() == 16, "exclude tools and multiply consumed inputs");
        check(filtered.outputs().size() == 1 && filtered.outputs().get(0).amount() == 8, "guaranteed output policy");
        PatternSpec kept = e.specForPolicy(8, 0, false);
        check(kept.inputs().size() == 3, "include separate catalyst slots");
        check(kept.inputs().get(1).amount() == 1 && kept.inputs().get(2).amount() == 1, "do not multiply non-consumed inputs");
        check(kept.inputs().get(1).tag().getInt("Configuration") == 24, "configured circuit NBT survives policy");
        RecipePlan.Entry allChance = entry(List.of(Ingredient.consumed(ingot)), List.of(), List.of(Ingredient.produced(plate, .1f)));
        check(allChance.allOutputsProbabilistic() && allChance.hasProbabilisticByproduct(), "all outputs are chance byproducts");
        check(allChance.specForPolicy(1, 1, true) == null && allChance.specForPolicy(1, 2, true) == null, "empty-output policies skip recipe");
        check(allChance.specForPolicy(1, 0, true) != null, "retain all-chance recipes with all-output policy");
        RecipePlan.Entry onlyTool = entry(List.of(Ingredient.notConsumed(circuit)), List.of(), List.of(Ingredient.produced(plate, 1)));
        check(!onlyTool.canEncode(0, true), "skip empty-input processing pattern");
        check(e.isSelected(), "policy changes preserve selection");
        check(new RecipeFilters(ItemSearchQuery.empty(), ItemSearchQuery.empty(), 3, 0, false).rejects(e), "regular-input filter excludes non-consumed tools");
        check(!new RecipeFilters(ItemSearchQuery.empty(), ItemSearchQuery.empty(), 0, 4, false).rejects(allChance), "all-chance output filter includes no-main recipes");
        check(new RecipeFilters(ItemSearchQuery.empty(), ItemSearchQuery.empty(), 0, 1, false).rejects(allChance), "guaranteed-only filter rejects all-chance recipe");
        RecipePlan.Entry crafting = new RecipePlan.Entry(PatternKind.CRAFTING, new ResourceLocation("test:crafting"), Component.literal("crafting"), null,
                List.of(Ingredient.consumed(ingot)), List.of(), List.of(Ingredient.produced(plate, 1)), true);
        check(crafting.specForPolicy(64, 0, true).outputs().get(0).amount() == 1, "processing multiplier leaves crafting unchanged");
        RecipePlan.Entry manyInputs = entry(java.util.Collections.nCopies(82, Ingredient.consumed(ingot)), List.of(), List.of(Ingredient.produced(plate, 1)));
        check(!manyInputs.canEncode(0, false), "combined input count respects AE2 limit");
        check(Ingredient.produced(plate, .9999f).probabilistic(), "99.99 percent is not guaranteed");
        check(Ingredient.produced(plate, .0001f).chanceText().equals("0.01%"), "preserve tiny GT probabilities");
        tag.putInt("Configuration", 0);
        check(circuit.tag().getInt("Configuration") == 24, "own the source NBT");
    }
    private static void wireFormat() {
        CompoundTag tag = new CompoundTag(); tag.putString("large", "x".repeat(6000));
        PatternSpec spec = PatternSpec.processing(new ResourceLocation("test:wire"),
                List.of(new StackSpec(StackType.ITEM, new ResourceLocation("gtceu:circuit"), tag, 1)), List.of(stack("test:plate", 2)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            spec.write(buf); check(buf.readableBytes() > 6000, "wire size includes NBT");
            check(PatternSpec.read(buf).equals(spec), "NBT / amount packet round trip");
            buf.clear(); buf.writeByte(1); buf.writeResourceLocation(new ResourceLocation("test:bad")); buf.writeVarInt(Integer.MAX_VALUE);
            boolean rejected = false;
            try { PatternSpec.read(buf); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "reject oversized slot count before allocating");
        } finally { buf.release(); }
    }
    private static void searches() throws Exception {
        Constructor<RecipePlan.Haystack> ctor = RecipePlan.Haystack.class.getDeclaredConstructor(List.class, List.class, List.class, List.class);
        ctor.setAccessible(true);
        var haystack = ctor.newInstance(List.of(), List.of("Double Iron Plate", "pipe|connector"), List.of("gtceu:iron_plate"), List.of("Greg Tech", "gtceu"));
        Method match = Class.forName(ItemSearchQuery.class.getName() + "$Alternative").getDeclaredMethod("matches", RecipePlan.Haystack.class);
        match.setAccessible(true);
        var alternatives = ItemSearchQuery.class.getDeclaredField("alternatives"); alternatives.setAccessible(true);
        for (String query : List.of("iron plate", "@\"Greg Tech\"", "&gtceu:iron_plate", "copper|iron", "-copper iron", "\"pipe|connector\"", "*Iron*")) {
            boolean found = false;
            for (Object branch : (List<?>) alternatives.get(ItemSearchQuery.parse(query))) if ((boolean) match.invoke(branch, haystack)) found = true;
            check(found, "search should match: " + query);
        }
        boolean found = false;
        for (Object branch : (List<?>) alternatives.get(ItemSearchQuery.parse("iron -plate"))) if ((boolean) match.invoke(branch, haystack)) found = true;
        check(!found, "excluded term vetoes an alternative");
    }
    public static final class OldContent { public int chance = 2500, maxChance = 10000; }
    public record NewContent(int chance, int maxChance) {}
    private static void layoutAndGrid() {
        for (int[] size : List.of(new int[]{320, 180}, new int[]{480, 241}, new int[]{480, 242}, new int[]{480, 270},
                new int[]{480, 271}, new int[]{480, 272}, new int[]{640, 331}, new int[]{640, 332}, new int[]{640, 360}, new int[]{1920, 1080})) {
            var l = io.github.sexileman.eae2pattern.client.WorkbenchLayout.measure(size[0], size[1]);
            check(l.bodyBottom() - l.bodyTop() >= 38, "at least one full multi-icon row at " + size[0] + "x" + size[1]);
            check(l.top() + l.toolbarY() + 19 < l.bodyTop(), "toolbar does not overlap list");
            check(l.footerTop() - l.bodyBottom() >= 5, "generation section has an explicit gap below the list");
            check(l.footerControlsY() >= l.footerTop() && l.footerControlsY() + 19 <= l.footerTop() + l.footerHeight(), "generation controls stay inside footer card");
            check(l.footerTop() + l.footerHeight() < l.statsY() && l.statsY() + 9 < l.top() + l.height(), "footer card and status summary do not overlap");
            check(l.left() >= 0 && l.left() + l.width() <= size[0], "panel stays on screen");
        }
        check(CraftingGrid.slot(2, 2) == 3 && CraftingGrid.slot(3, 2) == 4, "2x2 recipe row stride");
        check(CraftingGrid.slot(1, 1) == 3 && CraftingGrid.slot(2, 1) == 6, "1x3 recipe row stride");
        check(CraftingGrid.slot(8, 3) == 8, "full 3x3 grid");
        for (int count = 1; count <= 8; count++) {
            var popup = io.github.sexileman.eae2pattern.client.PopupLayout.measure(280, 150, 40, 19, 180, count, 320, 180);
            check(popup.x() >= 6 && popup.x() + popup.width() <= 314 && popup.y() >= 6 && popup.y() + popup.height() <= 174,
                    "popup stays inside minimum GUI canvas with " + count + " choices");
            check(popup.indexAt(popup.x() + 5, popup.y() + popup.height() - 4) == count - 1, "popup last row hit test");
            check(popup.indexAt(popup.x() - 1, popup.y() + 5) == -1 && popup.indexAt(popup.x() + 5, popup.y()) == -1, "popup border and outside click do not select");
        }
    }
    private static void gtVersions() throws Exception {
        Method chance = GtChanceReader.class.getDeclaredMethod("chanceOf", Object.class); chance.setAccessible(true);
        check((float) chance.invoke(null, new OldContent()) == .25f, "GT legacy public fields");
        check((float) chance.invoke(null, new NewContent(2500, 10000)) == .25f, "GT record accessors");
        check((float) chance.invoke(null, new NewContent(0, 10000)) == 0, "GT non-consumed chance");
    }

    private static void manualMultiplier() {
        check(ProcessingMultiplier.parse("1") == ProcessingMultiplier.DEFAULT, "default multiplier is one");
        check(ProcessingMultiplier.parse("100") == 100, "manual non-preset multiplier");
        check(ProcessingMultiplier.parse("65536") == ProcessingMultiplier.MAX, "maximum manual multiplier");
        for (String invalid : List.of("", "0", "65537", "9999999999999", "-1", "1.5", "x", " 2", "２"))
            check(ProcessingMultiplier.parse(invalid) == 0, "invalid multiplier disables generation: " + invalid);
        check(ProcessingMultiplier.acceptsText(""), "allow clearing field while editing");
        check(!ProcessingMultiplier.acceptsText("-1") && !ProcessingMultiplier.acceptsText("100000"), "field rejects non-numeric or overlong text");
        RecipePlan.Entry e = entry(List.of(Ingredient.consumed(stack("test:input", 2))), List.of(), List.of(Ingredient.produced(stack("test:output", 3), 1)));
        PatternSpec scaled = e.specForPolicy(ProcessingMultiplier.parse("100"), 0, true);
        check(scaled.inputs().get(0).amount() == 200 && scaled.outputs().get(0).amount() == 300, "manual multiplier reaches generation specification");
    }

    private static void grouping() throws Exception {
        RecipePlan.Entry a = entry(List.of(Ingredient.consumed(stack("test:ingot", 1))), List.of(), List.of(Ingredient.produced(stack("test:plate", 1), 1)));
        RecipePlan.Entry b = entry(List.of(Ingredient.consumed(stack("test:block", 1))), List.of(), List.of(Ingredient.produced(stack("test:plate", 9), 1)));
        check(RecipeGroups.keys(a, 1).get(0).id().equals(RecipeGroups.keys(b, 1).get(0).id()), "same-output grouping ignores route and amount");
        var ctor = RecipePlan.Haystack.class.getDeclaredConstructor(List.class, List.class, List.class, List.class); ctor.setAccessible(true);
        var tags = RecipePlan.Haystack.class.getDeclaredField("tags"); tags.setAccessible(true);
        var outputs = RecipePlan.Entry.class.getDeclaredField("outputHaystack"); outputs.setAccessible(true);
        var inputs = RecipePlan.Entry.class.getDeclaredField("inputHaystack"); inputs.setAccessible(true);
        var outA = ctor.newInstance(List.of(), List.of(), List.of(), List.of()); tags.set(outA, List.of("forge:plates", "forge:plates/iron", "forge:plates")); outputs.set(a, outA);
        var outB = ctor.newInstance(List.of(), List.of(), List.of(), List.of()); tags.set(outB, List.of("forge:plates")); outputs.set(b, outB);
        var inA = ctor.newInstance(List.of(), List.of(), List.of(), List.of()); tags.set(inA, List.of("forge:ingots")); inputs.set(a, inA);
        check(RecipeGroups.keys(a, 2).size() == 2, "preserve all distinct output tags");
        check(RecipeGroups.keys(a, 3).get(0).id().equals("tag:forge:ingots"), "input tag grouping uses the input side");
        RecipeGroups.Builder builder = new RecipeGroups.Builder(List.of(a, b), 2);
        while (!builder.step()) { }
        var groups = builder.finish();
        check(groups.size() == 2, "one material may appear in multiple tag groups");
        var plateGroup = groups.stream().filter(g -> g.key().id().equals("tag:forge:plates")).findFirst().orElseThrow();
        check(plateGroup.entries().size() == 2, "tag group contains all matching routes without duplicate membership");
        var rows = RecipeGroups.rows(List.of(a, b), groups, java.util.Set.of("tag:forge:plates", "tag:forge:plates/iron"), 2);
        check(rows.stream().filter(r -> r.entry() == a).count() == 2, "recipe is visible under both matching tags");
        a.setSelected(false);
        check(rows.stream().filter(r -> r.entry() == a).noneMatch(r -> r.entry().isSelected()), "selection is shared across tags");
        RecipePlan plan = new RecipePlan(new ResourceLocation("test:machine"), Component.literal("machine"), PatternKind.PROCESSING, List.of(a, b));
        check(plan.selectedSpecs(1, false, true).size() == 1, "generation uses unique source entries, not repeated group rows");
        check(plan.selectedSpecs(List.of(a), 1, false, true).isEmpty() && plan.selectedCount() == 1, "hidden selected recipes are not generated by a filtered subset");
        check(RecipeGroups.rows(List.of(a, b), groups, java.util.Set.of(), 2).size() == 2, "collapsed groups retain headers only");
        check(RecipeGroups.rows(List.of(a, b), groups, java.util.Set.of(), 0).size() == 2, "ungrouped view shows every source recipe once");
        var empty = ctor.newInstance(List.of(), List.of(), List.of(), List.of()); tags.set(empty, List.of()); outputs.set(b, empty);
        check(RecipeGroups.keys(b, 2).get(0).id().equals("untagged"), "untagged recipes are retained");
        RecipeGroups.Builder sliced = new RecipeGroups.Builder(java.util.Collections.nCopies(3000, b), 1);
        int steps = 1; while (!sliced.step()) steps++;
        check(steps >= 3 && sliced.finish().get(0).entries().size() == 3000, "group construction is time-sliced");
        CompoundTag c24 = new CompoundTag(); c24.putInt("Configuration", 24);
        CompoundTag c0 = new CompoundTag(); c0.putInt("Configuration", 0);
        StackSpec first = new StackSpec(StackType.ITEM, new ResourceLocation("test:circuit"), c24, 1);
        StackSpec second = new StackSpec(StackType.ITEM, new ResourceLocation("test:circuit"), c0, 1);
        check(!RecipeDiagnostics.sameMaterial(first, second), "click filtering distinguishes circuit NBT");
        var circuitA = entry(List.of(Ingredient.consumed(stack("test:in", 1))), List.of(), List.of(Ingredient.produced(first, 1)));
        var circuitB = entry(List.of(Ingredient.consumed(stack("test:in", 1))), List.of(), List.of(Ingredient.produced(second, 1)));
        check(!RecipeGroups.keys(circuitA, 1).get(0).id().equals(RecipeGroups.keys(circuitB, 1).get(0).id()), "same-output groups distinguish NBT");
        check(RecipeDiagnostics.sameMaterial(first, first.withAmount(64)), "click filtering ignores display count");
        RecipePlan.Entry chance = entry(List.of(Ingredient.consumed(stack("test:in", 1))), List.of(), List.of(Ingredient.produced(stack("test:out", 1), .5f)));
        check(RecipeDiagnostics.skipReason(chance, 1, true).equals("skip_no_guaranteed"), "explain all-chance output exclusion");
    }

    private static void inspectionPackets() {
        PatternSpec spec = PatternSpec.processing(new ResourceLocation("test:preview"), List.of(stack("test:in", 8)), List.of(stack("test:out", 16)));
        S2CInspectionPacket original = new S2CInspectionPacket(7, 2, true, 1, 0, List.of(), List.of(spec), List.of("preview"));
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.encode(b); check(S2CInspectionPacket.decode(b).equals(original), "canonical server preview packet round trip");
            b.clear(); new C2SGeneratePatternsPacket(8, 0, 1, List.of(spec), 1).encode(b);
            check(b.readUnsignedByte() == 1 && b.readVarInt() == 8, "read-only inspection operation is explicit on the wire");
            check(b.readVarInt() == 0 && b.readVarInt() == 1 && b.readVarInt() == 1 && PatternSpec.read(b).equals(spec), "inspection request preserves the full pattern specification");
        } finally { b.release(); }
    }

    private static void slotPresentation() {
        StackSpec material = stack("test:tool", 1);
        var nc = IngredientPresentation.of(Ingredient.notConsumed(material), 0);
        check(nc.kind() == IngredientPresentation.Kind.NOT_CONSUMED && nc.chance().isEmpty(), "non-consumed slot is not described as 0% consumption");
        check(nc.badge().equals("∞"), "non-consumed slot has a compact visual badge");
        var chanceInput = IngredientPresentation.of(Ingredient.consumed(material, .25f), 0);
        check(chanceInput.kind() == IngredientPresentation.Kind.CHANCE_INPUT && chanceInput.chance().equals("25%"), "chance input tooltip preserves exact chance");
        var chanceOutput = IngredientPresentation.of(Ingredient.produced(material, .125f), 1);
        check(chanceOutput.kind() == IngredientPresentation.Kind.CHANCE_OUTPUT && chanceOutput.chance().equals("12.5%"), "output probability uses production semantics");
        check(IngredientPresentation.of(Ingredient.produced(material, 0), 1).kind() == IngredientPresentation.Kind.CHANCE_OUTPUT, "zero-chance output is not a non-consumed input");
        check(IngredientPresentation.of(Ingredient.consumed(material), 0).equals(IngredientPresentation.REGULAR), "regular input does not get a probability badge");
        RecipePlan.Entry mixed = entry(List.of(Ingredient.notConsumed(material), Ingredient.consumed(material)), List.of(), List.of(Ingredient.produced(stack("test:out", 1), 1)));
        check(IngredientPresentation.encoded(mixed, material.withAmount(64), 0, true).equals(IngredientPresentation.REGULAR), "excluded tool cannot mislabel the remaining encoded material");
        check(IngredientPresentation.encoded(mixed, material.withAmount(65), 0, false).kind() == IngredientPresentation.Kind.MIXED, "merged consumption rules are identified instead of inventing a chance");
        RecipePlan.Entry tools = entry(List.of(), List.of(Ingredient.notConsumed(material)), List.of(Ingredient.produced(stack("test:out", 1), 1)));
        check(IngredientPresentation.encoded(tools, material, 0, false).kind() == IngredientPresentation.Kind.NOT_CONSUMED, "separate catalyst metadata survives encoded preview rendering");

        var rows = DetailRowLayout.of(List.of(false, true, false, true), Boolean::booleanValue);
        check(rows.height() == 96 && rows.top(1) == 18 && rows.top(2) == 48 && rows.top(3) == 66, "detail rows reserve full slot height without overlapping text");
        check(rows.indexAt(17.9) == 0 && rows.indexAt(18) == 1 && rows.indexAt(47.9) == 1 && rows.indexAt(48) == 2 && rows.indexAt(66) == 3, "detail tooltip hit tests follow the same offsets as rendering");
        check(rows.indexAt(-1) == -1 && rows.indexAt(96) == -1, "detail border and outside points do not select a row");
        var empty = DetailRowLayout.of(List.of(), ignored -> false);
        check(empty.height() == 0 && empty.indexAt(0) == -1, "empty details have no stale tooltip targets");
    }
}
