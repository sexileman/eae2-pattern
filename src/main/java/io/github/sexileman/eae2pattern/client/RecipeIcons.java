package io.github.sexileman.eae2pattern.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The mod's own little piece of pixel art: a stack of AE2 pattern cards.
 *
 * <p>It is drawn as a 16x16 character grid instead of a texture so it lives next to the code that uses it and can
 * be recoloured without opening an image editor. The same grid, scaled up eight times, is the mod logo shipped in
 * the jar ({@code eae2_pattern_logo.png}), so the button in JEI and the entry in the mod list look like the same
 * thing.</p>
 */
public final class RecipeIcons {

    public static final int SIZE = 16;

    /**
     * Palette:
     * {@code a} dark outline of the back card, {@code b} back card face (in shadow),
     * {@code k} outline of the front card, {@code w} front card face, {@code c} pattern cell.
     */
    private static final String[] PATTERN_STACK = {
            "................",
            ".aaaaaaaaa......",
            ".abbbbbbba......",
            ".abbbbbbba......",
            ".abbbbbbba......",
            ".abbbbbbba......",
            ".abbbbkkkkkkkkkk",
            ".abbbbkwwwwwwwwk",
            ".abbbbkwccwccwwk",
            ".aaaaakwccwccwwk",
            "......kwwwwwwwwk",
            "......kwccwccwwk",
            "......kwccwccwwk",
            "......kwwwwwwwwk",
            "......kwwwwwwwwk",
            "......kkkkkkkkkk",
    };

    private RecipeIcons() {
    }

    public static void drawPatternStack(GuiGraphics graphics, int x, int y) {
        drawPatternStack(graphics, x, y, 1.0F);
    }

    /**
     * @param brightness {@code 1.0} for full contrast, lower to fade the icon into its slot while it is not hovered
     */
    public static void drawPatternStack(GuiGraphics graphics, int x, int y, float brightness) {
        for (int row = 0; row < PATTERN_STACK.length; row++) {
            String line = PATTERN_STACK[row];
            for (int col = 0; col < line.length(); col++) {
                int color = colorOf(line.charAt(col));
                if (color != 0) {
                    graphics.fill(x + col, y + row, x + col + 1, y + row + 1, fade(color, brightness));
                }
            }
        }
    }

    private static int fade(int argb, float brightness) {
        if (brightness >= 0.999F) {
            return argb;
        }
        int alpha = argb >>> 24;
        int red = Math.round(((argb >> 16) & 0xFF) * brightness);
        int green = Math.round(((argb >> 8) & 0xFF) * brightness);
        int blue = Math.round((argb & 0xFF) * brightness);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int colorOf(char glyph) {
        return switch (glyph) {
            case 'a' -> 0xFF2B2B2B;
            case 'b' -> 0xFF6E6E6E;
            case 'k' -> 0xFF2B2B2B;
            case 'w' -> 0xFFECECEC;
            case 'c' -> 0xFF2F5F8F;
            default -> 0;
        };
    }
}
