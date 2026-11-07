package fw.common.game;

public class Zone {
    public final String id;
    public final ZoneKind kind;
    public final float x;
    public final float y;
    public final float w;
    public final float h;

    public Zone(String id, ZoneKind kind, float x, float y, float w, float h) {
        this.id = id;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public Rect rect() {
        return new Rect(x, y, w, h);
    }
}
