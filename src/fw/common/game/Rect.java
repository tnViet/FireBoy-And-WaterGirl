package fw.common.game;

public class Rect {
    public float x;
    public float y;
    public float w;
    public float h;

    public Rect(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public boolean overlaps(Rect other) {
        return x < other.x + other.w && x + w > other.x &&
               y < other.y + other.h && y + h > other.y;
    }
}
