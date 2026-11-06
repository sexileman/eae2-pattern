package io.github.sexileman.eae2pattern.client;

import net.minecraft.client.gui.GuiGraphics;

/** JEI renders the actual AE2 item. The only custom artwork is this unframed plus badge. */
public final class RecipeIcons {
    private RecipeIcons() {}

    public static void drawPlus(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y + 2, x + 6, y + 4, 0xFF7ADC55);
        graphics.fill(x + 2, y, x + 4, y + 6, 0xFF7ADC55);
    }
}
