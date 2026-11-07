package fw.server.svc;

import fw.common.game.Element;
import fw.common.game.World;
import fw.common.net.Msg;
import fw.common.net.MsgType;
import fw.server.net.Sess;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

public final class Room {
    public final String id;
    public final List<Mem> mem = new ArrayList<>(2);
    public volatile int host;
    public int lv;
    public Phase phase = Phase.IDLE;
    public int matchId;
    public World world;
    public ScheduledFuture<?> tick;
    public ScheduledFuture<?> helloTimer;
    public long goMs;
    /** Phiếu sau khi kết thúc màn: acc -> "REPLAY" | "NEXT". Xóa khi bắt đầu trận mới hoặc có người ra/vào */
    public final java.util.Map<Integer, String> votes = new java.util.HashMap<>();

    public enum Phase {
        IDLE, LOADING, RUNNING
    }

    public static final class Mem {
        public final Sess s;
        public Element el;

        public Mem(Sess s, Element el) {
            this.s = s;
            this.el = el;
        }
    }

    public Room(String id, Sess host, int lv) {
        this.id = id;
        this.host = host.acc;
        this.lv = lv;
        this.mem.add(new Mem(host, Element.FIRE));
        host.room = this;
    }

    public Mem findByAcc(int acc) {
        for (Mem m : mem) {
            if (m.s.acc == acc) return m;
        }
        return null;
    }

    public Mem other(int acc) {
        for (Mem m : mem) {
            if (m.s.acc != acc) return m;
        }
        return null;
    }

    public boolean full() {
        return mem.size() >= 2;
    }

    // Tin ROOM_INFO: mã phòng, chủ phòng, màn đang chọn, danh sách thành viên kèm nhân vật
    public Msg info() {
        JsonObject data = new JsonObject();
        data.addProperty("room", id);
        data.addProperty("host", host);
        data.addProperty("lv", lv);

        String status = (phase == Phase.IDLE) ? "WAITING" : "PLAYING";
        data.addProperty("status", status);

        com.google.gson.JsonArray members = new com.google.gson.JsonArray();
        for (Mem m : mem) {
            JsonObject memberData = new JsonObject();
            memberData.addProperty("acc", m.s.acc);
            memberData.addProperty("name", m.s.name);
            memberData.addProperty("el", m.el.name());
            members.add(memberData);
        }
        data.add("members", members);

        return new Msg(MsgType.ROOM_INFO, null, data);
    }
}
