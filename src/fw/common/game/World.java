package fw.common.game;

import java.util.*;

/**
 * Thế giới game: toàn bộ trạng thái của 1 ván và phần mô phỏng vật lý theo từng tick.
 * Chỉ chạy ở server; cùng màn + cùng chuỗi input luôn cho kết quả giống hệt nhau.
 */
public class World {
    public enum Phase { RUN, WON, LOST }

    public Phase phase;
    public int tick;
    public final Player[] pl;
    public final List<Rock> rocks;
    public final List<Key> keys;
    public final List<Door> doors;
    public final List<Lever> levers;
    public final List<Plate> plates;
    public final List<Mech> mechs;
    public final Level lv;
    public final int lvId;

    // Dựng trạng thái ban đầu từ định nghĩa màn: 2 nhân vật ở điểm xuất phát và các vật thể
    public World(Level lv, int lvId, int accFire, int accWater) {
        this.lv = lv;
        this.lvId = lvId;
        this.phase = Phase.RUN;
        this.tick = 0;

        this.pl = new Player[2];
        this.pl[0] = new Player(accFire, Element.FIRE, lv.spawnFire.x, lv.spawnFire.y);
        this.pl[1] = new Player(accWater, Element.WATER, lv.spawnWater.x, lv.spawnWater.y);

        this.rocks = new ArrayList<>();
        for (Level.RockDef def : lv.rocks) {
            rocks.add(new Rock(def.id, def.x, def.y));
        }

        this.keys = new ArrayList<>();
        for (Level.KeyDef def : lv.keys) {
            keys.add(new Key(def.id, def.x, def.y));
        }

        this.doors = new ArrayList<>();
        for (Level.DoorDef def : lv.doors) {
            doors.add(new Door(def.id, def.x, def.y, def.w, def.h));
        }

        this.levers = new ArrayList<>();
        for (Level.LeverDef def : lv.levers) {
            levers.add(new Lever(def.id, def.x, def.y, def.target));
        }

        this.plates = new ArrayList<>();
        for (Level.PlateDef def : lv.plates) {
            plates.add(new Plate(def.id, def.x, def.y, def.w, def.h, def.target));
        }

        this.mechs = new ArrayList<>();
        for (Level.MechDef def : lv.elevators) {
            mechs.add(new Mech(def.id, MechKind.ELEV, def.x, def.y, def.w, def.h, def.axis, def.dist, def.speed));
        }
        for (Level.MechDef def : lv.gates) {
            mechs.add(new Mech(def.id, MechKind.GATE, def.x, def.y, def.w, def.h, def.axis, def.dist, def.speed));
        }
    }

