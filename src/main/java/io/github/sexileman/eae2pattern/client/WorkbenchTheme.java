package io.github.sexileman.eae2pattern.client;

import net.minecraft.client.gui.GuiGraphics;

/** Neutral surfaces, thin separators and restrained highlights, matching the Codex desktop palette. */
public final class WorkbenchTheme {
    public static final int BACKGROUND = 0xFF191919;
    public static final int SURFACE = 0xFF222222;
    public static final int HOVER = 0xFF2D2D2D;
    public static final int LINE = 0xFF363636;
    public static final int TEXT = 0xFFECECEC;
    public static final int DIM = 0xFFA0A0A0;
    public static final int ACCENT = 0xFFF0F0F0;
    public static final int WARNING = 0xFFE5B87E;
    public static final int PLACEHOLDER = 0xFF858585;
    public static final int INPUT = 0xFF1C1C1C;
    public static final int PRIMARY = 0xFF363636;
    public static final int PRIMARY_HOVER = 0xFF424242;
    private WorkbenchTheme() {}

    public static void rounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w < 6 || h < 6) { g.fill(x, y, x + w, y + h, color); return; }
        g.fill(x + 3, y, x + w - 3, y + h, color);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        g.fill(x, y + 3, x + w, y + h - 3, color);
    }

    public static void control(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        rounded(g, x, y, w, h, border);
        rounded(g, x + 1, y + 1, w - 2, h - 2, fill);
    }
    public static void slot(GuiGraphics g, int x, int y, IngredientPresentation presentation) {
        g.fill(x, y, x + 24, y + 24, LINE);
        g.fill(x + 1, y + 1, x + 23, y + 23, INPUT);
        if (presentation.kind() != IngredientPresentation.Kind.REGULAR)
            g.fill(x + 1, y + 22, x + 23, y + 23, presentation.color());
    }
}
