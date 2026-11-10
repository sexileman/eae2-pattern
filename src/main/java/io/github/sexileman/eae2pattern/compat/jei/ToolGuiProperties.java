package io.github.sexileman.eae2pattern.compat.jei;

import mezz.jei.api.gui.handlers.IGuiProperties;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

/** JEI caches previous properties: these values must never follow mutable screen fields. */
public record ToolGuiProperties(Class<? extends Screen> screenClass, int left, int top,
        int width, int height, int screenWidth, int screenHeight) implements IGuiProperties {
    public static IGuiProperties capture(Class<? extends Screen> type, Rect2i area,
            int screenWidth, int screenHeight) {
        // Opening is delivered before Screen.init. Do not let JEI cache a zero-sized live view.
        if (area.getWidth() <= 0 || area.getHeight() <= 0 || screenWidth <= 0 || screenHeight <= 0)
            return null;
        return new ToolGuiProperties(type, area.getX(), area.getY(), area.getWidth(),
                area.getHeight(), screenWidth, screenHeight);
    }
    @Override public Class<? extends Screen> getScreenClass() { return screenClass; }
    @Override public int getGuiLeft() { return left; }
    @Override public int getGuiTop() { return top; }
    @Override public int getGuiXSize() { return width; }
    @Override public int getGuiYSize() { return height; }
    @Override public int getScreenWidth() { return screenWidth; }
    @Override public int getScreenHeight() { return screenHeight; }
}
