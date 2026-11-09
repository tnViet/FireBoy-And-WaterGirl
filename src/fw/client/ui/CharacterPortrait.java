package fw.client.ui;

import fw.client.render.Gfx;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Ảnh nhân vật phóng to trong thẻ người chơi ở phòng chờ: chạy animation đứng yên (idle).
 * Phóng theo bội số nguyên để giữ nét pixel art, căn giữa, đứng trên một bóng mờ.
 */
public class CharacterPortrait extends JComponent {
    private static final int FRAME = 30;
    // idle.png: 24 khung 30x30; chân ở hàng 27, thân từ cột 6 đến 22
    private static final int FOOT_Y = 28;
    private static final int MAX_SCALE = 7;

    private final BufferedImage sheet;
    private final int frames;
    private final Color tint;
    private final Timer timer;
    private int frame;

    /** who = "fireboy" hoặc "watergirl" */
    public CharacterPortrait(String who, Color tint) {
        this.sheet = Gfx.img("char/" + who + "_idle.png");
        this.frames = sheet == null ? 0 : sheet.getWidth() / FRAME;
        this.tint = tint;
        setOpaque(false);
        // ~12 khung/giây, giống tốc độ idle trong màn chơi
        timer = new Timer(85, e -> {
            if (frames > 0) frame = (frame + 1) % frames;
            repaint();
        });
    }

    // Chỉ chạy timer khi thẻ đang hiện (thẻ được tạo lại mỗi lần phòng cập nhật)
    @Override public void addNotify()    { super.addNotify(); timer.start(); }
    @Override public void removeNotify() { timer.stop(); super.removeNotify(); }

    @Override
    protected void paintComponent(Graphics g0) {
        if (sheet == null) return;
        int w = getWidth(), h = getHeight();
        int scale = Math.max(1, Math.min(MAX_SCALE, Math.min(w, h) / FRAME));
        int size = FRAME * scale;
        int x = (w - size) / 2;
        // đặt chân cách đáy một khoảng nhỏ, khung không vượt mép trên
        int floor = Math.min(h - 12, size - 4 + Math.max(0, (h - size) / 2));
        int y = floor - FOOT_Y * scale;

        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // bóng mờ dưới chân, màu theo nhân vật
        int sw = 18 * scale, sh = Math.max(6, 3 * scale);
        g.setColor(Theme.alpha(tint, 60));
        g.fillOval(w / 2 - sw / 2, floor - sh / 2, sw, sh);

        Gfx.draw(g, sheet.getSubimage(frame * FRAME, 0, FRAME, FRAME), x, y, size, size);
        g.dispose();
    }
}
