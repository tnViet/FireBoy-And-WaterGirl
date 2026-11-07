package fw.server.svc;

import fw.common.game.Element;
import fw.common.net.Err;
import fw.common.net.Msg;
import fw.common.net.MsgType;
import fw.common.util.Log;
import fw.server.db.LevelDao;
import fw.server.net.ClientHandler;
import fw.server.net.Online;
import fw.server.net.Sess;
import com.google.gson.JsonObject;

public final class RoomSvc {
    private RoomSvc() {}

    public static void create(ClientHandler h, Msg m) {
        Sess sess = h.sess;

        Room room = Rooms.create(sess);

        Msg info = room.info();
        info.rid = m.rid;
        h.send(info);

        Rooms.push();

        Log.info("RoomSvc", "create", "room", room.id, "acc", sess.acc);
    }

    // Vào phòng: phòng phải tồn tại, chưa đủ người và không đang chơi; mình chưa ở phòng khác
    public static void join(ClientHandler h, Msg m) {
        Sess sess = h.sess;
        String roomId = m.str("room");

        Room room = Rooms.get(roomId);
        if (room == null) {
            h.err(m, Err.ROOM_NOT_FOUND, "room not found");
            return;
        }

        synchronized (room) {
            if (room.phase != Room.Phase.IDLE) {
                h.err(m, Err.BAD_STATE, "room is not waiting");
                return;
            }

            if (room.full()) {
                h.err(m, Err.ROOM_FULL, "room is full");
                return;
            }

            // Người vào sau nhận nhân vật còn lại (người trong phòng là Lửa thì mình là Nước)
            Element firstEl = room.mem.get(0).el;
            Element newEl = (firstEl == Element.FIRE) ? Element.WATER : Element.FIRE;

            room.mem.add(new Room.Mem(sess, newEl));
            room.votes.clear();
            sess.room = room;

            // Gửi thông tin phòng mới cho cả 2 người
            Msg info = room.info();
            for (Room.Mem mem : room.mem) {
                Msg copy = new Msg(info.type, null, info.data);
                if (mem.s.acc == sess.acc) {
                    copy.rid = m.rid;
                }
                mem.s.h.send(copy);
            }
        }

        Rooms.push();

        Log.info("RoomSvc", "join", "room", roomId, "acc", sess.acc);
    }

    /**
     * Rời phòng (chủ động, đăng xuất hoặc mất kết nối): chuyển chủ phòng cho người còn lại,
     * hủy trận nếu đang chơi, phòng trống thì xóa phòng
     */
    public static void leave(Sess s) {
        Room r = s.room;
        if (r == null) return;

        synchronized (r) {
            Room.Mem mem = r.findByAcc(s.acc);
            if (mem != null) {
                r.mem.remove(mem);
            }

            s.room = null;
            s.status = "IDLE";
            r.votes.clear();

            // Chuyển chủ phòng cho người còn lại TRƯỚC khi kết thúc trận,
            // vì endMatch -> Rooms.push() cần tra tên chủ phòng trong r.mem
            if (!r.mem.isEmpty()) {
                r.host = r.mem.get(0).s.acc;
            }

            if (r.phase != Room.Phase.IDLE) {
                // Đang chơi mà có người rời thì hủy trận
                Match.After after = Match.endMatch(r, Match.MatchKind.ABORT, "PEER_LEFT");
                after.run();
            }

            if (r.mem.isEmpty()) {
                Rooms.remove(r);
            } else {
                // Báo thông tin phòng mới cho người còn lại
                r.mem.get(0).s.h.send(r.info());
            }
        }

        Rooms.push();
        Online.push();

        Log.info("RoomSvc", "leave", "room", r.id, "acc", s.acc);
    }

    public static void leaveMsg(ClientHandler h, Msg m) {
        Sess sess = h.sess;
        leave(sess);

        JsonObject data = new JsonObject();
        h.reply(m, MsgType.LEFT_ROOM, data);
    }

    // Chủ phòng chọn màn khác (chỉ khi chưa chơi); phiếu bầu cũ bị xóa
    public static void selectLevel(ClientHandler h, Msg m) {
        Sess sess = h.sess;
        Room room = sess.room;
        int lv = m.num("lv");

        synchronized (room) {
            if (room.host != sess.acc) {
                h.err(m, Err.NOT_HOST, "only host can select level");
                return;
            }

            if (room.phase != Room.Phase.IDLE) {
                h.err(m, Err.BAD_STATE, "cannot change level while playing");
                return;
            }

            LevelDao.LevelRow levelRow = Levels.get(lv);
            if (levelRow == null) {
                h.err(m, Err.LEVEL_NOT_FOUND, "level not found");
                return;
            }

            room.lv = lv;

            // Gửi thông tin phòng mới cho mọi người trong phòng
            for (Room.Mem mem : room.mem) {
                mem.s.h.send(room.info());
            }
        }

        Log.info("RoomSvc", "selectLevel", "room", room.id, "lv", lv);
    }

    // Đổi nhân vật Lửa/Nước giữa 2 người (chỉ khi chưa chơi)
    public static void swap(ClientHandler h, Msg m) {
        Sess sess = h.sess;
        Room room = sess.room;

        synchronized (room) {
            if (room.host != sess.acc) {
                h.err(m, Err.NOT_HOST, "only host can swap elements");
                return;
            }

            if (room.phase != Room.Phase.IDLE) {
                h.err(m, Err.BAD_STATE, "cannot swap while playing");
                return;
            }

            // Đổi nhân vật Lửa/Nước của mọi người trong phòng
            for (Room.Mem mem : room.mem) {
                mem.el = (mem.el == Element.FIRE) ? Element.WATER : Element.FIRE;
            }

            // Gửi thông tin phòng mới cho mọi người trong phòng
            for (Room.Mem mem : room.mem) {
                mem.s.h.send(room.info());
            }
        }

        Log.info("RoomSvc", "swap", "room", room.id);
    }

