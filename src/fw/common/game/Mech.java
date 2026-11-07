package fw.common.game;

/**
 * Cơ quan di động (thang máy hoặc cổng chắn): chạy giữa vị trí gốc (p = 0) và vị trí cuối (p = 1)
 * theo trục x hoặc y. Vị trí hiện tại = vị trí gốc + p * quãng đường.
 */
class Mech implements Body {
    public final String id;
    public final MechKind kind;
    public final float x0;
    public final float y0;
    public final float w;
    public final float h;
    public final char axis;
    public final float dist;
    public final float speed;
    public float p;
    public boolean moving;
    private boolean wasMoving;

    public Mech(String id, MechKind kind, float x0, float y0, float w, float h,
                char axis, float dist, float speed) {
        this.id = id;
        this.kind = kind;
        this.x0 = x0;
        this.y0 = y0;
        this.w = w;
        this.h = h;
        this.axis = axis;
        this.dist = dist;
        this.speed = speed;
        this.p = 0;
        this.moving = false;
        this.wasMoving = false;
    }

    public float x() {
        return x0 + (axis == 'x' ? p * dist : 0);
    }

    public float y() {
        return y0 + (axis == 'y' ? p * dist : 0);
    }

    public Rect rect() {
        return new Rect(x(), y(), w, h);
    }

    public void saveMovingState() {
        wasMoving = moving;
    }

    public boolean movingChanged() {
        return moving != wasMoving;
    }

    public void moveX(float dx) {
        // Cơ quan di chuyển bằng tiến độ p, không dịch trực tiếp x/y
    }

    public void moveY(float dy) {
        // Cơ quan di chuyển bằng tiến độ p, không dịch trực tiếp x/y
    }
}
