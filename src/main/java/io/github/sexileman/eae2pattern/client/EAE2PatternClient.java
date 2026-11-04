package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.compat.jei.JeiRecipeCollector;
import io.github.sexileman.eae2pattern.compat.jei.JeiRuntimeHolder;
import io.github.sexileman.eae2pattern.compat.jei.MachineCategories;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Glue between the key binding, JEI and the two screens. */
public final class EAE2PatternClient {

    private EAE2PatternClient() {
    }

    public static void openPreviewForHovered() {
        if (JeiRuntimeHolder.get() == null) {
            message(Component.translatable("eae2_pattern.msg.no_jei"));
            return;
        }
        ItemStack machine = hoveredItem();
        if (machine.isEmpty()) {
            message(Component.translatable("eae2_pattern.msg.hover_machine"));
            return;
        }
        List<IRecipeCategory<?>> categories = MachineCategories.forItem(machine);
        if (categories.isEmpty()) {
            message(Component.translatable("eae2_pattern.msg.no_recipes", machine.getHoverName()));
            return;
        }
        if (categories.size() == 1) {
            openCategory(categories.get(0));
        } else {
            Minecraft.getInstance().setScreen(new MachinePickerScreen(machine, categories));
        }
    }

    public static void openCategory(IRecipeCategory<?> category) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new CollectingScreen(category, minecraft.screen));
    }

    private static ItemStack hoveredItem() {
        IJeiRuntime runtime = JeiRuntimeHolder.get();
        if (runtime == null) {
            return ItemStack.EMPTY;
        }
        return runtime.getIngredientListOverlay()
                .getIngredientUnderMouse()
                .flatMap(ITypedIngredient::getItemStack)
                .orElse(ItemStack.EMPTY);
    }

    public static void message(Component text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(text, false);
        }
    }
}
