package fw.common.game;

public class Plate {
    public final String id;
    public final float x;
    public final float y;
    public final float w;
    public final float h;
    public final String target;
    public boolean on;

    public Plate(String id, float x, float y, float w, float h, String target) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.target = target;
        this.on = false;
    }

    public Rect rect() {
        return new Rect(x, y, w, h);
    }
}
