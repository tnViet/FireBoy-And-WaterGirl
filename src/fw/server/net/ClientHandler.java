package fw.server.net;

import fw.common.net.*;
import fw.common.util.Log;
import fw.server.svc.AuthSvc;
import fw.server.svc.RankSvc;
import fw.server.svc.RoomSvc;
import fw.server.svc.Rooms;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phục vụ 1 kết nối TCP của 1 client: luồng đọc nhận từng dòng JSON và xử lý,
 * luồng ghi riêng lấy tin từ hàng đợi để gửi (gửi không bao giờ chặn luồng game).
 */
public final class ClientHandler implements Runnable {
    private static final AtomicInteger idCounter = new AtomicInteger(0);
    private static final int MAX_LINE = 8192;
    private static final int TCP_TO_MS = 30000;

    private final int id = idCounter.incrementAndGet();
    public final Socket sock;
    public volatile Sess sess;
    private final BlockingQueue<String> out = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public ClientHandler(Socket sock) {
        this.sock = sock;
    }

    @Override
    public void run() {
        try {
            sock.setSoTimeout(TCP_TO_MS);
            sock.setTcpNoDelay(true);

            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8));

            // Luồng ghi: lấy từng dòng trong hàng đợi gửi đi, lỗi ghi thì đóng kết nối
            Thread writerThread = new Thread(() -> {
                try {
                    while (!closed.get()) {
                        String line = out.take();
                        writer.write(line);
                        writer.write('\n');
                        writer.flush();
                    }
                } catch (InterruptedException e) {
                    // Bị ngắt khi đóng kết nối: thoát luồng
                } catch (IOException e) {
                    close();
                }
            }, "fw-w-" + id);
            writerThread.setDaemon(true);
            writerThread.start();

            /**
             * Vòng đọc: mỗi dòng là 1 tin JSON. Hết thời gian chờ, client đóng kết nối
             * hoặc gửi sai JSON 3 lần liên tiếp thì ngắt. Lỗi khi xử lý tin chỉ trả ERR, không ngắt.
             */
            int errorCount = 0;

