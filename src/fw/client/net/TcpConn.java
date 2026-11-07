package fw.client.net;

import fw.common.net.Const;
import fw.common.net.Json;
import fw.common.net.Msg;
import fw.common.net.MsgType;
import fw.common.util.Log;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Kết nối TCP của client tới server: luồng đọc, luồng ghi (hàng đợi) và luồng gửi PING giữ kết nối.
 * Mỗi tin là 1 dòng JSON; tin nhận được chuyển cho onMsg, mất kết nối thì gọi onClosed.
 */
public final class TcpConn {
    private static final int MAX_LINE = 262144;

    private Socket sock;
    private final BlockingQueue<String> out = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger ridCounter = new AtomicInteger(0);

    private Thread readerThread;
    private Thread writerThread;
    private Thread pingThread;

    public Consumer<Msg> onMsg;
    public Runnable onClosed;

    // Mở kết nối mới và khởi động 3 luồng (ghi, đọc, PING); dùng được cả khi kết nối lại
    public void connect(String host, int port) throws IOException {
        sock = new Socket(host, port);
        sock.setTcpNoDelay(true);
        // bỏ các tin chưa gửi được của kết nối cũ
        out.clear();
        closed.set(false);

        BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8));

        // Luồng ghi: lấy tin trong hàng đợi gửi đi, lỗi ghi thì đóng kết nối
        writerThread = new Thread(() -> {
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
                if (!closed.get()) {
                    Log.error("TCP", "write error", e);
                }
                close();
            }
        }, "tcp-writer");
        writerThread.setDaemon(true);
        writerThread.start();

        // Luồng đọc: mỗi dòng là 1 tin JSON, chuyển cho onMsg; hết luồng thì báo onClosed
        readerThread = new Thread(() -> {
            try {
                while (!closed.get()) {
                    String line = readLine(in);
                    if (line == null) {
                        break;
                    }

                    try {
                        Msg msg = Json.parse(line);
                        if (onMsg != null) {
                            onMsg.accept(msg);
                        }
                    } catch (Exception e) {
                        Log.warn("TCP", "parse error", "error", e.getMessage());
                    }
                }
            } catch (IOException e) {
                if (!closed.get()) {
                    Log.error("TCP", "read error", e);
                }
            } finally {
                close();
                if (onClosed != null) {
                    onClosed.run();
                }
            }
        }, "tcp-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        // Heartbeat: server đóng kết nối nếu quá TCP_TO_MS (15s) không nhận được gì,
        // nên cứ PING_MS (5s) gửi 1 PING để báo "tôi vẫn còn đây" kể cả khi người chơi ngồi yên
        pingThread = new Thread(() -> {
            try {
                while (!closed.get()) {
                    Thread.sleep(Const.PING_MS);
                    if (!closed.get()) sendPing();
                }
            } catch (InterruptedException e) {
                // Bị ngắt khi đóng kết nối: thoát luồng
            }
        }, "tcp-ping");
        pingThread.setDaemon(true);
        pingThread.start();

        Log.info("TCP", "connected", "host", host, "port", port);
    }

    public boolean isClosed() {
        return closed.get();
    }

    // Đọc 1 dòng có giới hạn độ dài; null khi server đóng kết nối
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

    public void send(String type, JsonObject data) {
        int rid = ridCounter.incrementAndGet();
        Msg msg = new Msg(type, rid, data);
        String line = Json.str(msg);
        out.offer(line);
    }

    // Gửi tin kèm mã yêu cầu (rid) tăng dần để ghép với tin trả lời; trả về rid đã dùng
    public int sendRid(String type, JsonObject data) {
        int rid = ridCounter.incrementAndGet();
        Msg msg = new Msg(type, rid, data);
        String line = Json.str(msg);
        out.offer(line);
        return rid;
    }

    public void sendPing() {
        JsonObject data = new JsonObject();
        Msg msg = new Msg(MsgType.PING, null, data);
        String line = Json.str(msg);
        out.offer(line);
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (writerThread != null) writerThread.interrupt();
            if (pingThread != null) pingThread.interrupt();
            if (sock != null) {
                try {
                    sock.close();
                } catch (IOException e) {
                    // Socket đã đóng sẵn, bỏ qua
                }
            }
            Log.info("TCP", "closed");
        }
    }
}
