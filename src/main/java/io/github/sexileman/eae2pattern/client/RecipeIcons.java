package io.github.sexileman.eae2pattern.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The icon on the button we put inside JEI's catalyst tab.
 *
 * <p>It is Applied Energistics' own blank pattern sprite, drawn straight from AE2's resource pack, with a small
 * green "plus" badge in the bottom right corner. Drawing the real sprite instead of a copy of it keeps the button
 * consistent with the mod that owns the concept, follows resource packs that retexture AE2, and means this jar
 * ships no third party artwork for the button itself.</p>
 *
 * <p>The same shape, scaled up, is the mod logo shipped in the jar ({@code eae2_pattern_logo.png}), so the button in
 * JEI and the entry in the mod list look like the same thing.</p>
 */
public final class RecipeIcons {

    public static final int SIZE = 16;

    /** AE2's blank pattern item texture. We only reference the path; the pixels stay AE2's. */
    private static final ResourceLocation BLANK_PATTERN =
            new ResourceLocation("ae2", "textures/item/blank_pattern.png");

    private static final int BADGE_X = 9;
    private static final int BADGE_Y = 9;
    private static final int BADGE_SIZE = 6;
    private static final int COLOR_BADGE_BORDER = 0xFF141414;
    private static final int COLOR_BADGE_FACE = 0xFF333333;
    private static final int COLOR_PLUS = 0xFF7ADC55;

    private static boolean textureReady;

    private RecipeIcons() {
    }

    public static void drawPatternStack(GuiGraphics graphics, int x, int y) {
        drawPatternStack(graphics, x, y, 1.0F);
    }

    /**
     * @param brightness {@code 1.0} for full contrast, lower to fade the icon into its slot while it is not hovered
     */
    public static void drawPatternStack(GuiGraphics graphics, int x, int y, float brightness) {
        float level = Mth.clamp(brightness, 0.0F, 1.0F);
        ensureTextureLoaded();

        if (level < 0.999F) {
            graphics.setColor(level, level, level, 1.0F);
        }
        graphics.blit(BLANK_PATTERN, x, y, 0.0F, 0.0F, SIZE, SIZE, SIZE, SIZE);
        if (level < 0.999F) {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        drawBadge(graphics, x + BADGE_X, y + BADGE_Y, level);
    }

    private static void drawBadge(GuiGraphics graphics, int x, int y, float level) {
        graphics.fill(x, y, x + BADGE_SIZE, y + BADGE_SIZE, fade(COLOR_BADGE_BORDER, level));
        graphics.fill(x + 1, y + 1, x + BADGE_SIZE - 1, y + BADGE_SIZE - 1, fade(COLOR_BADGE_FACE, level));
        // A four pixel plus, two pixels thick, inside the four pixel face.
        graphics.fill(x + 1, y + 2, x + 5, y + 4, fade(COLOR_PLUS, level));
        graphics.fill(x + 2, y + 1, x + 4, y + 5, fade(COLOR_PLUS, level));
    }

    /** Makes sure the sprite is in the texture manager, which finally loads it from AE2's assets. */
    private static void ensureTextureLoaded() {
        if (textureReady) {
            return;
        }
        textureReady = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getTextureManager().getTexture(BLANK_PATTERN, new SimpleTexture(BLANK_PATTERN));
        }
    }

    private static int fade(int argb, float level) {
        if (level >= 0.999F) {
            return argb;
        }
        int alpha = argb >>> 24;
        int red = Math.round(((argb >> 16) & 0xFF) * level);
        int green = Math.round(((argb >> 8) & 0xFF) * level);
        int blue = Math.round((argb & 0xFF) * level);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