            while (!closed.get()) {
                String line;
                try {
                    line = readLine(in);
                } catch (SocketTimeoutException e) {
                    Log.warn("TCP", "timeout", "id", id);
                    break;
                }

                if (line == null) {
                    Log.info("TCP", "peer closed", "id", id);
                    break;
                }

                Msg msg;
                try {
                    msg = Json.parse(line);
                    errorCount = 0;
                } catch (Exception e) {
                    errorCount++;
                    err(null, Err.BAD_JSON, "invalid JSON: " + e.getMessage());
                    if (errorCount >= 3) {
                        Log.warn("TCP", "too many bad JSON", "id", id);
                        break;
                    }
                    continue;
                }

                try {
                    dispatch(msg);
                } catch (BadInput e) {
                    err(msg, Err.BAD_INPUT, e.getMessage());
                } catch (Exception e) {
                    Log.error("TCP", "dispatch error", "type", msg.type, "error", e.getMessage());
                    err(msg, Err.INTERNAL, "internal server error");
                }
            }

        } catch (IOException e) {
            Log.error("TCP", "IO error", "id", id, "error", e.getMessage());
        } finally {
            close();
        }
    }

    // Đọc 1 dòng có giới hạn độ dài (chống client gửi dòng quá dài làm tràn bộ nhớ); null khi hết luồng
    private String readLine(BufferedReader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = in.read()) != -1) {
            if (ch == '\n') {
                return sb.toString();
            }
            sb.append((char) ch);
            if (sb.length() > MAX_LINE) {
                throw new IOException("Line too long");
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Chuyển tin tới service tương ứng theo loại tin.
     * Phần lớn lệnh yêu cầu đã đăng nhập (sess != null), lệnh trong phòng yêu cầu đang ở trong phòng.
     */
    private void dispatch(Msg msg) {
        switch (msg.type) {
            case MsgType.PING -> {
                JsonObject data = new JsonObject();
                reply(msg, MsgType.PONG, data);
            }
            case MsgType.REGISTER, MsgType.LOGIN -> {
                if (sess != null) {
                    err(msg, Err.ALREADY_LOGGED_IN, "already logged in");
                    return;
                }
                if (msg.type.equals(MsgType.REGISTER)) {
                    AuthSvc.register(this, msg);
                } else {
                    AuthSvc.login(this, msg);
                }
            }
            case MsgType.LOGOUT -> {
                if (sess == null) {
                    err(msg, Err.NOT_LOGGED_IN, "not logged in");
                    return;
                }
                AuthSvc.logout(this, msg);
            }
            case MsgType.GET_RANK -> {
                if (sess == null) {
                    err(msg, Err.NOT_LOGGED_IN, "not logged in");
                    return;
                }
                RankSvc.get(this, msg);
            }
            case MsgType.GET_HISTORY, MsgType.GET_STATS -> {
                if (sess == null) {
                    err(msg, Err.NOT_LOGGED_IN, "not logged in");
                    return;
                }
                if (msg.type.equals(MsgType.GET_HISTORY)) RankSvc.history(this, msg);
                else RankSvc.stats(this, msg);
            }
            case MsgType.CHAT -> {
                if (sess == null) {
                    err(msg, Err.NOT_LOGGED_IN, "not logged in");
                    return;
                }
                if (sess.room == null) {
                    err(msg, Err.BAD_STATE, "not in room");
                    return;
                }
                RoomSvc.chat(this, msg);
            }
            case MsgType.LOBBY_CHAT -> {
                if (sess == null) {
                    err(msg, Err.NOT_LOGGED_IN, "not logged in");
                    return;
                }
                RoomSvc.lobbyChat(this, msg);
            }
            case MsgType.ROOM_LIST -> {
                // Client bấm "Refresh" ở lobby -> gửi lại danh sách phòng
                if (sess == null) {
                    err(msg, Err.NOT_LOGGED_IN, "not logged in");
                    return;
                }
                Msg list = Rooms.createRoomList();
                list.rid = msg.rid;
                send(list);
            }
            case MsgType.CREATE_ROOM -> {
                if (sess == null) {
                    err(msg, Err.NOT_LOGGED_IN, "not logged in");
                    return;
                }
                if (sess.room != null) {
                    err(msg, Err.BAD_STATE, "already in room");
                    return;
                }
                RoomSvc.create(this, msg);
            }
            case MsgType.JOIN_ROOM -> {
                if (sess == null) {
                    err(msg, Err.NOT_LOGGED_IN, "not logged in");
                    return;
                }
                if (sess.room != null) {
                    err(msg, Err.BAD_STATE, "already in room");
                    return;
                }
                RoomSvc.join(this, msg);
            }
            case MsgType.LEAVE_ROOM -> {
                if (sess == null) {
                    err(msg, Err.NOT_LOGGED_IN, "not logged in");
                    return;
                }
                if (sess.room == null) {
                    err(msg, Err.BAD_STATE, "not in room");
                    return;
                }
                RoomSvc.leaveMsg(this, msg);
            }
            case MsgType.SELECT_LEVEL -> {
                if (sess == null) {
                    err(msg, Err.NOT_LOGGED_IN, "not logged in");
                    return;
                }
                if (sess.room == null) {
                    err(msg, Err.BAD_STATE, "not in room");
                    return;
                }
                RoomSvc.selectLevel(this, msg);
            }
            case MsgType.SWAP -> {
                if (sess == null) {
                    err(msg, Err.NOT_LOGGED_IN, "not logged in");
                    return;
                }
                if (sess.room == null) {
                    err(msg, Err.BAD_STATE, "not in room");
                    return;
                }
                RoomSvc.swap(this, msg);
            }
            case MsgType.START_GAME -> {
                if (sess == null) {
                    err(msg, Err.NOT_LOGGED_IN, "not logged in");
                    return;
                }
                if (sess.room == null) {
                    err(msg, Err.BAD_STATE, "not in room");
                    return;
                }
                RoomSvc.start(this, msg);
            }
            case MsgType.VOTE -> {
                if (sess == null) {
                    err(msg, Err.NOT_LOGGED_IN, "not logged in");
                    return;
                }
                if (sess.room == null) {
                    err(msg, Err.BAD_STATE, "not in room");
                    return;
                }
                RoomSvc.vote(this, msg);
            }
            case MsgType.RESTART -> {
                if (sess == null) {
                    err(msg, Err.NOT_LOGGED_IN, "not logged in");
                    return;
                }
                if (sess.room == null) {
                    err(msg, Err.BAD_STATE, "not in room");
                    return;
                }
                RoomSvc.restart(this, msg);
            }
            default -> err(msg, Err.BAD_TYPE, "unknown message type");
        }
    }

    public void send(Msg m) {
        sendLine(Json.str(m));
    }

    public void sendLine(String line) {
        if (!closed.get()) {
            out.offer(line);
        }
    }

    public void reply(Msg req, String type, JsonObject data) {
        Msg reply = new Msg(type, req.rid, data);
        send(reply);
    }

    // Gửi tin ERR kèm mã lỗi; ghi lại lệnh gây lỗi (cmd) để client biết hiện lỗi ở đâu
    public void err(Msg req, String code, String message) {
        JsonObject data = new JsonObject();
        data.addProperty("code", code);
        if (req != null) {
            data.addProperty("cmd", req.type);
        }
        data.addProperty("msg", message);

        Msg errMsg = new Msg(MsgType.ERR, req != null ? req.rid : null, data);
        send(errMsg);
    }

    // Đóng kết nối đúng 1 lần: rời phòng (nếu có), xóa khỏi danh sách online và báo cho mọi người
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                sock.close();
            } catch (IOException e) {
                // Socket đã đóng sẵn, bỏ qua
            }

            if (sess != null) {
                if (sess.room != null) {
                    RoomSvc.leave(sess);
                }
                Online.remove(sess);
                Online.push();
                Log.info("TCP", "closed", "acc", sess.acc);
            } else {
                Log.info("TCP", "closed", "id", id);
            }
        }
    }
}
