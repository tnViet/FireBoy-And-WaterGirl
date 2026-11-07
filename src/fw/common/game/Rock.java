package fw.common.game;

class Rock implements Body {
    public final String id;
    public float x;
    public float y;

    public Rock(String id, float x, float y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public Rect rect() {
        return new Rect(x, y, Phys.ROCK, Phys.ROCK);
    }

    public void moveX(float dx) {
        x += dx;
    }

    public void moveY(float dy) {
        y += dy;
    }
}
