package fw.common.game;

public class Lever {
    public final String id;
    public final float x;
    public final float y;
    public final String target;
    public boolean on;
    public int cd;
    public boolean touch;

    public Lever(String id, float x, float y, String target) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.target = target;
        this.on = false;
        this.cd = 0;
        this.touch = false;
    }

    public Rect rect() {
        return new Rect(x, y, Phys.LEVER_W, Phys.LEVER_H);
    }
}
