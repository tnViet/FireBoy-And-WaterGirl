package fw.common.game;

import java.util.ArrayList;
import java.util.List;

public class Door {
    public final String id;
    public final float x;
    public final float y;
    public final float w;
    public final float h;
    public boolean open;
    public final List<Integer> passed;

    public Door(String id, float x, float y, float w, float h) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.open = false;
        this.passed = new ArrayList<>();
    }

    public Rect rect() {
        return new Rect(x, y, w, h);
    }
}
