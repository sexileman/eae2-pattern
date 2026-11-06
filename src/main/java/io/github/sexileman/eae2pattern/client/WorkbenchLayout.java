package io.github.sexileman.eae2pattern.client;

/** Geometry in Minecraft GUI pixels. Small windows retain one full recipe row. */
public record WorkbenchLayout(int width, int height, int left, int top, int split,
                              int searchY, int filterY, int toolbarY, int bodyTop, int bodyBottom,
                              int footerTop, int footerHeight, int footerControlsY, int statsY, boolean footerTitle) {
    public static WorkbenchLayout measure(int canvasWidth, int canvasHeight) {
        int w = Math.min(920, canvasWidth - 12), h = Math.min(540, canvasHeight - 12);
        int l = (canvasWidth - w) / 2, t = (canvasHeight - h) / 2;
        boolean compact = h < 230;
        boolean spacious = h >= 320;
        int search = compact ? 22 : spacious ? 39 : 30;
        int filter = search + (compact ? 20 : spacious ? 24 : 22);
        int toolbar = filter + (compact ? 20 : spacious ? 26 : 22);
        boolean title = h >= 260;
        int footer = t + h - (spacious ? 78 : title ? 64 : 43);
        int footerHeight = spacious ? 56 : title ? 42 : 23;
        int controls = footer + (spacious ? 28 : title ? 18 : 2);
        return new WorkbenchLayout(w, h, l, t, l + w * 3 / 5, search, filter, toolbar,
                t + toolbar + (compact ? 20 : spacious ? 27 : 24), footer - (spacious ? 10 : title ? 8 : 5),
                footer, footerHeight, controls, t + h - 15, title);
    }
}
