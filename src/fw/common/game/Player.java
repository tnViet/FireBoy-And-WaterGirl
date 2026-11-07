package fw.common.game;

class Player implements Body {
    public final int acc;
    public final Element el;
    public float x;
    public float y;
    public float vx;
    public float vy;
    public int face;
    public boolean onGround;
    public boolean alive;
    public boolean done;
    public boolean jHeld;

    public Player(int acc, Element el, float x, float y) {
        this.acc = acc;
        this.el = el;
        this.x = x;
        this.y = y;
        this.vx = 0;
        this.vy = 0;
        this.face = 1;
        this.onGround = false;
        this.alive = true;
        this.done = false;
        this.jHeld = false;
    }

    public Rect rect() {
        return new Rect(x, y, Phys.PW, Phys.PH);
    }

    public void moveX(float dx) {
        x += dx;
    }

    public void moveY(float dy) {
        y += dy;
    }
}
