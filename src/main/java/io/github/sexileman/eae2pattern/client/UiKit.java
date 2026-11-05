package io.github.sexileman.eae2pattern.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Small drawing helpers for the flat, rounded look the preview screen wears.
 *
 * <p>Vanilla {@code Button} paints the 1.20 stone texture, which fights with a dark panel, so the screen draws its
 * own controls instead of leaning on {@code addRenderableWidget}. Everything here is a thin wrapper over
 * {@link GuiGraphics#fill} - rounded corners are made by insetting each scan line with the circle equation, which
 * costs one quad per row and is cheap enough for a handful of boxes.</p>
 */
public final class UiKit {

    private UiKit() {
    }

    /** Fills a rectangle with rounded corners. Alpha in {@code argb} is honoured, so translucent panels work. */
    public static void roundedRect(GuiGraphics graphics, int x, int y, int width, int height, int radius, int argb) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width / 2, height / 2)));
        if (r == 0) {
            graphics.fill(x, y, x + width, y + height, argb);
            return;
        }
        for (int row = 0; row < height; row++) {
            int inset = 0;
            if (row < r) {
                inset = cornerInset(r, row);
            } else if (row >= height - r) {
                inset = cornerInset(r, height - 1 - row);
            }
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, argb);
        }
    }

    /** A rounded rectangle with a 1px border, drawn as a larger rounded rect with a smaller one on top. */
    public static void roundedFrame(GuiGraphics graphics, int x, int y, int width, int height, int radius,
                                    int fill, int border) {
        roundedRect(graphics, x, y, width, height, radius, border);
        roundedRect(graphics, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1), fill);
    }

    private static int cornerInset(int radius, int row) {
        // Square distance from the corner centre, so row 0 is the most inset and row radius-1 is flush.
        double dy = radius - 0.5D - row;
        double span = radius * radius - dy * dy;
        if (span <= 0.0D) {
            return radius;
        }
        return (int) Math.round(radius - Math.sqrt(span));
    }

    public static boolean over(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** 30x16 pill switch. Off is a grey track, on is the accent colour with the knob pushed right. */
    public static void toggle(GuiGraphics graphics, int x, int y, boolean on, int accent, int trackOff, int knob) {
        int w = 30;
        int h = 16;
        roundedRect(graphics, x, y, w, h, h / 2, on ? accent : trackOff);
        int knobX = on ? x + w - 13 : x + 3;
        roundedRect(graphics, knobX, y + 3, 10, 10, 5, knob);
    }

    /** 12x12 rounded checkbox with a hand-drawn tick. */
    public static void checkbox(GuiGraphics graphics, int x, int y, boolean on, int accent, int idleFill, int idleBorder) {
        checkbox(graphics, x, y, 12, on, accent, idleFill, idleBorder);
    }

    public static void checkbox(GuiGraphics graphics, int x, int y, int size, boolean on, int accent, int idleFill,
                                int idleBorder) {
        if (on) {
            roundedRect(graphics, x, y, size, size, Math.max(2, size / 4), accent);
            // Tick: two short strokes, drawn as thin quads.
            int quarter = Math.max(2, size / 4);
            graphics.fill(x + quarter, y + size / 2, x + size / 2, y + size - quarter, 0xFFFFFFFF);
            graphics.fill(x + size / 2, y + quarter, x + size - quarter, y + size / 2 + 1, 0xFFFFFFFF);
            graphics.fill(x + size / 2, y + quarter + 1, x + size - quarter - 1, y + size / 2, 0xFFFFFFFF);
        } else {
            roundedFrame(graphics, x, y, 12, 12, 3, idleFill, idleBorder);
        }
    }

    /** Truncates with the game's own measuring, so a long item name never runs under a badge. */
    public static String fit(Font font, String text, int width) {
        if (width <= 0) {
            return "";
        }
        return font.plainSubstrByWidth(text, width);
    }

    public static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color, false);
    }

    /** Section caption used above each sidebar block: dim, slightly spaced. */
    public static void caption(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color, false);
    }
}
