package fw.client.render;

import fw.common.game.Level;
import fw.common.game.Zone;
import fw.common.game.ZoneKind;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Vẽ màn chơi bằng đồ họa pixel art:
 *  - Nền nhiều lớp và khối đất/cỏ lấy từ bộ Lost Lamb (Backgrounds, Tileset).
 *  - Thang máy, cổng chắn, nút bấm ghép từ Tileset và UI của Lost Lamb.
 *  - Dung nham, nước, chìa khóa, cửa, cần gạt, khối đá dùng ảnh riêng trong assets.
 * Mỗi hàm vẽ 1 loại vật thể; GamePanel chỉ việc gọi theo thứ tự.
 */
final class ScenePainter {
    private ScenePainter() {}

    static final int TILE = 32;
    /** Ô gốc trong Tileset.png là 16px, vẽ phóng 2 lần cho vừa ô 32px của game */
    private static final int SRC = 16;

    private static final Color OUTLINE = new Color(0x090A14);
    private static final Color WOOD    = new Color(0x492329);
    private static final Color WOOD_LT = new Color(0x5B4754);

    // ── Nền nhiều lớp ───────────────────────────────────────────────────

    static void background(Graphics2D g, int w, int h, String theme) {
        g.drawImage(Gfx.background(theme, w, h), 0, 0, null);
    }

    // ── Khối đất (vẽ sẵn 1 lần cho mỗi màn) ─────────────────────────────

    private static Level tileLevel;
    private static BufferedImage tileCache;

    static void tiles(Graphics2D g, Level lv) {
        if (lv != tileLevel || tileCache == null) {
            tileLevel = lv;
            tileCache = renderTiles(lv);
        }
        g.drawImage(tileCache, 0, 0, null);
    }

    /**
     * Chọn ô trong Tileset theo hàng xóm (kiểu "autotile" đơn giản):
     *  - mặt trên lộ ra: ô cỏ; cả trên lẫn dưới lộ ra: ô cỏ mỏng có viền đáy
     *  - chỉ mặt dưới lộ ra: ô đất có viền đáy; còn lại: ô đất bên trong
     * Cột ô gốc lặp theo chu kỳ 4 để rễ cây và rêu nối liền nhau như trong Tileset.
     * Ngoài biên bản đồ coi như đất liền (trần và tường biên không mọc cỏ).
     */
    private static BufferedImage renderTiles(Level lv) {
        BufferedImage img = new BufferedImage(lv.cols * TILE, lv.rows * TILE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        boolean[][] s = lv.solid;
        for (int r = 0; r < lv.rows; r++) {
            for (int c = 0; c < lv.cols; c++) {
                if (!s[r][c]) continue;
                boolean top   = r > 0 && !s[r - 1][c];
                boolean bot   = r < lv.rows - 1 && !s[r + 1][c];
                boolean left  = c > 0 && !s[r][c - 1];
                boolean right = c < lv.cols - 1 && !s[r][c + 1];

                int sx = (c % 4) * SRC;
                int sy;
                if (top && bot) sy = 48;
                else if (top)   sy = 0;
                else if (bot)   sy = 32;
                else            sy = 16 + (r % 2) * SRC;

                int x = c * TILE, y = r * TILE;
                BufferedImage piece = Gfx.sub("tileset.png", sx, sy, SRC, SRC);
                if (piece != null) g.drawImage(piece, x, y, TILE, TILE, null);
                else { g.setColor(new Color(0x2D1927)); g.fillRect(x, y, TILE, TILE); }

                // Viền tối ở cạnh trái/phải lộ ra để khối đất tách khỏi nền
                g.setColor(OUTLINE);
                if (left)  g.fillRect(x, y + (top ? 10 : 0), 2, TILE - (top ? 10 : 0));
                if (right) g.fillRect(x + TILE - 2, y + (top ? 10 : 0), 2, TILE - (top ? 10 : 0));
            }
        }
        g.dispose();
        return img;
    }

    // ── Hồ nước / dung nham / chất độc khoét xuống mặt đất ───────────────

    private static BufferedImage poisonTex;

    /** Chất độc chưa có ảnh riêng: đổi màu ảnh nước sang xanh lá */
    private static BufferedImage poison() {
        if (poisonTex == null) {
            BufferedImage w = Gfx.img("water.png");
            if (w == null) return null;
            poisonTex = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < w.getHeight(); y++) {
                for (int x = 0; x < w.getWidth(); x++) {
                    int p = w.getRGB(x, y);
                    int a = p >>> 24, r = (p >> 16) & 255, gr = (p >> 8) & 255, b = p & 255;
                    int nr = (int) (gr * 0.55), ng = Math.min(255, (int) (b * 1.0)), nb = (int) (r * 0.4);
                    poisonTex.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
                }
            }
        }
        return poisonTex;
    }

