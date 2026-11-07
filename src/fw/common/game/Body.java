package fw.common.game;

interface Body {
    Rect rect();
    void moveX(float dx);
    void moveY(float dy);
}
