package io.github.sexileman.eae2pattern.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Shared destination of all JEI entry points. */
public final class PreviewGui {
    private PreviewGui() {}
    public static void open(RecipePlan plan, Screen parent) {
        Minecraft.getInstance().setScreen(new PatternWorkbenchScreen(plan, parent));
    }
}
