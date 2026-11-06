package io.github.sexileman.eae2pattern.client;

import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** One collection/preview route for every JEI category. */
public final class EAE2PatternClient {
    private EAE2PatternClient() {}
    public static void openCategory(IRecipeCategory<?> category) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new CollectingScreen(category, minecraft.screen));
    }
    public static void message(Component text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(text, false);
    }
}
