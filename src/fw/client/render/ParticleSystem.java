package fw.client.render;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ParticleSystem - quản lý tất cả particles
 */
public class ParticleSystem {
    private final List<Particle> particles;
    private final Random rand;

    public ParticleSystem() {
        this.particles = new ArrayList<>();
        this.rand = new Random();
    }

    /**
     * Thêm explosion particles (khi chết)
     */
    public void addExplosion(float x, float y, Color baseColor, int count) {
        for (int i = 0; i < count; i++) {
            float angle = (float) (rand.nextFloat() * Math.PI * 2);
            float speed = 1 + rand.nextFloat() * 3;
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed;

            int life = 20 + rand.nextInt(20);
            float size = 3 + rand.nextFloat() * 5;

            particles.add(new Particle(x, y, vx, vy, baseColor, life, size, 0.15f));
        }
    }

    /**
     * Thêm splash particles (khi chạm nước/lava)
     */
    public void addSplash(float x, float y, Color color, int count) {
        for (int i = 0; i < count; i++) {
            float vx = (rand.nextFloat() - 0.5f) * 4;
            float vy = -2 - rand.nextFloat() * 2;

            int life = 15 + rand.nextInt(15);
            float size = 2 + rand.nextFloat() * 3;

            particles.add(new Particle(x, y, vx, vy, color, life, size, 0.2f));
        }
    }

    /**
     * Thêm sparkle particles (khi lấy key)
     */
    public void addSparkle(float x, float y, int count) {
        for (int i = 0; i < count; i++) {
            float vx = (rand.nextFloat() - 0.5f) * 2;
            float vy = (rand.nextFloat() - 0.5f) * 2;

            Color gold = new Color(255, 215, 0);
            int life = 30 + rand.nextInt(20);
            float size = 3 + rand.nextFloat() * 4;

            particles.add(new Particle(x, y, vx, vy, gold, life, size, 0.05f));
        }
    }

    /**
     * Thêm ambient particles (continuous effect cho zones)
     */
    public void addAmbient(float x, float y, float w, float h, Color color) {
        // 30% chance mỗi frame
        if (rand.nextFloat() < 0.3f) {
            float px = x + rand.nextFloat() * w;
            float py = y + rand.nextFloat() * h;
            float vx = (rand.nextFloat() - 0.5f) * 0.5f;
            float vy = -0.5f - rand.nextFloat() * 0.5f;

            int life = 40 + rand.nextInt(20);
            float size = 2 + rand.nextFloat() * 3;

            particles.add(new Particle(px, py, vx, vy, color, life, size, 0.02f));
        }
    }

    /**
     * Update tất cả particles
     */
    public void update() {
        particles.removeIf(p -> !p.isAlive());
        for (Particle p : particles) {
            p.update();
        }
    }

    /**
     * Render tất cả particles
     */
    public void render(Graphics2D g) {
        for (Particle p : particles) {
            p.render(g);
        }
    }

    /**
     * Clear tất cả particles
     */
    public void clear() {
        particles.clear();
    }
}
