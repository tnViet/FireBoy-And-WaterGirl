package fw.client.render;

import java.awt.*;

/**
 * Particle - hạt hiệu ứng (lửa, nước, khói, etc.)
 */
public class Particle {
    private float x, y;
    // vận tốc
    private float vx, vy;
    private Color color;
    // số tick còn sống
    private int life;
    private int maxLife;
    private float size;
    private float gravity;

    public Particle(float x, float y, float vx, float vy, Color color, int life, float size, float gravity) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.color = color;
        this.life = life;
        this.maxLife = life;
        this.size = size;
        this.gravity = gravity;
    }

    /**
     * Update particle mỗi frame
     */
    public void update() {
        x += vx;
        y += vy;
        // áp dụng trọng lực
        vy += gravity;
        life--;
    }

    /**
     * Vẽ particle
     */
    public void render(Graphics2D g) {
        if (life <= 0) return;

        // Alpha giảm dần theo thời gian
        float alpha = (float) life / maxLife;
        Color renderColor = new Color(
            color.getRed(),
            color.getGreen(),
            color.getBlue(),
            (int) (alpha * 255)
        );

        g.setColor(renderColor);
        // size giảm dần
        float currentSize = size * alpha;
        g.fillOval((int) (x - currentSize / 2), (int) (y - currentSize / 2),
                   (int) currentSize, (int) currentSize);
    }

    /**
     * Kiểm tra particle còn sống không
     */
    public boolean isAlive() {
        return life > 0;
    }
}
