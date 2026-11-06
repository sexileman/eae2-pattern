package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.compat.jei.JeiRuntimeHolder;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.gui.screens.Screen;
import java.lang.reflect.Field;
import java.util.List;

public final class RecipeNavigation {
    private RecipeNavigation() {}
    public record ReturnContext(Screen jei, Screen originalParent, Field field) {
        public void restore() {
            try { field.set(jei, originalParent); }
            catch (IllegalAccessException ignored) { }
        }
    }
    public static ReturnContext capture(Screen parent) {
        if (!(parent instanceof RecipesGui)) {
            var runtime = JeiRuntimeHolder.get();
            if (runtime == null || !(runtime.getRecipesGui() instanceof RecipesGui gui)) return null;
            parent = gui;
        }
        try {
            Field field = RecipesGui.class.getDeclaredField("parentScreen"); field.setAccessible(true);
            return new ReturnContext(parent, (Screen) field.get(parent), field);
        } catch (ReflectiveOperationException | RuntimeException e) { return null; }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void show(RecipePlan plan, RecipePlan.Entry entry) {
        var runtime = JeiRuntimeHolder.get();
        if (runtime != null && plan.jeiCategory() != null && entry.jeiRecipe() != null)
            runtime.getRecipesGui().showRecipes((IRecipeCategory) plan.jeiCategory(), List.of(entry.jeiRecipe()), List.of());
    }
}
