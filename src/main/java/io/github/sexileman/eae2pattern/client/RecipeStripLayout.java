package io.github.sexileman.eae2pattern.client;

/** Count-independent columns; each side reserves room for its overflow count. */
public record RecipeStripLayout(int arrowX, int outputX, int inputMax, int outputMax, int inputWidth, int outputWidth) {
    public static int overflowWidth(int sideWidth,int visible) { return Math.max(0,sideWidth-visible*22-3); }
    public static RecipeStripLayout measure(int width) {
        int usable = Math.max(0, width - 22);
        int inputWidth = usable * 55 / 100;
        int outputWidth = usable - inputWidth;
        return new RecipeStripLayout(inputWidth + 7, inputWidth + 22,
                Math.max(1, Math.min(3, (inputWidth - 30) / 22)),
                Math.max(1, Math.min(2, (outputWidth - 30) / 22)), inputWidth, outputWidth);
    }
}
