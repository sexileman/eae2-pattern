package io.github.sexileman.eae2pattern.compat.jei;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.client.RecipePlan;
import io.github.sexileman.eae2pattern.pattern.PatternKind;
import io.github.sexileman.eae2pattern.pattern.PatternSpec;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads "everything this machine can make" out of JEI.
 *
 * <p>The trick that makes this work for arbitrary modded machines is that we do not interpret their recipes at all:
 * we ask JEI to build the layout it would draw on its own recipe screen, then read the ingredient slots back out.
 * Whatever a mod taught JEI about its machine is exactly what we get, including custom slot logic it may have added.</p>
 */
public final class JeiRecipeCollector {

    private static final int MAX_RECIPES_PER_CATEGORY = 2048;

    private JeiRecipeCollector() {
    }

    @SuppressWarnings("unchecked")
    public static RecipePlan collect(IRecipeCategory<?> category) {
        Collector collector = begin(category);
        while (!collector.finished()) {
            collector.step();
        }
        return collector.finish();
    }

    /**
     * Starts a collection that the caller drives. Building a JEI layout for every recipe of a category is far too
     * expensive to do in one client tick (a crafting table category can hold thousands of recipes), so the caller is
     * expected to advance this a little at a time.
     */
    @SuppressWarnings("unchecked")
    public static Collector begin(IRecipeCategory<?> category) {
        return new Collector((IRecipeCategory<Object>) category);
    }

    public static final class Collector {
        private final IRecipeCategory<Object> category;
        @Nullable
        private final IJeiRuntime runtime;
        private final RecipeType<Object> type;
        private final List<Object> recipes;
        private final List<RecipePlan.Entry> entries = new ArrayList<>();
        private final Set<ResourceLocation> usedIds = new HashSet<>();
        private final int[] kindCounts = new int[PatternKind.values().length];
        private int fallbackCounter;
        private int cursor;
        private boolean truncated;

        private Collector(IRecipeCategory<Object> category) {
            this.category = category;
            this.runtime = JeiRuntimeHolder.get();
            this.type = category.getRecipeType();
            if (this.runtime == null) {
                this.recipes = List.of();
            } else {
                // Deliberately NOT includeHidden(): those are recipes JEI keeps out of the list for this category,
                // so generating patterns for them would hand the player entries the machine never shows.
                // isHandled() then drops anything the category itself refuses to display, which matters when
                // several category instances share one RecipeType.
                this.recipes = this.runtime.getRecipeManager()
                        .createRecipeLookup(this.type)
                        .get()
                        .filter(category::isHandled)
                        .toList();
            }
        }

        public boolean finished() {
            return runtime == null || cursor >= recipes.size();
        }

        public int processed() {
            return cursor;
        }

        public int total() {
            return recipes.size();
        }

        /** Consumes exactly one recipe, so the caller can decide the per-tick budget. */
        public void step() {
            if (finished()) {
                return;
            }
            Object recipe = recipes.get(cursor++);
            if (entries.size() >= MAX_RECIPES_PER_CATEGORY) {
                if (!truncated) {
                    truncated = true;
                    EAE2Pattern.LOGGER.warn("Category {} has more than {} recipes, truncating the plan",
                            type.getUid(), MAX_RECIPES_PER_CATEGORY);
                }
                return;
            }
            Optional<IRecipeLayoutDrawable<Object>> layout = runtime.getRecipeManager()
                    .createRecipeLayoutDrawable(category, recipe, emptyFocus());
            if (layout.isEmpty()) {
                return;
            }
            IRecipeSlotsView slots = layout.get().getRecipeSlotsView();
            List<StackSpec> inputs = toSpecs(slots.getSlotViews(RecipeIngredientRole.INPUT));
            List<StackSpec> outputs = toSpecs(slots.getSlotViews(RecipeIngredientRole.OUTPUT));
            if (outputs.isEmpty()) {
                return;
            }
            ResourceLocation recipeId = uniqueId(category.getRegistryName(recipe), type, usedIds, fallbackCounter++);
            if (recipeId == null) {
                return;
            }
            PatternKind kind = kindOf(recipe);
            PatternSpec spec;
            try {
                spec = new PatternSpec(kind, recipeId, inputs, outputs);
            } catch (IllegalArgumentException e) {
                EAE2Pattern.LOGGER.debug("Skipping recipe {} of {}: {}", recipeId, type.getUid(), e.getMessage());
                return;
            }
            if (!spec.fits()) {
                return;
            }
            ItemStack icon = outputs.get(0).toItemStack();
            Component label = icon.isEmpty() ? Component.literal(recipeId.toString()) : icon.getHoverName();
            entries.add(new RecipePlan.Entry(spec, label, icon, true));
            kindCounts[kind.ordinal()]++;
        }

        private IFocusGroup emptyFocus() {
            return runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        }

        public RecipePlan finish() {
            EAE2Pattern.LOGGER.info("Collected {} of {} recipes from category '{}' ({})",
                    entries.size(), recipes.size(), category.getTitle().getString(), type.getUid());
            return new RecipePlan(type.getUid(), category.getTitle(), dominantKind(kindCounts), entries);
        }
    }

    @Nullable
    private static ResourceLocation uniqueId(@Nullable ResourceLocation id, RecipeType<?> type, Set<ResourceLocation> used, int fallback) {
        if (id != null && used.add(id)) {
            return id;
        }
        ResourceLocation synthetic = EAE2Pattern.id("jei/" + type.getUid().getPath() + "/" + fallback);
        return used.add(synthetic) ? synthetic : null;
    }

    @Nullable
    private static PatternKind dominantKind(int[] counts) {
        PatternKind best = null;
        int bestCount = 0;
        for (PatternKind kind : PatternKind.values()) {
            int count = counts[kind.ordinal()];
            if (count > bestCount) {
                best = kind;
                bestCount = count;
            }
        }
        return best;
    }

    private static PatternKind kindOf(Object recipe) {
        if (recipe instanceof CraftingRecipe) {
            return PatternKind.CRAFTING;
        }
        if (recipe instanceof StonecutterRecipe) {
            return PatternKind.STONECUTTING;
        }
        if (recipe instanceof SmithingRecipe) {
            return PatternKind.SMITHING;
        }
        return PatternKind.PROCESSING;
    }

    private static List<StackSpec> toSpecs(List<IRecipeSlotView> slotViews) {
        List<StackSpec> specs = new ArrayList<>(slotViews.size());
        for (IRecipeSlotView slot : slotViews) {
            if (slot.isEmpty()) {
                continue;
            }
            Optional<ItemStack> item = slot.getIngredients(VanillaTypes.ITEM_STACK).findFirst();
            if (item.isPresent() && !item.get().isEmpty()) {
                specs.add(StackSpec.item(item.get()));
                continue;
            }
            Optional<FluidStack> fluid = slot.getIngredients(ForgeTypes.FLUID_STACK).findFirst();
            if (fluid.isPresent() && !fluid.get().isEmpty()) {
                specs.add(StackSpec.fluid(fluid.get()));
            }
        }
        return specs;
    }
}
