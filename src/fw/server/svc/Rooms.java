package fw.server.svc;

import fw.common.net.Json;
import fw.common.net.Msg;
import fw.common.net.MsgType;
import fw.common.util.Log;
import fw.server.net.Sess;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Rooms {
    private static final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private static final AtomicInteger counter = new AtomicInteger(0);

    private Rooms() {}

    public static Room create(Sess host) {
        String id = "R" + counter.incrementAndGet();

        // Phòng mới mặc định chọn màn đầu tiên
        List<fw.server.db.LevelDao.LevelRow> all = Levels.all();
        int firstLv = all.isEmpty() ? 1 : all.get(0).id();

        Room room = new Room(id, host, firstLv);
        rooms.put(id, room);

        Log.info("Rooms", "create", "room", id, "host", host.acc);
        return room;
    }

    public static Room get(String id) {
        return rooms.get(id);
    }

    public static void remove(Room r) {
        rooms.remove(r.id);
        Log.info("Rooms", "remove", "room", r.id);
    }

    // Gửi danh sách phòng mới nhất cho mọi người đang online
    public static void push() {
        Msg msg = createRoomList();
        String line = Json.str(msg);

        for (Sess s : fw.server.net.Online.all()) {
            s.h.sendLine(line);
        }

        Log.debug("Rooms", "push", "count", rooms.size());
    }

    // Tin ROOM_LIST: mỗi phòng gồm mã, tên chủ phòng, số người, trạng thái
    public static Msg createRoomList() {
        JsonObject data = new JsonObject();
        JsonArray arr = new JsonArray();

        List<Room> sorted = rooms.values().stream()
            .sorted(Comparator.comparing(r -> Integer.parseInt(r.id.substring(1))))
            .toList();

        for (Room r : sorted) {
            synchronized (r) {
                JsonObject roomData = new JsonObject();
                roomData.addProperty("room", r.id);
                roomData.addProperty("n", r.mem.size());

                Room.Mem hostMem = r.findByAcc(r.host);
                String hostName = hostMem != null ? hostMem.s.name : "";
                roomData.addProperty("host", hostName);

                String status = (r.phase == Room.Phase.IDLE) ? "WAITING" : "PLAYING";
                roomData.addProperty("status", status);

                arr.add(roomData);
            }
        }

        data.add("rooms", arr);
        return new Msg(MsgType.ROOM_LIST, null, data);
    }
}