    /**
     * Vùng nguy hiểm nằm ngay trên mặt đất (chạm là chết), nhưng hình vẽ khoét xuống
     * khối đất cho giống hồ: mặt chất lỏng thấp hơn mặt cỏ vài pixel. Ảnh 32x32 lặp ngang
     * và trôi chậm để mặt hồ trông như đang chảy.
     */
    static void pool(Graphics2D g, Zone z, int tick) {
        fw.common.game.Rect r = z.rect();
        // mặt đất
        int floor = Math.round(r.y + r.h);
        int x = Math.round(r.x), w = Math.round(r.w);
        // mép trên chất lỏng
        int surface = floor - 2;
        // độ sâu hồ (vẽ đè lên ô đất bên dưới)
        int depth = 26;

        BufferedImage tex = switch (z.kind) {
            case LAVA  -> Gfx.img("lava.png");
            case WATER -> Gfx.img("water.png");
            default    -> poison();
        };

        // Lòng hồ tối, rộng hơn mặt chất lỏng 2px mỗi bên làm bờ
        g.setColor(OUTLINE);
        g.fillRect(x - 2, surface - 1, w + 4, depth + 3);

        Shape oldClip = g.getClip();
        g.clipRect(x, surface, w, depth);
        if (tex != null) {
            int tw = tex.getWidth();
            // dung nham chảy chậm hơn nước
            int shift = (tick / (z.kind == ZoneKind.LAVA ? 6 : 4)) % tw;
            for (int tx = x - tw + shift; tx < x + w; tx += tw) {
                g.drawImage(tex, tx, surface, tw, depth, null);
            }
        }
        g.setClip(oldClip);

        // Ánh sáng hắt lên phía trên hồ (dung nham sáng hơn)
        Color glow = switch (z.kind) {
            case LAVA  -> new Color(255, 120, 20);
            case WATER -> new Color(60, 160, 255);
            default    -> new Color(130, 230, 60);
        };
        int a = z.kind == ZoneKind.LAVA ? 80 : 40;
        g.setPaint(new GradientPaint(0, floor - 20, new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 0),
                                     0, floor, new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), a)));
        g.fill(new Rectangle2D.Float(x, floor - 20, w, 20));
    }

    // ── Cửa ─────────────────────────────────────────────────────────────

    /** Ảnh cửa mở và đóng cắt về cùng một khung 96x121 để vòm đá trùng khít nhau */
    private static BufferedImage doorImg(boolean open) {
        return open ? Gfx.sub("door_opened.png", 9, 18, 96, 121)
                    : Gfx.sub("door_closed.png", 18, 18, 96, 121);
    }

    static void door(Graphics2D g, Level.DoorDef d, boolean open, int tick) {
        int x = (int) d.x, y = (int) d.y, w = (int) d.w, h = (int) d.h;
        int dh = h + 14;
        int dw = Math.round(dh * 96f / 121f);
        int dx = x + (w - dw) / 2, dy = y + h - dh;

        // quầng sáng ấm phía sau cửa đã mở
        if (open) {
            float pulse = (float) (0.75 + 0.25 * Math.sin(tick * 0.08));
            g.setColor(new Color(255, 220, 120, (int) (60 * pulse)));
            g.fill(new Ellipse2D.Float(dx - 6, dy - 4, dw + 12, dh + 6));
        }
        BufferedImage img = doorImg(open);
        if (img != null) {
            // ảnh chi tiết, thu nhỏ mượt (bilinear)
            g.drawImage(img, dx, dy, dw, dh, null);
        } else {
            g.setColor(open ? new Color(0xE7D5B3) : new Color(0x492329));
            g.fillRect(x, y, w, h);
        }
        // ổ khóa: cần chìa mới mở
        if (!open) {
            BufferedImage lock = Gfx.img("padlock_locked.png");
            if (lock != null) Gfx.draw(g, lock, x + w / 2 - 7, y + h / 2 - 6, 14, 21);
        }
    }

    // ── Chìa khóa ───────────────────────────────────────────────────────

    static void key(Graphics2D g, float x, float y, int tick) {
        float bob = (float) Math.sin(tick * 0.08) * 3;
        float cx = x + 12, cy = y + 12 + bob;
        g.setColor(new Color(255, 200, 80, 55));
        g.fill(new Ellipse2D.Float(cx - 16, cy - 18, 32, 36));
        BufferedImage k = Gfx.img("key.png");
        if (k != null) {
            Gfx.draw(g, k, Math.round(cx - k.getWidth() / 2f), Math.round(cy - k.getHeight() / 2f), k.getWidth(), k.getHeight());
        } else {
            g.setColor(new Color(0xE2B93B));
            g.fill(new Ellipse2D.Float(cx - 6, cy - 6, 12, 12));
        }
    }

    // ── Cần gạt: lever.png có 3 khung, tay gạt quay từ phải (bật) sang trái (tắt) ──

    /** angle: góc của thanh (độ), -45 = tắt (nghiêng trái), 45 = bật (nghiêng phải) */
    static void lever(Graphics2D g, Level.LeverDef d, double angle, boolean on) {
        // giữa cần gạt
        int cx = Math.round(d.x + 8);
        // mặt đất
        int baseY = Math.round(d.y + 24);
        // tâm ổ trục
        int sockY = baseY - 14;

        // Cột gỗ đỡ ổ trục
        g.setColor(OUTLINE);
        g.fillRect(cx - 3, sockY, 6, baseY - sockY);
        g.setColor(WOOD);
        g.fillRect(cx - 2, sockY, 4, baseY - sockY);
        g.setColor(WOOD_LT);
        g.fillRect(cx - 2, sockY, 1, baseY - sockY);

        // lever.png có 3 khung xếp dọc: 0 = bật, 1 = đang gạt, 2 = tắt
        int frame = (int) Math.round((45 - angle) / 45);
        frame = Math.max(0, Math.min(2, frame));
        BufferedImage f = Gfx.sub("lever.png", 0, frame * 64, 64, 64);
        if (f != null) {
            // phóng 2 lần như các ô đất
            Gfx.draw(g, f, cx - 64, sockY - 66, 128, 128);
        }
        // đèn nhỏ báo đang bật
        if (on) {
            g.setColor(new Color(255, 190, 90, 60));
            g.fill(new Ellipse2D.Float(cx - 22, sockY - 16, 44, 32));
        }
    }

    // ── Nút bấm (lún xuống khi có người/đá đè) ──────────────────────────

    static void plate(Graphics2D g, Level.PlateDef p, boolean on, int tick) {
        int floor = Math.round(p.y + p.h);
        int x = Math.round(p.x), w = Math.round(p.w);
        // Nút cam: cao 7px khi nhả, 2px khi bị đè
        int bh = on ? 2 : 7;
        Gfx.nine(g, Gfx.uiButtonMain(), 3, 1, x + 5, floor - 5 - bh, w - 10, bh + 5);
        // Đế tối
        Gfx.nine(g, Gfx.uiBar(), 3, 1, x - 3, floor - 6, w + 6, 8);
        if (on) {
            int a = (int) (60 + 30 * Math.sin(tick * 0.15));
            g.setColor(new Color(255, 170, 90, a / 2));
            g.fill(new Ellipse2D.Float(x - 2, floor - 14, w + 4, 14));
        }
    }

    // ── Cổng chắn (cột khung tối của UI) và thang máy (cầu gỗ của Tileset) ──

    static void gate(Graphics2D g, int x, int y, int w, int h) {
        Gfx.nine(g, Gfx.uiPillar(), 4, 2, x, y, w, h);
        // Đai gỗ ngang cho cột trông chắc chắn
        for (int yy = y + 14; yy < y + h - 12; yy += 24) {
            g.setColor(OUTLINE);
            g.fillRect(x + 4, yy, w - 8, 6);
            g.setColor(WOOD);
            g.fillRect(x + 5, yy + 1, w - 10, 4);
            g.setColor(WOOD_LT);
            g.fillRect(x + 5, yy + 1, w - 10, 1);
        }
    }

    static void elevator(Graphics2D g, int x, int y, int w, int h, Level.MechDef def) {
        // Đường ray mờ cho biết thang máy chạy tới đâu
        int rx = (int) def.x, ry = (int) def.y;
        int rw = def.axis == 'x' ? (int) (def.dist + w) : w;
        int rh = def.axis == 'y' ? (int) (def.dist + h) : h;
        g.setColor(new Color(255, 240, 210, 22));
        g.fillRect(Math.min(rx, rx + rw), Math.min(ry, ry + rh), Math.abs(rw), Math.abs(rh));

        // Hàng khúc gỗ của cây cầu trong Tileset (cao 8px), phóng 2 lần:
        // mép trái 12px, khúc gỗ 8px lặp lại, mép phải 12px
        int ph = Math.max(h, 12);
        BufferedImage left  = Gfx.sub("tileset.png", 112, 190, 12, 8);
        BufferedImage mid   = Gfx.sub("tileset.png", 124, 190, 8, 8);
        BufferedImage right = Gfx.sub("tileset.png", 164, 190, 12, 8);
        if (left == null) {
            g.setColor(WOOD);
            g.fillRect(x, y, w, ph);
            return;
        }
        Shape oldClip = g.getClip();
        g.clipRect(x, y, w, ph);
        Gfx.draw(g, left, x, y, 24, ph);
        for (int xx = x + 24; xx < x + w - 24; xx += 16) Gfx.draw(g, mid, xx, y, 16, ph);
        Gfx.draw(g, right, x + w - 24, y, 24, ph);
        g.setClip(oldClip);
    }

    // ── Khối đá đẩy được ────────────────────────────────────────────────

    static void rock(Graphics2D g, float x, float y) {
        BufferedImage r = Gfx.img("rock.png");
        int ix = Math.round(x), iy = Math.round(y);
        if (r != null) {
            Gfx.draw(g, r, ix, iy, 32, 32);
        } else {
            g.setColor(new Color(0x6A6542));
            g.fillRect(ix, iy, 32, 32);
        }
    }
}