    /**
     * Chạy 1 tick (1/60 giây) theo thứ tự cố định: cơ quan, người chơi, đá, nút, cần gạt,
     * chìa khóa, vùng nguy hiểm, cửa, kiểm tra thắng. Trả về các sự kiện để server gửi cho client.
     */
    public List<Ev> step(Input[] in) {
        List<Ev> events = new ArrayList<>();
        if (phase != Phase.RUN) {
            return events;
        }

        tick++;

        // 1. Cơ quan: lưu trạng thái chạy cũ, di chuyển, báo khi đổi giữa chạy/dừng
        for (Mech m : mechs) {
            m.saveMovingState();
        }
        stepMechs();
        for (Mech m : mechs) {
            if (m.movingChanged()) {
                events.add(new Ev("MECH_MOVING", m.id, m.moving));
            }
        }

        // 2. Người chơi (Lửa trước, Nước sau)
        for (int i = 0; i < 2; i++) {
            stepPlayer(pl[i], in[i]);
        }

        // 3. Đá
        for (Rock r : rocks) {
            stepRock(r);
        }

        // 4. Nút bấm: bật khi có người chơi hoặc đá đè lên
        for (Plate p : plates) {
            boolean wasOn = p.on;
            p.on = false;
            Rect pr = p.rect();
            for (Player player : pl) {
                if (player.alive && !player.done && player.rect().overlaps(pr)) {
                    p.on = true;
                    break;
                }
            }
            if (!p.on) {
                for (Rock r : rocks) {
                    if (r.rect().overlaps(pr)) {
                        p.on = true;
                        break;
                    }
                }
            }
            if (p.on != wasOn) {
                events.add(new Ev("PLATE_CHANGED", p.id, p.on));
            }
        }

        // 5. Cần gạt: đổi trạng thái khi vừa chạm vào (không phải đang chạm sẵn) và đã hết thời gian chờ
        for (Lever lv : levers) {
            if (lv.cd > 0) {
                lv.cd--;
            }
            boolean touching = false;
            Rect lr = lv.rect();
            for (Player player : pl) {
                if (player.alive && !player.done && player.rect().overlaps(lr)) {
                    touching = true;
                    break;
                }
            }
            if (touching && !lv.touch && lv.cd == 0) {
                lv.on = !lv.on;
                lv.cd = Phys.LEVER_CD;
                events.add(new Ev("LEVER_TOGGLED", lv.id, lv.on));
            }
            lv.touch = touching;
        }

        // 6. Chìa khóa: ai chạm trước thì lấy; cửa mở khi đã lấy hết chìa
        for (Key k : keys) {
            if (!k.got) {
                Rect kr = k.rect();
                for (Player p : pl) {
                    if (p.alive && !p.done && p.rect().overlaps(kr)) {
                        k.got = true;
                        k.by = p.acc;
                        events.add(new Ev("KEY_COLLECTED", k.id, p.acc));
                        break;
                    }
                }
            }
        }
        for (Door d : doors) {
            boolean allGot = true;
            for (Key k : keys) {
                if (!k.got) {
                    allGot = false;
                    break;
                }
            }
            d.open = allGot;
        }

        // 7. Vùng nguy hiểm: hitbox thu hẹp 2 bên, chạm vùng giết mình thì chết và thua màn
        for (Player p : pl) {
            if (p.alive && !p.done) {
                Rect hb = new Rect(p.x + Phys.HAZ_INSET, p.y, Phys.PW - 2 * Phys.HAZ_INSET, Phys.PH);
                for (Zone z : lv.zones) {
                    if (hb.overlaps(z.rect()) && kills(z.kind, p.el)) {
                        p.alive = false;
                        events.add(new Ev("PLAYER_DIED", p.acc, z.kind.name()));
                        if (phase == Phase.RUN) {
                            phase = Phase.LOST;
                            events.add(new Ev("LEVEL_FAILED", p.acc, "DEAD"));
                        }
                        break;
                    }
                }
            }
        }

        // 8. Vào cửa: tâm nhân vật nằm trong cửa đang mở; người cầm chìa vào trước, người kia vào sau
        for (Door d : doors) {
            if (d.open) {
                Rect dr = d.rect();
                for (Player p : pl) {
                    if (p.alive && !p.done) {
                        float cx = p.x + Phys.PW / 2;
                        float cy = p.y + Phys.PH / 2;
                        if (cx >= dr.x && cx < dr.x + dr.w && cy >= dr.y && cy < dr.y + dr.h) {
                            int holder = keys.get(0).by;
                            if (p.acc == holder || d.passed.contains(holder)) {
                                p.done = true;
                                d.passed.add(p.acc);
                                events.add(new Ev("DOOR_PASSED", d.id, p.acc));
                            }
                        }
                    }
                }
            }
        }

        // 9. Thắng khi cả 2 đã vào cửa
        if (phase == Phase.RUN && pl[0].done && pl[1].done) {
            phase = Phase.WON;
            events.add(new Ev("LEVEL_COMPLETE", sec()));
        }

        return events;
    }

    public double sec() {
        return Math.round(tick * 100.0 / 60) / 100.0;
    }

    // Vùng nào giết ai: độc giết cả hai, nước giết Lửa, dung nham giết Nước
    private boolean kills(ZoneKind kind, Element el) {
        if (kind == ZoneKind.POISON) return true;
        if (kind == ZoneKind.WATER) return el == Element.FIRE;
        if (kind == ZoneKind.LAVA) return el == Element.WATER;
        return false;
    }

