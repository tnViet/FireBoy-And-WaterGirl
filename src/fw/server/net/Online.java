package fw.server.net;

import fw.common.net.Json;
import fw.common.net.Msg;
import fw.common.net.MsgType;
import fw.common.util.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class Online {
    private static final ConcurrentHashMap<Integer, Sess> sessions = new ConcurrentHashMap<>();

    private Online() {}

    public static boolean add(Sess s) {
        return sessions.putIfAbsent(s.acc, s) == null;
    }

    public static void remove(Sess s) {
        sessions.remove(s.acc, s);
    }

    public static Sess get(int acc) {
        return sessions.get(acc);
    }

    public static Collection<Sess> all() {
        return sessions.values();
    }

    public static void push() {
        Msg msg = createOnlineList();
        String line = Json.str(msg);

        for (Sess s : sessions.values()) {
            s.h.sendLine(line);
        }

        Log.debug("Online", "push", "count", sessions.size());
    }

    // Danh sách người đang online (sắp theo tên), kèm trạng thái rảnh hoặc đang chơi
    public static Msg createOnlineList() {
        JsonObject data = new JsonObject();
        JsonArray arr = new JsonArray();

        List<Sess> sorted = sessions.values().stream()
            .sorted(Comparator.comparing(s -> s.name))
            .toList();

        for (Sess s : sorted) {
            JsonObject player = new JsonObject();
            player.addProperty("acc", s.acc);
            player.addProperty("name", s.name);
            player.addProperty("status", s.status);
            arr.add(player);
        }

        data.add("players", arr);
        return new Msg(MsgType.ONLINE_LIST, null, data);
    }
}
