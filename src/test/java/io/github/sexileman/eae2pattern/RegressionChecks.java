package io.github.sexileman.eae2pattern;

import io.github.sexileman.eae2pattern.client.Ingredient;
import io.github.sexileman.eae2pattern.client.ItemSearchQuery;
import io.github.sexileman.eae2pattern.client.RecipePlan;
import io.github.sexileman.eae2pattern.client.RecipeFilters;
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
        policies(); wireFormat(); searches(); gtVersions(); layoutAndGrid();
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
        for (int[] size : List.of(new int[]{320, 180}, new int[]{480, 270}, new int[]{640, 360}, new int[]{1920, 1080})) {
            var l = io.github.sexileman.eae2pattern.client.WorkbenchLayout.measure(size[0], size[1]);
            check(l.bodyBottom() - l.bodyTop() >= 28, "at least one row at " + size[0]);
            check(l.top() + l.toolbarY() + 19 < l.bodyTop(), "toolbar does not overlap list");
            check(l.bodyBottom() < l.top() + l.height() - 45, "footer does not overlap list");
            check(l.left() >= 0 && l.left() + l.width() <= size[0], "panel stays on screen");
        }
        check(CraftingGrid.slot(2, 2) == 3 && CraftingGrid.slot(3, 2) == 4, "2x2 recipe row stride");
        check(CraftingGrid.slot(1, 1) == 3 && CraftingGrid.slot(2, 1) == 6, "1x3 recipe row stride");
        check(CraftingGrid.slot(8, 3) == 8, "full 3x3 grid");
    }
    private static void gtVersions() throws Exception {
        Method chance = GtChanceReader.class.getDeclaredMethod("chanceOf", Object.class); chance.setAccessible(true);
        check((float) chance.invoke(null, new OldContent()) == .25f, "GT legacy public fields");
        check((float) chance.invoke(null, new NewContent(2500, 10000)) == .25f, "GT record accessors");
        check((float) chance.invoke(null, new NewContent(0, 10000)) == 0, "GT non-consumed chance");
    }
}