    /**
     * Thang máy và cổng chắn: được kích hoạt khi có cần gạt đang bật hoặc nút đang bị đè trỏ tới,
     * tiến độ p tiến dần về 1 (bật) hoặc 0 (tắt). Thử di chuyển cả cơ quan lẫn vật đứng trên nó trước:
     * bị chặn ở đâu thì cả nhóm đứng chờ, không đè lên người.
     */
    private void stepMechs() {
        for (Mech m : mechs) {
            boolean active = false;
            for (Lever lv : levers) {
                if (lv.target.equals(m.id) && lv.on) {
                    active = true;
                    break;
                }
            }
            if (!active) {
                for (Plate p : plates) {
                    if (p.target.equals(m.id) && p.on) {
                        active = true;
                        break;
                    }
                }
            }

            float target = active ? 1 : 0;
            if (Math.abs(m.p - target) < Phys.EPS) {
                m.moving = false;
                continue;
            }

            float step = m.speed * Phys.DT / Math.abs(m.dist);
            float np = m.p;
            if (target > m.p) {
                np = Math.min(m.p + step, target);
            } else {
                np = Math.max(m.p - step, target);
            }

            float d = (np - m.p) * m.dist;
            List<Body> riders = ridersOf(m);
            Set<Body> grp = new HashSet<>();
            grp.add(m);
            grp.addAll(riders);

            boolean ok = true;
            float actualMove = m.axis == 'x' ? sweepX(m.rect(), d, grp) : sweepY(m.rect(), d, grp);
            if (Math.abs(actualMove) < Math.abs(d) - Phys.EPS) {
                ok = false;
            }

            if (ok) {
                for (Body rider : riders) {
                    Rect rr = rider.rect();
                    float rMove = m.axis == 'x' ? sweepX(rr, d, grp) : sweepY(rr, d, grp);
                    if (Math.abs(rMove) < Math.abs(d) - Phys.EPS) {
                        ok = false;
                        break;
                    }
                }
            }

            if (!ok) {
                m.moving = false;
                continue;
            }

            if (m.axis == 'x') {
                for (Body b : grp) {
                    b.moveX(d);
                }
            } else {
                for (Body b : grp) {
                    b.moveY(d);
                }
            }

            m.p = np;
            m.moving = true;
        }
    }

    /**
     * Nhân vật: input thành vận tốc ngang, nhảy khi đang đứng trên mặt đỡ, cộng trọng lực rồi di chuyển.
     * Chạm đất hoặc đụng trần thì đặt lại vận tốc dọc về 0.
     */
    private void stepPlayer(Player p, Input in) {
        if (!p.alive || p.done) return;

        int dir = (in.r() ? 1 : 0) - (in.l() ? 1 : 0);
        p.vx = dir * Phys.MOVE_V;
        if (dir != 0) {
            p.face = dir;
        }

        p.onGround = sweepY(p.rect(), Phys.PROBE, Set.of(p)) < Phys.PROBE - Phys.EPS;

        // Đang có người đứng trên đầu thì không nhảy được (phải để người trên tự nhảy lên)
        if (in.j() && !p.jHeld && p.onGround && !hasPlayerOnHead(p)) {
            p.vy = -Phys.JUMP_V;
        }
        p.jHeld = in.j();

        p.vy = Math.min(p.vy + Phys.GRAV * Phys.DT, Phys.MAXFALL);

        MoveResult res = moveGroup(p, p.vx * Phys.DT, p.vy * Phys.DT, true);
        float dyWant = p.vy * Phys.DT;
        if (dyWant > 0 && res.ay < dyWant - Phys.EPS) {
            p.vy = 0;
        }
        if (dyWant < 0 && Math.abs(res.ay) < Math.abs(dyWant) - Phys.EPS) {
            p.vy = 0;
        }
    }

    // Đá chỉ tự rơi theo trọng lực; việc đẩy ngang do walkX của người chơi xử lý
    private void stepRock(Rock r) {
        // Quãng rơi trong 1 tick, không vượt tốc độ rơi tối đa
        float dy = Phys.GRAV * Phys.DT * Phys.DT;
        if (dy > Phys.MAXFALL * Phys.DT) {
            dy = Phys.MAXFALL * Phys.DT;
        }

        MoveResult res = moveGroup(r, 0, dy, false);
        // Chạm đất thì dừng: không nảy, không lưu vận tốc
    }

    /** Có người chơi nào đang đứng trên đầu p không */
    private boolean hasPlayerOnHead(Player p) {
        for (Body r : ridersOf(p)) {
            if (r instanceof Player) return true;
        }
        return false;
    }

    /**
     * Di chuyển vật b cùng các vật đang đứng trên nó.
     * Ngang: b đi trước (người chơi dùng walkX để đẩy đá), đá/thang máy chở theo vật bên trên.
     * Dọc: đi lên thì cả nhóm dừng theo thành viên bị chặn sớm nhất, đi xuống thì chỉ xét b.
     * Trả về quãng đường thực tế đã đi theo 2 trục.
     */
    private MoveResult moveGroup(Body b, float dx, float dy, boolean walk) {
        List<Body> riders = ridersOf(b);
        Set<Body> grp = new HashSet<>();
        grp.add(b);
        grp.addAll(riders);

        float ax = walk ? walkX(b, dx, grp) : sweepX(b.rect(), dx, grp);
        b.moveX(ax);
        // Đá và thang máy chở vật trên nó theo chiều ngang. Người chơi thì KHÔNG:
        // người bên dưới bước đi thì người đứng trên đầu bị hụt chân và rơi xuống.
        if (!(b instanceof Player)) {
            for (Body r : riders) {
                float rax = sweepX(r.rect(), ax, grp);
                r.moveX(rax);
            }
        }

        float ay;
        if (dy < 0) {
            ay = 0;
            for (Body m : grp) {
                float my = sweepY(m.rect(), dy, grp);
                if (ay == 0 || Math.abs(my) < Math.abs(ay)) {
                    ay = my;
                }
            }
        } else {
            ay = sweepY(b.rect(), dy, grp);
        }

        for (Body m : grp) {
            m.moveY(ay);
        }

        return new MoveResult(ax, ay);
    }

