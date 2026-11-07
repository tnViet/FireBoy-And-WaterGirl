package fw.server.svc;

import fw.common.game.Element;
import fw.common.game.Level;
import fw.common.game.LevelLoader;
import fw.common.game.World;
import fw.common.net.Const;
import fw.common.net.Err;
import fw.common.net.Msg;
import fw.common.net.MsgType;
import fw.common.util.Log;
import fw.server.db.LevelDao;
import fw.server.db.ResultDao;
import fw.server.net.Online;
import fw.server.net.Sess;
import fw.server.net.UdpServer;
import com.google.gson.JsonObject;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Vòng đời 1 trận: START_GAME -> LOADING (chờ 2 người gửi UDP_HELLO) -> RUNNING (Loop chạy 60 tick/giây)
 * -> kết thúc (thắng, thua, hoặc hủy). Mọi thay đổi trạng thái phòng đều nằm trong khóa synchronized(room).
 */
public final class Match {
    private static final int HELLO_WAIT_MS = 5000;

    private static ScheduledExecutorService sched;
    private static ExecutorService dbExec;

    private Match() {}

    public static void init(ScheduledExecutorService scheduler, ExecutorService dbExecutor) {
        sched = scheduler;
        dbExec = dbExecutor;
    }

    /**
     * Chủ phòng bấm bắt đầu: kiểm tra điều kiện, ghi lượt thử vào DB, tạo World mới và báo GAME_START.
     * Trận chỉ chạy thật khi cả 2 client đã gửi UDP_HELLO (xem onHello).
     */
    public static void start(Sess host, Msg req) {
        Room r = host.room;
        if (r == null) {
            host.h.err(req, Err.BAD_STATE, "not in room");
            return;
        }

        int acc1, acc2, lv, mid;

        synchronized (r) {
            if (r.host != host.acc) {
                host.h.err(req, Err.NOT_HOST, "only host can start game");
                return;
            }

            if (r.phase != Room.Phase.IDLE) {
                host.h.err(req, Err.BAD_STATE, "game already started");
                return;
            }

            if (r.mem.size() != 2) {
                host.h.err(req, Err.NEED_2, "need 2 players");
                return;
            }

            LevelDao.LevelRow levelRow = Levels.get(r.lv);
            if (levelRow == null) {
                host.h.err(req, Err.LEVEL_NOT_FOUND, "level not found");
                return;
            }

            r.phase = Room.Phase.LOADING;
            r.votes.clear();
            mid = ++r.matchId;
            acc1 = r.mem.get(0).s.acc;
            acc2 = r.mem.get(1).s.acc;
            lv = r.lv;
        }

        // Ghi DB ngoài khóa phòng để không chặn các luồng khác đang chờ phòng
        try {
            ResultDao.onStart(acc1, acc2, lv);
        } catch (Exception e) {
            Log.error("Match", "onStart DB error", "error", e.getMessage());
            synchronized (r) {
                if (r.matchId == mid) {
                    r.phase = Room.Phase.IDLE;
                }
            }
            host.h.err(req, Err.DB_ERROR, "database error");
            return;
        }

        synchronized (r) {
            // Trong lúc ghi DB có thể có người rời phòng hoặc trận bị hủy: kiểm tra lại
            if (r.matchId != mid || r.mem.size() != 2) {
                return;
            }

            try {
                LevelDao.LevelRow levelRow = Levels.get(lv);
                Level level = LevelLoader.fromJson(levelRow.json());

                // Xác định ai là Lửa, ai là Nước theo lựa chọn trong phòng
                int accFire = r.mem.get(0).el == Element.FIRE ? r.mem.get(0).s.acc : r.mem.get(1).s.acc;
                int accWater = r.mem.get(0).el == Element.WATER ? r.mem.get(0).s.acc : r.mem.get(1).s.acc;

                World w = new World(level, lv, accFire, accWater);
                r.world = w;

                // Xóa trạng thái UDP cũ: mỗi người phải gửi UDP_HELLO lại cho trận mới
                for (Room.Mem m : r.mem) {
                    m.s.status = "PLAYING";
                    m.s.udp = null;
                    m.s.input = fw.common.game.Input.NONE;
                    m.s.lastSeq = 0;
                }

                // Báo GAME_START cho cả 2 (người chủ phòng nhận kèm rid của yêu cầu)
                JsonObject data = new JsonObject();
                data.addProperty("lv", lv);

                for (Room.Mem m : r.mem) {
                    Msg gameStart = new Msg(MsgType.GAME_START, null, data);
                    if (m.s.acc == host.acc) {
                        gameStart.rid = req.rid;
                    }
                    m.s.h.send(gameStart);
                }

                // Hẹn giờ: quá thời gian mà chưa đủ 2 UDP_HELLO thì hủy trận
                r.helloTimer = sched.schedule(() -> helloTimeout(r, mid), HELLO_WAIT_MS, TimeUnit.MILLISECONDS);

                Log.info("Match", "started", "room", r.id, "mid", mid, "lv", lv);

            } catch (Exception e) {
                Log.error("Match", "start failed", "room", r.id, "error", e.getMessage());
                r.phase = Room.Phase.IDLE;
                r.world = null;
                host.h.err(req, Err.INTERNAL, "failed to create world");
                return;
            }
        }

        Rooms.push();
        Online.push();
    }

