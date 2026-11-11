package io.github.sexileman.eae2pattern.client;

/** Logical GUI pixels; reserve side lanes and bottom space for JEI instead of covering the screen. */
public record ToolViewport(int left,int top,int width,int height) {
    public static ToolViewport measure(int canvasWidth,int canvasHeight,int preferredWidth,int preferredHeight) {
        int lane=canvasWidth>=400?72:canvasWidth>=360?54:18;
        int available=Math.min(canvasWidth-12,Math.max(244,canvasWidth-lane*2-12));
        int width=Math.max(1,Math.min(preferredWidth,available));
        int height=Math.max(1,Math.min(preferredHeight,canvasHeight-(canvasHeight>=220?34:12)));
        int top=Math.max(6,(canvasHeight-(canvasHeight>=220?22:0)-height)/2);
        return new ToolViewport((canvasWidth-width)/2,top,width,height);
    }
    public static ToolViewport encoding(int canvasWidth,int canvasHeight) {
        return measure(canvasWidth,canvasHeight,380,300);
    }
    public boolean contains(double x,double y){return x>=left&&x<left+width&&y>=top&&y<top+height;}
}
