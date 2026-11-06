package io.github.sexileman.eae2pattern.client;

/** Geometry in Minecraft GUI pixels. Small windows retain one full recipe row. */
public record WorkbenchLayout(int width, int height, int left, int top, int split,
                              int searchY, int filterY, int toolbarY, int bodyTop, int bodyBottom) {
    public static WorkbenchLayout measure(int canvasWidth, int canvasHeight) {
        int w = Math.min(920, canvasWidth - 12), h = Math.min(540, canvasHeight - 12);
        int l = (canvasWidth - w) / 2, t = (canvasHeight - h) / 2;
        boolean compact = h < 230;
        int search = compact ? 22 : 39;
        int filter = search + (compact ? 20 : 24), toolbar = filter + (compact ? 20 : 26);
        return new WorkbenchLayout(w, h, l, t, l + w * 3 / 5, search, filter, toolbar,
                t + toolbar + (compact ? 20 : 27), t + h - 48);
    }
}
