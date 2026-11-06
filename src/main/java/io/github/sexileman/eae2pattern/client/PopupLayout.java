package io.github.sexileman.eae2pattern.client;

/** Shared menu geometry and hit-testing, including compact 320x180 GUI canvases. */
public record PopupLayout(int x, int y, int width, int height, int rows) {
    public static PopupLayout measure(int anchorX, int anchorY, int anchorWidth, int anchorHeight,
                                      int desiredWidth, int count, int canvasWidth, int canvasHeight) {
        if (count < 1 || count > 8) throw new IllegalArgumentException("Unsupported menu size");
        int w = Math.min(canvasWidth - 12, Math.max(anchorWidth, desiredWidth));
        int h = count * 19 + 6;
        return new PopupLayout(Math.max(6, Math.min(anchorX, canvasWidth - w - 6)),
                Math.max(6, Math.min(anchorY + anchorHeight + 3, canvasHeight - h - 6)), w, h, count);
    }
    public int indexAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y + 3 || mouseY >= y + height - 3) return -1;
        return (int) (mouseY - y - 3) / 19;
    }
}
