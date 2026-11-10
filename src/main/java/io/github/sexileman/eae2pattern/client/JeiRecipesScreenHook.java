package io.github.sexileman.eae2pattern.client;

import appeng.core.definitions.AEParts;
import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.compat.jei.JeiRuntimeHolder;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipeCatalysts;
import mezz.jei.gui.recipes.RecipeOptionButtons;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** The action occupies a real JEI catalyst slot, including JEI's own tab layout and background. */
@Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID, value = Dist.CLIENT)
public final class JeiRecipesScreenHook {
    private static Access access;
    private static boolean probed;
    private static WeakReference<RecipesGui> owner = new WeakReference<>(null);
    private static IRecipeSlotDrawable actionSlot;

    private JeiRecipesScreenHook() {}

    @SubscribeEvent
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof RecipesGui gui)) return;
        ensureActionSlot(gui);
    }

    @SubscribeEvent
    public static void clicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof RecipesGui gui)) return;
        ensureActionSlot(gui);
        if (owner.get() != gui || actionSlot == null || !actionSlot.isMouseOver(event.getMouseX(), event.getMouseY())) return;
        // Consume all mouse buttons so JEI never treats this action as a blank-pattern recipe lookup/bookmark.
        event.setCanceled(true);
        if (event.getButton() != 0) return;
        try {
            IRecipeGuiLogic logic = (IRecipeGuiLogic) access.logic.get(gui);
            if (logic.hasAllCategories()) {
                List<IRecipeCategory<?>> categories = logic.getRecipeCategories().stream()
                        .filter(c -> !"jei".equals(c.getRecipeType().getUid().getNamespace())).toList();
                if (!categories.isEmpty()) Minecraft.getInstance().setScreen(new MachinePickerScreen(
                        Component.translatable("eae2_pattern.picker.choose"), categories));
            } else if (logic.getSelectedRecipeCategory() != null) {
                EAE2PatternClient.openCategory(logic.getSelectedRecipeCategory());
            }
        } catch (ReflectiveOperationException e) { disable(e); }
    }

    @SuppressWarnings("unchecked")
    private static void ensureActionSlot(RecipesGui gui) {
        if (!probe() || JeiRuntimeHolder.get() == null) return;
        try {
            RecipeCatalysts catalysts = (RecipeCatalysts) access.catalysts.get(gui);
            List<IRecipeSlotDrawable> slots = (List<IRecipeSlotDrawable>) access.slots.get(catalysts);
            if (owner.get() == gui && actionSlot != null && slots.contains(actionSlot)) return;
            owner = new WeakReference<>(null);
            actionSlot = null;
            var area = gui.getArea();
            RecipeOptionButtons options = (RecipeOptionButtons) access.options.get(gui);
            // JEI requires at least one row. Never invent a fallback outside its catalyst area.
            if (area.isEmpty() || area.getHeight() - options.getArea().getHeight() - 20 < 16) return;
            IRecipeGuiLogic logic = (IRecipeGuiLogic) access.logic.get(gui);
            List<ITypedIngredient<?>> ingredients = new ArrayList<>(logic.getRecipeCatalysts().toList());
            var marker = JeiRuntimeHolder.get().getIngredientManager().createTypedIngredient(
                    VanillaTypes.ITEM_STACK, AEParts.PATTERN_ENCODING_TERMINAL.stack());
            if (marker.isEmpty()) return;
            ingredients.add(marker.get());
            // Reflow ALL slots with the additional entry. Handles one workstation, full columns and no catalysts.
            catalysts.updateLayout(ingredients, area, options.getArea());
            IRecipeSlotDrawable slot = slots.get(slots.size() - 1);
            if (slot.getRect().getX() < 0) {
                ingredients.remove(ingredients.size() - 1);
                catalysts.updateLayout(ingredients, area, options.getArea());
                return;
            }
            slot.addTooltipCallback((view, tooltip) -> {
                tooltip.clear();
                tooltip.add(Component.translatable("eae2_pattern.jei.button"));
                tooltip.add(Component.translatable("eae2_pattern.jei.button.desc").withStyle(ChatFormatting.GRAY));
            });
            actionSlot = slot;
            owner = new WeakReference<>(gui);
        } catch (ReflectiveOperationException | RuntimeException e) { disable(e); }
    }

    private static boolean probe() {
        if (!probed) {
            probed = true;
            try {
                access = new Access(field(RecipesGui.class, "logic"), field(RecipesGui.class, "recipeCatalysts"),
                        field(RecipesGui.class, "optionButtons"), field(RecipeCatalysts.class, "recipeSlots"));
            } catch (ReflectiveOperationException | RuntimeException e) { disable(e); }
        }
        return access != null;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void disable(Exception e) {
        access = null; actionSlot = null; owner.clear();
        EAE2Pattern.LOGGER.warn("JEI catalyst integration unavailable; no external fallback button is drawn", e);
    }

    private record Access(Field logic, Field catalysts, Field options, Field slots) {}
}