    public static void start(ClientHandler h, Msg m) {
        Match.start(h.sess, m);
    }

    /**
     * Bỏ phiếu sau khi kết thúc màn (thắng hoặc thua).
     * choice = REPLAY (chơi lại màn này) hoặc NEXT (sang màn tiếp theo).
     * Cả 2 người chọn giống nhau thì server tự bắt đầu trận mới; muốn nghỉ thì gửi LEAVE_ROOM.
     */
    public static void vote(ClientHandler h, Msg m) {
        Sess sess = h.sess;
        Room room = sess.room;
        String choice = m.str("choice");
        if (!choice.equals("REPLAY") && !choice.equals("NEXT")) {
            h.err(m, Err.BAD_INPUT, "choice must be REPLAY or NEXT");
            return;
        }

        Sess host = null;
        synchronized (room) {
            if (room.phase != Room.Phase.IDLE) {
                h.err(m, Err.BAD_STATE, "game is running");
                return;
            }
            if (room.mem.size() != 2) {
                h.err(m, Err.NEED_2, "need 2 players");
                return;
            }
            if (choice.equals("NEXT") && nextLevel(room.lv) < 0) {
                h.err(m, Err.LEVEL_NOT_FOUND, "đây là màn cuối cùng");
                return;
            }

            room.votes.put(sess.acc, choice);
            broadcastVotes(room);

            // Đủ 2 phiếu và giống nhau -> bắt đầu
            boolean agreed = room.votes.size() == 2 && room.votes.values().stream().distinct().count() == 1;
            if (agreed) {
                if (choice.equals("NEXT")) {
                    room.lv = nextLevel(room.lv);
                }
                Room.Mem hostMem = room.findByAcc(room.host);
                host = hostMem != null ? hostMem.s : null;
            }
        }

        Log.info("RoomSvc", "vote", "room", room.id, "acc", sess.acc, "choice", choice);

        // Match.start tự lấy khóa phòng, nên gọi ngoài synchronized
        if (host != null) {
            Match.start(host, new Msg(MsgType.START_GAME, null, new JsonObject()));
        }
    }

    /** Gửi VOTE_STATE (danh sách phiếu hiện tại) cho mọi người trong phòng. Đang giữ khóa phòng. */
    private static void broadcastVotes(Room room) {
        JsonObject data = new JsonObject();
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (java.util.Map.Entry<Integer, String> e : room.votes.entrySet()) {
            JsonObject v = new JsonObject();
            v.addProperty("acc", e.getKey());
            v.addProperty("choice", e.getValue());
            arr.add(v);
        }
        data.add("votes", arr);
        for (Room.Mem mem : room.mem) {
            mem.s.h.send(new Msg(MsgType.VOTE_STATE, null, data));
        }
    }

    /** id của màn đứng sau màn lv (theo thứ tự id), -1 nếu lv là màn cuối */
    private static int nextLevel(int lv) {
        java.util.List<LevelDao.LevelRow> all = Levels.all();
        for (int i = 0; i < all.size() - 1; i++) {
            if (all.get(i).id() == lv) return all.get(i + 1).id();
        }
        return -1;
    }

    /** CHAT {text}: gửi tin nhắn cho mọi người trong phòng (kể cả người gửi, để mọi máy hiện giống nhau) */
    public static void chat(ClientHandler h, Msg m) {
        Sess sess = h.sess;
        Room room = sess.room;
        String text = cleanChat(m.str("text"));
        if (text == null) return;

        JsonObject data = new JsonObject();
        data.addProperty("acc", sess.acc);
        data.addProperty("name", sess.name);
        data.addProperty("text", text);
        synchronized (room) {
            for (Room.Mem mem : room.mem) {
                mem.s.h.send(new Msg(MsgType.CHAT, null, data));
            }
        }
    }

    /** LOBBY_CHAT {text}: chat tổng, gửi cho mọi người đang online (kể cả người gửi) */
    public static void lobbyChat(ClientHandler h, Msg m) {
        Sess sess = h.sess;
        String text = cleanChat(m.str("text"));
        if (text == null) return;

        JsonObject data = new JsonObject();
        data.addProperty("acc", sess.acc);
        data.addProperty("name", sess.name);
        data.addProperty("text", text);
        String line = fw.common.net.Json.str(new Msg(MsgType.LOBBY_CHAT, null, data));
        for (Sess s : Online.all()) {
            s.h.sendLine(line);
        }
    }

    /** Cắt tin dài quá 200 ký tự, bỏ ký tự điều khiển để 1 tin luôn nằm trên 1 dòng; null nếu tin rỗng */
    private static String cleanChat(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty()) return null;
        if (text.length() > 200) text = text.substring(0, 200);
        return text.replaceAll("\\p{Cntrl}", " ");
    }

    // Chủ phòng bấm chơi lại: hủy trận đang chạy (nếu có) rồi bắt đầu lại màn hiện tại
    public static void restart(ClientHandler h, Msg m) {
        Sess sess = h.sess;
        Room room = sess.room;

        synchronized (room) {
            if (room.host != sess.acc) {
                h.err(m, Err.NOT_HOST, "only host can restart");
                return;
            }

            // Đang chơi thì hủy trận hiện tại; nếu trận đã kết thúc (thắng/thua) thì chơi lại luôn
            if (room.phase != Room.Phase.IDLE) {
                Match.After after = Match.endMatch(room, Match.MatchKind.ABORT, "RESTART");
                after.run();
            }
        }

        // Bắt đầu trận mới với màn hiện tại
        Match.start(sess, m);
    }
}
