package fw.client.render;

import fw.common.util.Log;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Nạp và cắt ảnh đồ họa trong /fw/client/resources/gfx (bộ Lost Lamb + vật phẩm riêng).
 * Dùng chung cho màn chơi (ScenePainter) và giao diện Swing (Theme).
 * Ảnh pixel art nên luôn vẽ phóng to kiểu "nearest neighbor" để không bị nhòe.
 */
public final class Gfx {
    private Gfx() {}

    private static final String DIR = "/fw/client/resources/gfx/";
    private static final Map<String, BufferedImage> cache = new HashMap<>();
    /** Nền ghép sẵn theo kích thước: chỉ giữ vài bản gần nhất (cửa sổ đổi cỡ liên tục) */
    private static final Map<String, BufferedImage> bgCache = new java.util.LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> e) { return size() > 6; }
    };

    /** Ảnh theo tên file trong thư mục gfx, null nếu không có */
    public static synchronized BufferedImage img(String name) {
        if (cache.containsKey(name)) return cache.get(name);
        BufferedImage img = null;
        try (InputStream in = Gfx.class.getResourceAsStream(DIR + name)) {
            if (in != null) img = toArgb(ImageIO.read(in));
            else Log.warn("GFX", "Missing image: " + name);
        } catch (Exception e) {
            Log.warn("GFX", "Cannot load image: " + name);
        }
        cache.put(name, img);
        return img;
    }

    /** Một vùng con của ảnh (có cache), null nếu ảnh không có */
    public static synchronized BufferedImage sub(String name, int x, int y, int w, int h) {
        String key = name + "@" + x + "," + y + "," + w + "," + h;
        if (cache.containsKey(key)) return cache.get(key);
        BufferedImage src = img(name);
        BufferedImage s = src == null ? null : src.getSubimage(x, y, w, h);
        cache.put(key, s);
        return s;
    }

    private static BufferedImage toArgb(BufferedImage src) {
        if (src == null || src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    // ── Vẽ pixel art ────────────────────────────────────────────────────

    /** Vẽ ảnh co giãn vào (x, y, w, h) không làm nhòe điểm ảnh */
    public static void draw(Graphics2D g, BufferedImage img, int x, int y, int w, int h) {
        if (img == null) return;
        Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, x, y, w, h, null);
        if (old != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
    }

    /**
     * Vẽ khung "9 mảnh": 4 góc giữ nguyên (phóng theo scale), 4 cạnh và phần giữa kéo giãn.
     * b = bề dày viền trong ảnh gốc (px).
     */
    public static void nine(Graphics2D g, BufferedImage src, int b, int scale, int x, int y, int w, int h) {
        if (src == null || w <= 0 || h <= 0) return;
        int sw = src.getWidth(), sh = src.getHeight();
        // viền sau khi phóng, không vượt nửa khung
        int d = Math.min(b * scale, Math.min(w, h) / 2);
        int[] sx = {0, b, sw - b, sw};
        int[] sy = {0, b, sh - b, sh};
        int[] dx = {x, x + d, x + w - d, x + w};
        int[] dy = {y, y + d, y + h - d, y + h};
        Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (dx[c + 1] <= dx[c] || dy[r + 1] <= dy[r]) continue;
                g.drawImage(src, dx[c], dy[r], dx[c + 1], dy[r + 1], sx[c], sy[r], sx[c + 1], sy[r + 1], null);
            }
        }
        if (old != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
    }

    /**
     * Giống nine() nhưng 4 cạnh được LẶP LẠI thay vì kéo giãn (cho viền có hoa văn như hàng đinh gỗ).
     * Phần giữa tô phẳng bằng màu ở tâm ảnh nguồn (kéo giãn sẽ làm các nét trang trí bị bè ra).
     */
    public static void nineTiled(Graphics2D g, BufferedImage src, int b, int scale, int x, int y, int w, int h) {
        if (src == null || w <= 0 || h <= 0) return;
        int sw = src.getWidth(), sh = src.getHeight();
        int d = b * scale;
        // độ dài 1 đoạn cạnh sau khi phóng
        int ew = (sw - 2 * b) * scale, eh = (sh - 2 * b) * scale;
        Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        Shape clip = g.getClip();
        // Giữa
        g.setColor(new Color(src.getRGB(sw / 2, sh / 2), true));
        g.fillRect(x + d, y + d, Math.max(0, w - 2 * d), Math.max(0, h - 2 * d));
        // Cạnh trên / dưới
        g.clipRect(x + d, y, Math.max(0, w - 2 * d), h);
        for (int xx = x + d; xx < x + w - d; xx += ew) {
            g.drawImage(src, xx, y, xx + ew, y + d, b, 0, sw - b, b, null);
            g.drawImage(src, xx, y + h - d, xx + ew, y + h, b, sh - b, sw - b, sh, null);
        }
        g.setClip(clip);
        // Cạnh trái / phải
        g.clipRect(x, y + d, w, Math.max(0, h - 2 * d));
        for (int yy = y + d; yy < y + h - d; yy += eh) {
            g.drawImage(src, x, yy, x + d, yy + eh, 0, b, b, sh - b, null);
            g.drawImage(src, x + w - d, yy, x + w, yy + eh, sw - b, b, sw, sh - b, null);
        }
        g.setClip(clip);
        // 4 góc
        g.drawImage(src, x, y, x + d, y + d, 0, 0, b, b, null);
        g.drawImage(src, x + w - d, y, x + w, y + d, sw - b, 0, sw, b, null);
        g.drawImage(src, x, y + h - d, x + d, y + h, 0, sh - b, b, sh, null);
        g.drawImage(src, x + w - d, y + h - d, x + w, y + h, sw - b, sh - b, sw, sh, null);
        if (old != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
    }

    // ── Mảnh giao diện trong UI.png ─────────────────────────────────────

    /** Khung giấy da viền đinh gỗ (form đăng nhập, hộp thông báo) */
    public static BufferedImage uiParchment() { return sub("ui.png", 0, 80, 48, 48); }

    /** Khung tối lớn (bảng, thẻ) */
    public static BufferedImage uiPanel()      { return sub("ui.png", 48, 64, 48, 48); }
    /** Ô nhập lõm (khung tối nhỏ) */
    public static BufferedImage uiInset()      { return sub("ui.png", 96, 112, 16, 16); }
    /** Khung tối hẹp đứng (dùng cho cổng chắn) */
    public static BufferedImage uiPillar()     { return sub("ui.png", 96, 64, 16, 48); }
    /** Nút tối */
    public static BufferedImage uiButton()     { return sub("ui.png", 96, 0, 32, 16); }
    /** Nút cam (hành động chính) */
    public static BufferedImage uiButtonMain() { return sub("ui.png", 96, 32, 32, 16); }
    /** Thanh ngang mảnh (đế nút bấm) */
    public static BufferedImage uiBar()        { return sub("ui.png", 112, 112, 48, 12); }
    /** Biểu tượng 16x16 trong lưới icon của UI.png (cột, hàng tính từ ô (0,160)) */
    public static BufferedImage uiIcon(int col, int row) { return sub("ui.png", col * 16, 160 + row * 16, 16, 16); }

    // ── Ảnh nền nhiều lớp (Backgrounds của Lost Lamb) ───────────────────

    public static final String FOREST = "forest", MOUNTAINS = "mountains", CAVE = "cave";

    /** Chủ đề nền theo số màn: lần lượt rừng, núi, hang, rồi lặp lại */
    public static String themeOf(int lv) {
        String[] t = {FOREST, MOUNTAINS, CAVE};
        return t[Math.floorMod(lv - 1, t.length)];
    }

    /**
     * Ghép 3 lớp nền (xa -> gần) phủ kín w x h: phóng đều để lấp đầy, căn đáy và giữa.
     * Kết quả được cache theo chủ đề và kích thước.
     */
    public static synchronized BufferedImage background(String theme, int w, int h) {
        String key = "bg:" + theme + ":" + w + "x" + h;
        BufferedImage hit = bgCache.get(key);
        if (hit != null) return hit;
        w = Math.max(1, w);
        h = Math.max(1, h);
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(0x2D1927));
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int i = 1; i <= 3; i++) {
            BufferedImage layer = img("bg/" + theme + i + ".png");
            if (layer == null) continue;
            double s = Math.max((double) w / layer.getWidth(), (double) h / layer.getHeight());
            int lw = (int) Math.ceil(layer.getWidth() * s), lh = (int) Math.ceil(layer.getHeight() * s);
            g.drawImage(layer, (w - lw) / 2, h - lh, lw, lh, null);
        }
        g.dispose();
        bgCache.put(key, out);
        return out;
    }
}