    /**
     * Người chơi đi ngang: đi tới sát viên đá gần nhất trên đường, phần quãng còn lại dùng để đẩy đá.
     * Đá bị chặn (tường, đá khác, người kia) thì người cũng đứng lại.
     */
    private float walkX(Body b, float dx, Set<Body> grp) {
        if (Math.abs(dx) < Phys.EPS) return 0;

        float s = dx > 0 ? 1 : -1;

        // Quét bỏ qua mọi viên đá: người chơi chỉ bị tường, cơ quan và người kia chặn
        Set<Body> exRocks = new HashSet<>(grp);
        exRocks.addAll(rocks);
        float a = sweepX(b.rect(), dx, exRocks);

        // Tìm viên đá gần nhất nằm trên đường đi
        Rock rock = null;
        float gap = Float.MAX_VALUE;

        Rect br = b.rect();
        float bc = br.x + br.w / 2;
        for (Rock r : rocks) {
            if (grp.contains(r)) continue;
            Rect rr = r.rect();
            // Chồng nhau theo chiều dọc thật sự (> 1 px): đứng trên nóc đá thì không tính là đẩy
            if (!(br.y < rr.y + rr.h - 1 && br.y + br.h > rr.y + 1)) continue;
            // Đá phải nằm phía trước theo hướng đi (so tâm)
            if (s * (rr.x + rr.w / 2 - bc) <= 0) continue;

            // Khoảng hở tới đá. Khi đang áp sát, sai số số thực có thể làm g hơi âm:
            // vẫn tính là đá chắn đường (trước đây bỏ qua nên người đi xuyên vào đá).
            float g = s > 0 ? rr.x - (br.x + br.w) : br.x - (rr.x + rr.w);
            if (g > Math.abs(a)) continue;

            if (g < gap) {
                gap = g;
                rock = r;
            }
        }

        // Không có đá cản đường
        if (rock == null) {
            return a;
        }

        // Đẩy đá chậm hơn đi bộ; đá dịch được bao nhiêu thì người đi theo bấy nhiêu.
        // gap âm (đang lấn vào đá) thì người lùi lại đúng phần lấn, tự về sát mép đá.
        float pushDist = (Math.abs(a) - Math.max(0, gap)) * Phys.PUSH_K;
        MoveResult rockRes = moveGroup(rock, s * pushDist, 0, false);
        float moved = Math.abs(rockRes.ax);

        return s * (gap + moved);
    }

    private boolean yOverlap(Rect a, Rect b) {
        return a.y < b.y + b.h && a.y + a.h > b.y;
    }

    /**
     * Các vật đang đứng trên b: đáy vật chạm đỉnh b, chồng nhau theo chiều ngang, người chơi không đang bay lên.
     * Lấy cả vật đứng trên vật đó (đệ quy), sắp theo y tăng dần.
     */
    private List<Body> ridersOf(Body b) {
        List<Body> result = new ArrayList<>();
        Rect br = b.rect();

        for (Player p : pl) {
            if (!p.alive || p.done || p == b) continue;
            if (Math.abs((p.y + Phys.PH) - br.y) <= Phys.RIDE_EPS && p.vy >= 0) {
                Rect pr = p.rect();
                if (pr.x < br.x + br.w && pr.x + pr.w > br.x) {
                    result.add(p);
                }
            }
        }

        for (Rock r : rocks) {
            if (r == b) continue;
            // Rock không lưu vận tốc (luôn chỉ rơi theo trọng lực), nên chỉ cần xét khoảng cách
            if (Math.abs((r.y + Phys.ROCK) - br.y) <= Phys.RIDE_EPS) {
                Rect rr = r.rect();
                if (rr.x < br.x + br.w && rr.x + rr.w > br.x) {
                    result.add(r);
                }
            }
        }

        List<Body> transitive = new ArrayList<>(result);
        for (Body rider : result) {
            for (Body r2 : ridersOf(rider)) {
                if (!transitive.contains(r2)) {
                    transitive.add(r2);
                }
            }
        }

        transitive.sort(Comparator.comparingDouble(a -> a.rect().y));
        return transitive;
    }

