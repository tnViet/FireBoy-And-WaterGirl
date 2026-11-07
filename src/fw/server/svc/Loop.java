package fw.server.svc;

import fw.common.game.*;
import fw.common.net.Const;
import fw.common.net.Msg;
import fw.common.net.MsgType;
import fw.common.util.Log;
import fw.server.net.UdpServer;
import com.google.gson.JsonObject;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Vòng lặp game của 1 phòng, chạy 60 lần/giây trên luồng lịch của server.
 * Mỗi tick: kiểm tra mất UDP, đưa input mới nhất vào World.step, gửi sự kiện qua TCP,
 * cứ 2 tick gửi 1 snapshot qua UDP, rồi kiểm tra trận đã kết thúc chưa.
 */
public final class Loop implements Runnable {
    private final Room room;
    private final int matchId;

    private long tickCount = 0;
    private long totalUs = 0;
    private long maxUs = 0;
    private long statsResetAt = 0;

    public Loop(Room room, int matchId) {
        this.room = room;
        this.matchId = matchId;
    }

    @Override
    public void run() {
        try {
            tick();
        } catch (Throwable t) {
            Log.error("LOOP", "uncaught error", "room", room.id, t);
            endWithError();
        }
    }

    /**
     * Toàn bộ thay đổi trạng thái nằm trong khóa phòng; gửi UDP và việc sau khi kết thúc trận làm ngoài khóa.
     * matchId dùng để dừng vòng lặp cũ nếu phòng đã sang trận khác.
     */
    private void tick() {
        long startNano = System.nanoTime();
        Match.After after = null;
        List<Out> outs = new ArrayList<>();

        synchronized (room) {
            if (room.matchId != matchId || room.phase != Room.Phase.RUNNING) {
                if (room.tick != null) {
                    room.tick.cancel(false);
                }
                return;
            }

            long now = System.currentTimeMillis();

            // Quá lâu không nhận gói UDP nào của 1 người thì hủy trận
            for (Room.Mem m : room.mem) {
                if (now - m.s.lastUdpMs > Const.UDP_TO_MS) {
                    after = Match.endMatch(room, Match.MatchKind.ABORT, "UDP_TIMEOUT");
                    break;
                }
            }

            if (after == null) {
                // Lấy input mới nhất của từng người theo nhân vật (0 = Lửa, 1 = Nước)
                Input[] inputs = new Input[2];
                for (Room.Mem m : room.mem) {
                    int idx = m.el == Element.FIRE ? 0 : 1;
                    inputs[idx] = m.s.input;
                }

                // Chạy 1 tick vật lý
                List<Ev> evs = room.world.step(inputs);

                // Sự kiện (lấy chìa, gạt cần, chết...) gửi qua TCP để không bị mất
                for (Ev ev : evs) {
                    Msg eventMsg = ev.toMsg(room.lv);
                    for (Room.Mem m : room.mem) {
                        m.s.h.send(eventMsg);
                    }
                }

                // Cứ TICK_HZ / SNAP_HZ tick thì đóng gói 1 snapshot, kèm số input cuối đã nhận của mỗi người
                if (room.world.tick % (Const.TICK_HZ / Const.SNAP_HZ) == 0) {
                    int[] acks = new int[2];
                    for (Room.Mem m : room.mem) {
                        int idx = m.el == Element.FIRE ? 0 : 1;
                        acks[idx] = m.s.lastSeq;
                    }

                    String snapJson = Snap.write(room.world, acks);
                    if (snapJson != null) {
                        byte[] snapBytes = snapJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        for (Room.Mem m : room.mem) {
                            if (m.s.udp != null) {
                                outs.add(new Out(m.s.udp, snapBytes));
                            }
                        }
                    }
                }

                // Thắng hoặc thua thì kết thúc trận
                if (room.world.phase != World.Phase.RUN) {
                    Match.MatchKind kind = (room.world.phase == World.Phase.WON)
                        ? Match.MatchKind.WON
                        : Match.MatchKind.LOST;
                    after = Match.endMatch(room, kind, null);
                }
            }
        }

        // Gửi UDP ngoài khóa phòng để không giữ khóa lâu
        for (Out out : outs) {
            UdpServer.send(out.addr, out.data);
        }

        if (after != null) {
            after.run();
        }

        // Đo thời gian mỗi tick: cảnh báo tick chậm, cứ 5 giây ghi thống kê trung bình và lớn nhất
        long elapsedNano = System.nanoTime() - startNano;
        long elapsedUs = elapsedNano / 1000;

        tickCount++;
        totalUs += elapsedUs;
        if (elapsedUs > maxUs) {
            maxUs = elapsedUs;
        }

        if (elapsedUs > 16000) {
            Log.warn("LOOP", "slow tick", "room", room.id, "us", elapsedUs);
        }

        // Cứ 300 tick (5 giây) ghi thống kê 1 lần
        if (tickCount - statsResetAt >= 300) {
            long avgUs = totalUs / (tickCount - statsResetAt);
            Log.info("LOOP", "stats", "room", room.id, "ticks", (tickCount - statsResetAt), "avgUs", avgUs, "maxUs", maxUs);
            statsResetAt = tickCount;
            totalUs = 0;
            maxUs = 0;
        }
    }

    private void endWithError() {
        Match.After after;
        synchronized (room) {
            after = Match.endMatch(room, Match.MatchKind.ABORT, "SERVER_ERROR");
        }
        if (after != null) {
            after.run();
        }
    }

    private static class Out {
        final InetSocketAddress addr;
        final byte[] data;

        Out(InetSocketAddress addr, byte[] data) {
            this.addr = addr;
            this.data = data;
        }
    }
}
