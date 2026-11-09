package fw.client.render;

import fw.client.Model;
import fw.client.ui.Theme;
import fw.common.game.Level;
import fw.common.game.Snap;
import fw.common.game.Zone;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vẽ màn chơi từ snapshot UDP mà server gửi (30 lần/giây), khung hình vẽ 60 lần/giây.
 *
 * Mỗi frame:
 *   1. Lấy snapshot mới nhất (Interp) và màn chơi (Model.game.level)
 *   2. So sánh với snapshot trước để phát hiện sự kiện -> hạt hiệu ứng + âm thanh
 *   3. Vẽ theo lớp: tường -> khối đá -> hồ -> vật thể -> nhân vật -> hạt -> thanh trên cùng
 * Hình dáng từng vật thể nằm trong ScenePainter.
 */
public class GamePanel extends JPanel {
    private static final int PW = 24;
    private static final int PH = 30;
    /** Độ phóng sprite nhân vật (4 px ảnh -> 5 px màn hình): thân cao ~25 px vừa khung 30 px */
    private static final float SPRITE_SCALE = 1.25f;

    private final Map<Integer, PlayerAnimations> playerAnims = new HashMap<>();
    private final ParticleSystem particleSystem = new ParticleSystem();
    private final SoundManager soundManager = new SoundManager();
    private int animTick = 0;

    // Trạng thái snapshot trước: server không gửi sự kiện qua UDP, client tự so sánh 2 snapshot liên tiếp
    private final Map<Integer, PlayerState> prevPlayers = new HashMap<>();
    private final Map<String, Boolean> prevKeys   = new HashMap<>();
    private final Map<String, Boolean> prevDoors  = new HashMap<>();
    private final Map<String, Boolean> prevLevers = new HashMap<>();
    private final Map<String, Boolean> prevPlates = new HashMap<>();
    private final Map<String, Float>   prevMechP  = new HashMap<>();
    private final Map<String, Boolean> mechMoving = new HashMap<>();
    // góc thanh gạt đang vẽ (để gạt mượt)
    private final Map<String, Double>  leverAngle = new HashMap<>();
    private int lastTick = -1;
    private int lastSnapTick = -1;
    // frame này có snapshot mới không (snapshot 30Hz, vẽ 60fps)
    private boolean newSnap;
    // đã có ai lấy chìa chưa (hiện trên thanh trên cùng)
    private boolean keyGot;

    /** Trạng thái tối thiểu của 1 người chơi trong snapshot trước */
    private record PlayerState(float x, float y, float dx, float dy, boolean alive, boolean done) {}

    /**
     * Một animation kèm điểm neo trong ảnh gốc (px):
     * ax = tâm thân theo chiều ngang, footY = mép dưới bàn chân.
     * Điểm neo được đặt trùng giữa-đáy khung va chạm của người chơi.
     */
    private record Clip(Animation anim, float ax, int footY) {}

    /**
     * Bộ animation của 1 nhân vật, ảnh trong gfx/char/<tên>_*.png (bản gốc hướng mặt sang phải):
     *   idle  24 khung 30x30 | run 10 khung 30x30
     *   jump  28 khung 30x30: 1-12 bay lên, 14-25 rơi xuống
     *   death 20 khung 40x50 (khung cuối trống)
     */
    private static class PlayerAnimations {
        final Clip idle, run, rise, fall, death;
        Clip current;

        PlayerAnimations(String who) {
            idle  = new Clip(new Animation(strip(who + "_idle.png",  30, 30, 0, 24), 5, true),  14.5f, 28);
            run   = new Clip(new Animation(strip(who + "_run.png",   30, 30, 0, 10), 4, true),  14.5f, 28);
            rise  = new Clip(new Animation(strip(who + "_jump.png",  30, 30, 1, 13), 4, true),  14.5f, 28);
            fall  = new Clip(new Animation(strip(who + "_jump.png",  30, 30, 14, 26), 4, true), 14.5f, 28);
            death = new Clip(new Animation(strip(who + "_death.png", 40, 50, 0, 19), 4, false), 19.5f, 48);
            current = idle;
        }

        /** Cắt các khung [from, to) của dải ảnh 1 hàng; thiếu ảnh thì trả mảng rỗng */
        private static BufferedImage[] strip(String file, int fw, int fh, int from, int to) {
            BufferedImage img = Gfx.img("char/" + file);
            if (img == null) return new BufferedImage[0];
            SpriteSheet sheet = new SpriteSheet(img, fw, fh);
            BufferedImage[] frames = new BufferedImage[to - from];
            for (int i = 0; i < frames.length; i++) frames[i] = sheet.getFrame(from + i);
            return frames;
        }

