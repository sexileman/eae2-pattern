package io.github.sexileman.eae2pattern.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Explicit placeholder for an unbordered field, visible even while the empty field has focus. */
public final class WorkbenchSearchBox extends EditBox {
    private final Font textFont;
    private final Component placeholder;
    public WorkbenchSearchBox(Font font, int x, int y, int width, Component placeholder) {
        this(font, x, y, width, placeholder, placeholder);
    }
    public WorkbenchSearchBox(Font font, int x, int y, int width, Component label, Component placeholder) {
        super(font, x, y, width, 14, label);
        this.textFont = font; this.placeholder = placeholder;
        setBordered(false);
    }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (getValue().isEmpty()) g.drawString(textFont,
                textFont.plainSubstrByWidth(placeholder.getString(), Math.max(0, getWidth() - 2)),
                getX(), getY(), WorkbenchTheme.PLACEHOLDER, false);
        // Draw the native cursor last, retaining text selection, clipboard, IME and keyboard behavior.
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }
}
