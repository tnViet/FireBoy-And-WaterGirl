package fw.common.game;

public class Key {
    public final String id;
    public final float x;
    public final float y;
    public boolean got;
    public int by;

    public Key(String id, float x, float y) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.got = false;
        this.by = 0;
    }

    public Rect rect() {
        return new Rect(x, y, Phys.KEY_S, Phys.KEY_S);
    }
}
