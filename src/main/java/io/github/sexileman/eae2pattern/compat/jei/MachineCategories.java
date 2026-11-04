package io.github.sexileman.eae2pattern.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Answers "which JEI categories is this item a machine for?".
 *
 * <p>This is the inverse of {@code createRecipeCatalystLookup}, and it is exactly what JEI itself does when it
 * decides which recipe tabs to show for the item under the cursor on a "uses" lookup. Reusing the catalyst focus
 * means we inherit every mod's catalyst registration for free.</p>
 */
public final class MachineCategories {

    private MachineCategories() {
    }

    public static List<IRecipeCategory<?>> forItem(ItemStack machine) {
        IJeiRuntime runtime = JeiRuntimeHolder.get();
        if (runtime == null || machine.isEmpty()) {
            return List.of();
        }
        IIngredientManager ingredientManager = runtime.getIngredientManager();
        Optional<ITypedIngredient<ItemStack>> typed = ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, machine);
        if (typed.isEmpty()) {
            return List.of();
        }
        IFocus<ItemStack> catalystFocus = runtime.getJeiHelpers().getFocusFactory()
                .createFocus(RecipeIngredientRole.CATALYST, typed.get());
        return runtime.getRecipeManager()
                .createRecipeCategoryLookup()
                .limitFocus(List.of(catalystFocus))
                .includeHidden()
                .get()
                .filter(category -> !category.getRecipeType().getUid().getNamespace().equals("jei"))
                .toList();
    }
}