    /**
     * Quét theo trục X: quãng đi được lớn nhất (không quá |dx|, cùng dấu dx) trước khi chạm vật rắn.
     * Xét người chơi, đá, cơ quan (trừ các vật trong ex) và ô tường có khoảng y chồng với r.
     */
    private float sweepX(Rect r, float dx, Set<Body> ex) {
        if (Math.abs(dx) < Phys.EPS) return 0;

        float limit = Math.abs(dx);
        float sign = dx > 0 ? 1 : -1;

        for (Body solid : allSolids()) {
            if (ex.contains(solid)) continue;
            Rect sr = solid.rect();
            if (!yOverlap(r, sr)) continue;

            if (sign > 0) {
                if (sr.x >= r.x + r.w - Phys.EPS) {
                    float gap = sr.x - (r.x + r.w);
                    if (gap < limit) {
                        limit = gap;
                    }
                }
            } else {
                if (sr.x + sr.w <= r.x + Phys.EPS) {
                    float gap = r.x - (sr.x + sr.w);
                    if (gap < limit) {
                        limit = gap;
                    }
                }
            }
        }

        for (int row = 0; row < lv.rows; row++) {
            for (int col = 0; col < lv.cols; col++) {
                if (lv.solid[row][col]) {
                    Rect tile = new Rect(col * 32, row * 32, 32, 32);
                    if (!yOverlap(r, tile)) continue;

                    if (sign > 0) {
                        if (tile.x >= r.x + r.w - Phys.EPS) {
                            float gap = tile.x - (r.x + r.w);
                            if (gap < limit) {
                                limit = gap;
                            }
                        }
                    } else {
                        if (tile.x + tile.w <= r.x + Phys.EPS) {
                            float gap = r.x - (tile.x + tile.w);
                            if (gap < limit) {
                                limit = gap;
                            }
                        }
                    }
                }
            }
        }

        return sign * Math.max(0, limit);
    }

    // Quét theo trục Y, cách làm giống sweepX nhưng xét các vật có khoảng x chồng với r
    private float sweepY(Rect r, float dy, Set<Body> ex) {
        if (Math.abs(dy) < Phys.EPS) return 0;

        float limit = Math.abs(dy);
        float sign = dy > 0 ? 1 : -1;

        for (Body solid : allSolids()) {
            if (ex.contains(solid)) continue;
            Rect sr = solid.rect();
            if (!xOverlap(r, sr)) continue;

            if (sign > 0) {
                if (sr.y >= r.y + r.h - Phys.EPS) {
                    float gap = sr.y - (r.y + r.h);
                    if (gap < limit) {
                        limit = gap;
                    }
                }
            } else {
                if (sr.y + sr.h <= r.y + Phys.EPS) {
                    float gap = r.y - (sr.y + sr.h);
                    if (gap < limit) {
                        limit = gap;
                    }
                }
            }
        }

        for (int row = 0; row < lv.rows; row++) {
            for (int col = 0; col < lv.cols; col++) {
                if (lv.solid[row][col]) {
                    Rect tile = new Rect(col * 32, row * 32, 32, 32);
                    if (!xOverlap(r, tile)) continue;

                    if (sign > 0) {
                        if (tile.y >= r.y + r.h - Phys.EPS) {
                            float gap = tile.y - (r.y + r.h);
                            if (gap < limit) {
                                limit = gap;
                            }
                        }
                    } else {
                        if (tile.y + tile.h <= r.y + Phys.EPS) {
                            float gap = r.y - (tile.y + tile.h);
                            if (gap < limit) {
                                limit = gap;
                            }
                        }
                    }
                }
            }
        }

        return sign * Math.max(0, limit);
    }

    private boolean xOverlap(Rect a, Rect b) {
        return a.x < b.x + b.w && a.x + a.w > b.x;
    }

    // Các vật rắn di động: người chơi còn trong màn, đá, cơ quan (ô tường được xét riêng)
    private List<Body> allSolids() {
        List<Body> solids = new ArrayList<>();
        for (Player p : pl) {
            if (p.alive && !p.done) {
                solids.add(p);
            }
        }
        solids.addAll(rocks);
        solids.addAll(mechs);
        return solids;
    }

    private static class MoveResult {
        final float ax, ay;
        MoveResult(float ax, float ay) {
            this.ax = ax;
            this.ay = ay;
        }
    }
}