    // Nhận UDP_HELLO: lưu địa chỉ UDP của người chơi; đủ 2 người thì chuyển sang RUNNING
    public static void onHello(Room r, Sess s, InetSocketAddress src) {
        synchronized (r) {
            if (r.phase == Room.Phase.IDLE) {
                return;
            }

            s.udp = src;
            s.lastUdpMs = System.currentTimeMillis();

            // Xác nhận đã nhận địa chỉ UDP
            JsonObject data = new JsonObject();
            UdpServer.send(src, new Msg(MsgType.UDP_ACK, null, data));

            // Cả 2 đã có địa chỉ UDP thì bắt đầu chạy trận
            if (r.phase == Room.Phase.LOADING) {
                boolean allReady = true;
                for (Room.Mem m : r.mem) {
                    if (m.s.udp == null) {
                        allReady = false;
                        break;
                    }
                }

                if (allReady) {
                    go(r);
                }
            }

            Log.debug("Match", "hello", "room", r.id, "acc", s.acc);
        }
    }

    // Bắt đầu chạy trận: hủy hẹn giờ chờ, đặt lại mốc UDP để không bị tính là mất kết nối, lên lịch Loop
    private static void go(Room r) {
        // Đã giữ khóa phòng khi gọi hàm này
        r.phase = Room.Phase.RUNNING;
        if (r.helloTimer != null) {
            r.helloTimer.cancel(false);
        }
        r.goMs = System.currentTimeMillis();

        long now = System.currentTimeMillis();
        for (Room.Mem m : r.mem) {
            m.s.lastUdpMs = now;
        }

        // Báo GAME_GO rồi chạy vòng lặp game 60 tick/giây
        JsonObject data = new JsonObject();
        for (Room.Mem m : r.mem) {
            m.s.h.send(new Msg(MsgType.GAME_GO, null, data));
        }

        int mid = r.matchId;
        r.tick = sched.scheduleAtFixedRate(
            new Loop(r, mid),
            0,
            1_000_000_000L / Const.TICK_HZ,
            TimeUnit.NANOSECONDS
        );

        Log.info("Match", "go", "room", r.id, "mid", mid);
    }

    // Hết giờ chờ UDP_HELLO mà trận vẫn đang LOADING: hủy trận (mid để bỏ qua hẹn giờ của trận cũ)
    private static void helloTimeout(Room r, int mid) {
        After after = null;

        synchronized (r) {
            if (r.matchId == mid && r.phase == Room.Phase.LOADING) {
                after = endMatch(r, MatchKind.ABORT, "UDP_TIMEOUT");
            }
        }

        if (after != null) {
            after.run();
        }
    }

    /**
     * Kết thúc trận: dừng Loop, đưa phòng về IDLE, báo hủy nếu là ABORT.
     * Trả về After chứa việc làm sau khi nhả khóa (đẩy danh sách phòng/online, ghi DB khi thắng).
     */
    public static After endMatch(Room r, MatchKind kind, String info) {
        // Đã giữ khóa phòng khi gọi hàm này
        if (r.tick != null) {
            r.tick.cancel(false);
            r.tick = null;
        }
        if (r.helloTimer != null) {
            r.helloTimer.cancel(false);
            r.helloTimer = null;
        }

        r.phase = Room.Phase.IDLE;
        r.votes.clear();

        // Khi 1 người thoát giữa trận, họ đã bị xóa khỏi r.mem trước khi gọi hàm này,
        // nên chỉ đọc acc khi còn đủ 2 người (chỉ cần cho trường hợp WON)
        int acc1 = r.mem.size() > 0 ? r.mem.get(0).s.acc : 0;
        int acc2 = r.mem.size() > 1 ? r.mem.get(1).s.acc : 0;
        int lv = r.lv;
        double sec = r.world != null ? r.world.sec() : 0;

        for (Room.Mem m : r.mem) {
            m.s.status = "IDLE";
        }

        After after = new After();

        switch (kind) {
            case WON -> {
                // Sự kiện thắng đã gửi trong Loop; chỉ cần ghi kết quả vào DB (chạy sau, ngoài khóa)
                after.dbTask = () -> {
                    try {
                        ResultDao.onWin(acc1, acc2, lv, sec);
                    } catch (Exception e) {
                        Log.error("Match", "onWin DB error", "error", e.getMessage());
                    }
                };
            }
            case LOST -> {
                // Sự kiện LEVEL_FAILED đã gửi trong Loop, không cần làm gì thêm
            }
            case ABORT -> {
                JsonObject data = new JsonObject();
                data.addProperty("reason", info);
                Msg abortMsg = new Msg(MsgType.GAME_ABORT, null, data);
                for (Room.Mem m : r.mem) {
                    m.s.h.send(abortMsg);
                }
            }
        }

        r.world = null;

        Log.info("Match", "ended", "room", r.id, "kind", kind, "info", info != null ? info : "");

        return after;
    }

    public enum MatchKind {
        WON, LOST, ABORT
    }

    // Các việc chạy sau khi nhả khóa phòng: gửi danh sách mới cho mọi người, ghi DB ở luồng riêng
    public static class After {
        Runnable dbTask;

        public void run() {
            Rooms.push();
            Online.push();

            if (dbTask != null && dbExec != null) {
                dbExec.submit(dbTask);
            }
        }
    }
}