        /** Ưu tiên: chết > đang bay (lên / xuống) > đang chạy > đứng yên */
        void update(float dx, float dy, boolean inAir, boolean alive) {
            Clip next = !alive ? death
                      : inAir ? (dy < 0 ? rise : fall)
                      : Math.abs(dx) > 0.1 ? run : idle;
            if (next != current) {
                current = next;
                current.anim().reset();
            }
            current.anim().update();
        }
    }

    public GamePanel() {
        setOpaque(true);
        setBackground(Theme.NIGHT);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1024, 576);
    }

    // ── Vẽ 1 frame ──────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        animTick++;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        Level level = Model.game != null ? Model.game.level : null;
        Snap.SnapData snap = Interp.get();
        int w = level != null ? level.cols * ScenePainter.TILE : 1024;
        int h = level != null ? level.rows * ScenePainter.TILE : 576;

        if (snap != null) {
            // Tick bị lùi lại => ván mới / màn mới: xóa trạng thái cũ để không phát sự kiện giả
            if (snap.tick < lastTick) resetState();
            lastTick = snap.tick;
            newSnap = snap.tick != lastSnapTick;
            lastSnapTick = snap.tick;
        }
        particleSystem.update();

        // Căn màn chơi vào giữa panel
        g.translate(Math.max(0, (getWidth() - w) / 2), Math.max(0, (getHeight() - h) / 2));
        ScenePainter.background(g, w, h, Gfx.themeOf(Model.game != null ? Model.game.lv : 1));
        if (level != null) {
            ScenePainter.tiles(g, level);
            drawPools(g, level);
        }
        if (snap != null && level != null) {
            drawDoorsAndKeys(g, snap, level);
            drawLevers(g, snap, level);
            drawPlates(g, snap, level);
            drawMechs(g, snap.elevs, level.elevators, false);
            drawMechs(g, snap.gates, level.gates, true);
            drawRocks(g, snap);
            drawPlayers(g, snap);
        }
        particleSystem.render(g);
        drawTopBar(g, snap, w);
        g.dispose();
    }

    private void resetState() {
        prevPlayers.clear();
        prevKeys.clear();
        prevDoors.clear();
        prevLevers.clear();
        prevPlates.clear();
        prevMechP.clear();
        mechMoving.clear();
        leverAngle.clear();
        lastSnapTick = -1;
        playerAnims.clear();
        particleSystem.clear();
    }

    /** true nếu giá trị chuyển từ false -> true so với snapshot trước */
    private static boolean turnedOn(Map<String, Boolean> prev, String id, boolean now) {
        Boolean before = prev.put(id, now);
        return before != null && !before && now;
    }

    // ── Hồ nước / dung nham / chất độc ──────────────────────────────────

    private void drawPools(Graphics2D g, Level level) {
        if (level.zones == null) return;
        for (Zone z : level.zones) {
            ScenePainter.pool(g, z, animTick);
            Color c = switch (z.kind) {
                case LAVA   -> new Color(255, 140, 30);
                case WATER  -> new Color(120, 200, 255);
                case POISON -> new Color(160, 240, 90);
            };
            particleSystem.addAmbient(z.rect().x, z.rect().y + z.rect().h - 6, z.rect().w, 6, c);
        }
    }

    // ── Cửa + chìa khóa ─────────────────────────────────────────────────

    private void drawDoorsAndKeys(Graphics2D g, Snap.SnapData snap, Level level) {
        if (snap.doors != null && level.doors != null) {
            for (JsonElement el : snap.doors) {
                JsonObject dj = el.getAsJsonObject();
                String id = dj.get("id").getAsString();
                boolean open = dj.get("open").getAsBoolean();
                Level.DoorDef def = find(level.doors, id);
                if (def == null) continue;
                if (turnedOn(prevDoors, id, open)) {
                    particleSystem.addSparkle(def.x + def.w / 2, def.y + def.h / 2, 20);
                    soundManager.play("door");
                }
                ScenePainter.door(g, def, open, animTick);
            }
        }
        keyGot = false;
        if (snap.keys != null && level.keys != null) {
            for (JsonElement el : snap.keys) {
                JsonObject kj = el.getAsJsonObject();
                String id = kj.get("id").getAsString();
                boolean got = kj.get("got").getAsBoolean();
                keyGot |= got;
                Level.KeyDef def = find(level.keys, id);
                if (def == null) continue;
                if (turnedOn(prevKeys, id, got)) {
                    particleSystem.addSparkle(def.x + 12, def.y + 12, 18);
                    soundManager.play("key");
                }
                if (!got) ScenePainter.key(g, def.x, def.y, animTick);
            }
        }
    }

    // ── Cần gạt: thanh đổ từ bên này sang bên kia ───────────────────────

    private void drawLevers(Graphics2D g, Snap.SnapData snap, Level level) {
        if (snap.levers == null || level.levers == null) return;
        for (JsonElement el : snap.levers) {
            JsonObject lj = el.getAsJsonObject();
            String id = lj.get("id").getAsString();
            boolean on = lj.get("on").getAsBoolean();
            Boolean before = prevLevers.put(id, on);
            // gạt cả 2 chiều đều kêu
            if (before != null && before != on) soundManager.play("lever");
            Level.LeverDef def = find(level.levers, id);
            if (def == null) continue;
            // Thanh quay dần về góc đích (tắt = nghiêng trái 45°, bật = nghiêng phải 45°)
            double target = on ? 45 : -45;
            double cur = leverAngle.getOrDefault(id, target);
            cur += Math.max(-9, Math.min(9, target - cur));
            leverAngle.put(id, cur);
            ScenePainter.lever(g, def, cur, on);
        }
    }

    // ── Nút bấm ─────────────────────────────────────────────────────────

    private void drawPlates(Graphics2D g, Snap.SnapData snap, Level level) {
        if (snap.plates == null || level.plates == null) return;
        for (JsonElement el : snap.plates) {
            JsonObject pj = el.getAsJsonObject();
            String id = pj.get("id").getAsString();
            boolean on = pj.get("on").getAsBoolean();
            if (turnedOn(prevPlates, id, on)) soundManager.play("lever");
            Level.PlateDef def = find(level.plates, id);
            if (def != null) ScenePainter.plate(g, def, on, animTick);
        }
    }

    // ── Thang máy / cổng chắn: snapshot chỉ gửi tiến độ p (0..1) ────────

    private void drawMechs(Graphics2D g, JsonArray arr, List<Level.MechDef> defs, boolean isGate) {
        if (arr == null || defs == null) return;
        for (JsonElement el : arr) {
            JsonObject mj = el.getAsJsonObject();
            String id = mj.get("id").getAsString();
            float p = mj.get("p").getAsFloat();

            // Chỉ xét "bắt đầu chạy" khi có snapshot mới, tránh dừng/chạy giả giữa 2 frame
            if (newSnap) {
                Float before = prevMechP.put(id, p);
                boolean moving = before != null && Math.abs(p - before) > 1e-4f;
                Boolean wasMoving = mechMoving.put(id, moving);
                if (moving && !Boolean.TRUE.equals(wasMoving)) soundManager.play("mech");
            }

            Level.MechDef def = find(defs, id);
            if (def == null) continue;
            // Vị trí thật = vị trí gốc + p * quãng đường (giống Mech.x()/y() ở server)
            int x = (int) (def.x + (def.axis == 'x' ? p * def.dist : 0));
            int y = (int) (def.y + (def.axis == 'y' ? p * def.dist : 0));
            if (isGate) ScenePainter.gate(g, x, y, (int) def.w, (int) def.h);
            else        ScenePainter.elevator(g, x, y, (int) def.w, (int) def.h, def);
        }
    }

    private void drawRocks(Graphics2D g, Snap.SnapData snap) {
        if (snap.rocks == null) return;
        for (JsonElement el : snap.rocks) {
            JsonObject r = el.getAsJsonObject();
            ScenePainter.rock(g, r.get("x").getAsFloat(), r.get("y").getAsFloat());
        }
    }

    // ── Nhân vật (snapshot chỉ có: acc, x, y, face, air, alive, done) ───

    private void drawPlayers(Graphics2D g, Snap.SnapData snap) {
        if (snap.players == null) return;
        for (JsonElement el : snap.players) {
            JsonObject p = el.getAsJsonObject();
            int acc       = p.get("acc").getAsInt();
            float x       = p.get("x").getAsFloat();
            float y       = p.get("y").getAsFloat();
            int face      = p.has("face") ? p.get("face").getAsInt() : 1;
            boolean inAir = p.has("air") && p.get("air").getAsBoolean();
            boolean alive = p.get("alive").getAsBoolean();
            boolean done  = p.get("done").getAsBoolean();

            String el2 = Model.elementOf(acc);
            boolean isFire = !"WATER".equals(el2);
            Color tint = isFire ? Theme.FIRE : Theme.WATER;

            // So sánh với snapshot trước: chết / về đích
            PlayerState prev = prevPlayers.get(acc);
            float dx = prev == null ? 0 : newSnap ? x - prev.x() : prev.dx();
            float dy = prev == null ? 0 : newSnap ? y - prev.y() : prev.dy();
            if (prev != null) {
                if (prev.alive() && !alive) {
                    particleSystem.addExplosion(x + PW / 2f, y + PH / 2f, tint, 25);
                    soundManager.play("death");
                }
                if (!prev.done() && done) {
                    particleSystem.addSparkle(x + PW / 2f, y + PH / 2f, 25);
                    soundManager.play("win");
                }
            }
            prevPlayers.put(acc, new PlayerState(x, y, dx, dy, alive, done));
            // đã vào cửa thì không vẽ nữa
            if (done) continue;

            PlayerAnimations anim = playerAnims.computeIfAbsent(acc,
                k -> new PlayerAnimations(isFire ? "fireboy" : "watergirl"));
            anim.update(dx, dy, inAir, alive);

            // quầng sáng quanh nhân vật
            if (alive) {
                int a = (int) (40 + 20 * Math.sin(animTick * 0.15));
                g.setColor(Theme.alpha(tint, a));
                g.fillOval((int) x - 6, (int) y - 6, PW + 12, PH + 12);
            }
            drawSprite(g, anim.current, x, y, face < 0, tint);
        }
    }

    /** Vẽ khung hiện tại của clip, neo giữa-đáy vào khung va chạm; thiếu ảnh thì vẽ khối màu */
    private static void drawSprite(Graphics2D g, Clip clip, float x, float y, boolean flip, Color tint) {
        BufferedImage frame = clip.anim().getCurrentFrame();
        if (frame == null) {
            g.setColor(tint);
            g.fillRoundRect((int) x, (int) y, PW, PH, 8, 8);
            return;
        }
        int fw = frame.getWidth(), fh = frame.getHeight();
        int dw = Math.round(fw * SPRITE_SCALE), dh = Math.round(fh * SPRITE_SCALE);
        // lật ngang thì điểm neo cũng lật theo
        float ax = flip ? fw - clip.ax() : clip.ax();
        int sx = Math.round(x + PW / 2f - ax * SPRITE_SCALE);
        int sy = Math.round(y + PH - clip.footY() * SPRITE_SCALE);

        Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        if (flip) g.drawImage(frame, sx, sy, sx + dw, sy + dh, fw, 0, 0, fh, null);
        else      g.drawImage(frame, sx, sy, sx + dw, sy + dh, 0, 0, fw, fh, null);
        if (old != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
    }

    // ── Thanh trên cùng: tên màn, trạng thái chìa khóa, đồng hồ ─────────

    private void drawTopBar(Graphics2D g, Snap.SnapData snap, int w) {
        // Khung tối của bộ UI Lost Lamb, nằm đè lên hàng tường trên cùng
        int bx = 6, by = 3, bw = w - 12, bh = 28;
        Gfx.nine(g, Gfx.uiPanel(), 5, 1, bx, by, bw, bh);

        g.setFont(Theme.display(16));
        g.setColor(Theme.GILT);
        g.drawString(levelName(), bx + 12, by + 19);

        // Giữa: biểu tượng ổ khóa (đóng/mở) + chìa khóa + dòng nhắc
        String keyText = keyGot ? "Đã có chìa khóa, vào cửa thôi" : "Tìm chìa khóa để mở cửa";
        g.setFont(Theme.body(13));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(keyText);
        int iconW = 16 + 4 + 9 + 8;
        int sx = (w - tw - iconW) / 2;
        Gfx.draw(g, Gfx.uiIcon(keyGot ? 3 : 2, 3), sx, by + 6, 16, 16);
        java.awt.image.BufferedImage key = Gfx.img("key.png");
        if (key != null) {
            Composite old = g.getComposite();
            if (!keyGot) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            Gfx.draw(g, key, sx + 20, by + 4, 6, 20);
            g.setComposite(old);
        }
        g.setColor(keyGot ? Theme.GILT : Theme.MUTED);
        g.drawString(keyText, sx + iconW, by + 18);

        if (snap != null) {
            int sec = snap.tick / 60;
            String time = String.format("%02d:%02d", sec / 60, sec % 60);
            g.setFont(Theme.display(18));
            fm = g.getFontMetrics();
            g.setColor(Theme.PARCHMENT);
            g.drawString(time, bx + bw - fm.stringWidth(time) - 12, by + 20);
        }
    }

    private static String levelName() {
        if (Model.game == null) return "";
        for (Model.LevelInfo info : Model.levels) {
            if (info.lv() == Model.game.lv) return info.name();
        }
        return Model.game.level != null ? Model.game.level.name : "";
    }

    // ── Tiện ích: tìm định nghĩa vật thể theo id ────────────────────────

    private static <T> T find(List<T> defs, String id) {
        for (T d : defs) {
            String did = switch (d) {
                case Level.DoorDef x  -> x.id;
                case Level.KeyDef x   -> x.id;
                case Level.LeverDef x -> x.id;
                case Level.PlateDef x -> x.id;
                case Level.MechDef x  -> x.id;
                default -> null;
            };
            if (id.equals(did)) return d;
        }
        return null;
    }
}
