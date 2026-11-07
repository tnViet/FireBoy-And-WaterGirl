package fw.client.render;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * SpriteGenerator - tạo placeholder sprite sheets
 * User sẽ thay thế bằng sprite thật sau
 */
public class SpriteGenerator {

    /**
     * Sinh sprite sheet tạm cho Fireboy: 4 hàng x 4 khung (32x48)
     * Hàng 0 đứng yên, 1 đi bộ, 2 nhảy, 3 chết
     */
    public static BufferedImage generateFireboy() {
        int frameW = 32, frameH = 48;
        int cols = 4, rows = 4;
        BufferedImage sheet = new BufferedImage(frameW * cols, frameH * rows, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color fireRed = new Color(255, 80, 0);
        Color fireOrange = new Color(255, 150, 0);

        // Hàng 0: đứng yên (nhấp nhô nhẹ như đang thở)
        for (int i = 0; i < 4; i++) {
            int x = i * frameW;
            int y = 0;
            // ±2 pixels
            int breathe = (int) (Math.sin(i * Math.PI / 2) * 2);
            drawCharacter(g, x, y, frameW, frameH, fireRed, fireOrange, breathe);
        }

        // Hàng 1: đi bộ
        for (int i = 0; i < 4; i++) {
            int x = i * frameW;
            int y = frameH;
            // alternate legs
            int legOffset = (i % 2 == 0) ? 3 : -3;
            drawCharacter(g, x, y, frameW, frameH, fireRed, fireOrange, legOffset);
        }

        // Hàng 2: nhảy (bay lên -> đỉnh -> rơi xuống)
        for (int i = 0; i < 4; i++) {
            int x = i * frameW;
            int y = frameH * 2;
            // compressed up, stretched down
            int jumpPose = (i <= 1) ? -4 : 2;
            drawCharacter(g, x, y, frameW, frameH, fireRed, fireOrange, jumpPose);
        }

        // Hàng 3: chết (mờ dần và co lại)
        for (int i = 0; i < 4; i++) {
            int x = i * frameW;
            int y = frameH * 3;
            float alpha = 1.0f - (i * 0.25f);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            drawCharacter(g, x, y, frameW, frameH, fireRed, fireOrange, i * 2);
            g.setComposite(AlphaComposite.SrcOver);
        }

        g.dispose();
        return sheet;
    }

    /**
     * Sinh sprite sheet tạm cho Watergirl (bố cục giống Fireboy)
     */
    public static BufferedImage generateWatergirl() {
        int frameW = 32, frameH = 48;
        int cols = 4, rows = 4;
        BufferedImage sheet = new BufferedImage(frameW * cols, frameH * rows, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color waterBlue = new Color(0, 150, 255);
        Color waterCyan = new Color(100, 200, 255);

        // Hàng 0: đứng yên
        for (int i = 0; i < 4; i++) {
            int x = i * frameW;
            int breathe = (int) (Math.sin(i * Math.PI / 2) * 2);
            drawCharacter(g, x, 0, frameW, frameH, waterBlue, waterCyan, breathe);
        }

        // Hàng 1: đi bộ
        for (int i = 0; i < 4; i++) {
            int x = i * frameW;
            int legOffset = (i % 2 == 0) ? 3 : -3;
            drawCharacter(g, x, frameH, frameW, frameH, waterBlue, waterCyan, legOffset);
        }

        // Hàng 2: nhảy
        for (int i = 0; i < 4; i++) {
            int x = i * frameW;
            int jumpPose = (i <= 1) ? -4 : 2;
            drawCharacter(g, x, frameH * 2, frameW, frameH, waterBlue, waterCyan, jumpPose);
        }

        // Hàng 3: chết
        for (int i = 0; i < 4; i++) {
            int x = i * frameW;
            float alpha = 1.0f - (i * 0.25f);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            drawCharacter(g, x, frameH * 3, frameW, frameH, waterBlue, waterCyan, i * 2);
            g.setComposite(AlphaComposite.SrcOver);
        }

        g.dispose();
        return sheet;
    }

    /**
     * Vẽ 1 nhân vật đơn giản (đầu, thân, tay chân); offset dùng để tạo dáng từng khung
     */
    private static void drawCharacter(Graphics2D g, int x, int y, int w, int h,
                                      Color primary, Color secondary, int offset) {
        int centerX = x + w / 2;
        int centerY = y + h / 2;

        // Thân (hình bầu dục)
        g.setColor(primary);
        g.fillOval(centerX - 8, centerY - 6 + offset, 16, 20);

        // Đầu (hình tròn tô chuyển màu)
        GradientPaint headGradient = new GradientPaint(
            centerX, centerY - 12, secondary,
            centerX, centerY - 20, primary
        );
        g.setPaint(headGradient);
        g.fillOval(centerX - 10, centerY - 20, 20, 20);

        // Mắt (2 chấm trắng)
        g.setColor(Color.WHITE);
        g.fillOval(centerX - 6, centerY - 16, 4, 4);
        g.fillOval(centerX + 2, centerY - 16, 4, 4);

        // Chân
        g.setColor(primary);
        g.setStroke(new BasicStroke(3));
        g.drawLine(centerX - 4, centerY + 14 + offset, centerX - 4, centerY + 22 + offset);
        g.drawLine(centerX + 4, centerY + 14 + offset, centerX + 4, centerY + 22 + offset);

        // Tay
        g.drawLine(centerX - 8, centerY + offset, centerX - 12, centerY + 6 + offset);
        g.drawLine(centerX + 8, centerY + offset, centerX + 12, centerY + 6 + offset);
    }

    /**
     * Main method để generate sprites khi cần
     */
    public static void main(String[] args) {
        try {
            // Ghi vào src/fw/client/resources để GamePanel load được qua classpath
            // (chạy từ thư mục gốc project)
            File dir = new File("src/fw/client/resources");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Sinh ảnh rồi lưu ra file
            BufferedImage fireboySheet = generateFireboy();
            ImageIO.write(fireboySheet, "PNG", new File(dir, "fireboy_sprite.png"));
            System.out.println("Generated: fireboy_sprite.png");

            BufferedImage watergirlSheet = generateWatergirl();
            ImageIO.write(watergirlSheet, "PNG", new File(dir, "watergirl_sprite.png"));
            System.out.println("Generated: watergirl_sprite.png");

            System.out.println("✅ Sprite sheets generated successfully!");
            System.out.println("   Each sheet: 128x192 pixels (4x4 frames, 32x48 each)");
            System.out.println("   Layout: Row 0=Idle, 1=Walk, 2=Jump, 3=Death");

        } catch (IOException e) {
            System.err.println("Failed to generate sprites: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
