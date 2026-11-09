package io.github.sexileman.eae2pattern.client;

/** AE2CT's installed tree style is 350x250 GUI units; shrink only for smaller viewports. */
public record CyclePanelLayout(int x, int y, int width, int height) {
    public static CyclePanelLayout fit(int screenWidth, int screenHeight) {
        int width = Math.min(350, Math.max(1, screenWidth - 40));
        int height = Math.min(250, Math.max(1, screenHeight - 40));
        return new CyclePanelLayout(
                (screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }
}
