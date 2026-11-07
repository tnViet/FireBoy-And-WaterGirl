package fw.client.net;

import fw.common.game.Input;
import fw.common.game.Snap;
import fw.common.net.Const;
import fw.common.net.Json;
import fw.common.net.Msg;
import fw.common.net.MsgType;
import fw.common.util.Log;
import fw.client.render.Interp;
import com.google.gson.JsonObject;

import javax.swing.Timer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * Kết nối UDP của client trong trận: gửi UDP_HELLO tới khi server xác nhận,
 * gửi input 30 lần/giây kèm số thứ tự, gửi PING mỗi giây để đo độ trễ, nhận snapshot đưa vào Interp.
 */
public final class UdpConn {
    private DatagramSocket sock;
    private InetAddress serverAddr;
    private int serverPort;
    private Thread readerThread;

    private Timer helloTimer;
    private Timer inputTimer;
    private Timer pingTimer;

    private int seq = 0;
    private Supplier<Input> inputSource;

    public volatile boolean acked = false;
    public volatile int pingMs = 0;

    private String currentRoom;
    private int currentAcc;
    private String currentToken;

    public void open(InetAddress server, int port) throws Exception {
        this.serverAddr = server;
        this.serverPort = port;

        sock = new DatagramSocket();
        sock.connect(serverAddr, serverPort);

        readerThread = new Thread(this::readLoop, "udp-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        Log.info("UDP", "opened", "server", server.getHostAddress(), "port", port);
    }

    // Gửi UDP_HELLO lặp lại tới khi nhận UDP_ACK (gói UDP có thể bị mất)
    public void hello(String room, int acc, String token) {
        this.currentRoom = room;
        this.currentAcc = acc;
        this.currentToken = token;
        this.acked = false;

        helloTimer = new Timer(Const.HELLO_MS, e -> sendHello());
        helloTimer.start();
    }

    private void sendHello() {
        if (acked) {
            if (helloTimer != null) {
                helloTimer.stop();
            }
            return;
        }

        JsonObject data = new JsonObject();
        data.addProperty("room", currentRoom);
        data.addProperty("acc", currentAcc);
        data.addProperty("token", currentToken);

        send(new Msg(MsgType.UDP_HELLO, null, data));
    }

    public void startInput(Supplier<Input> src) {
        this.inputSource = src;
        this.seq = 0;

        inputTimer = new Timer(1000 / Const.IN_HZ, e -> sendInput());
        inputTimer.start();
    }

    public void sendNow() {
        if (inputTimer != null && inputTimer.isRunning()) {
            sendInput();
        }
    }

    // Gửi trạng thái phím hiện tại (trái, phải, nhảy); số thứ tự tăng dần để server bỏ gói đến muộn
    private void sendInput() {
        if (inputSource == null) return;

        Input raw = inputSource.get();
        seq++;

        JsonObject data = new JsonObject();
        data.addProperty("room", currentRoom);
        data.addProperty("acc", currentAcc);
        data.addProperty("seq", seq);
        data.addProperty("l", raw.l());
        data.addProperty("r", raw.r());
        data.addProperty("j", raw.j());

        send(new Msg(MsgType.INPUT, null, data));
    }

    public void startPing() {
        pingTimer = new Timer(1000, e -> sendPing());
        pingTimer.start();
    }

    private void sendPing() {
        JsonObject data = new JsonObject();
        data.addProperty("room", currentRoom);
        data.addProperty("acc", currentAcc);
        data.addProperty("t", System.currentTimeMillis());

        send(new Msg(MsgType.PING, null, data));
    }

    private void send(Msg msg) {
        if (sock == null || sock.isClosed()) return;

        try {
            String json = Json.str(msg);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            DatagramPacket pkt = new DatagramPacket(bytes, bytes.length);
            sock.send(pkt);

        } catch (Exception e) {
            Log.warn("UDP", "send error", "error", e.getMessage());
        }
    }

    // Luồng nhận gói UDP: chạy tới khi socket bị đóng
    private void readLoop() {
        byte[] buffer = new byte[2048];

        while (sock != null && !sock.isClosed()) {
            try {
                DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                sock.receive(pkt);

                String json = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);
                handlePacket(json);

            } catch (Exception e) {
                if (sock != null && !sock.isClosed()) {
                    Log.warn("UDP", "receive error", "error", e.getMessage());
                }
                break;
            }
        }
    }

    // Xử lý gói nhận được: ACK dừng gửi HELLO, SNAP cập nhật trạng thái để vẽ, PONG tính độ trễ
    private void handlePacket(String json) {
        try {
            Msg msg = Json.parse(json);

            switch (msg.type) {
                case MsgType.UDP_ACK -> {
                    acked = true;
                    if (helloTimer != null) {
                        helloTimer.stop();
                    }
                    Log.debug("UDP", "acked");
                }

                case MsgType.SNAP -> {
                    Snap.SnapData snap = Snap.read(json);
                    if (snap != null) {
                        Interp.push(snap, System.nanoTime());
                    }
                }

                case MsgType.PONG -> {
                    if (msg.data.has("t")) {
                        long t = msg.data.get("t").getAsLong();
                        pingMs = (int) (System.currentTimeMillis() - t);
                    }
                }
            }

        } catch (Exception e) {
            Log.warn("UDP", "handle error", "error", e.getMessage());
        }
    }

    public void close() {
        if (helloTimer != null) {
            helloTimer.stop();
            helloTimer = null;
        }
        if (inputTimer != null) {
            inputTimer.stop();
            inputTimer = null;
        }
        if (pingTimer != null) {
            pingTimer.stop();
            pingTimer = null;
        }

        if (sock != null) {
            sock.close();
            sock = null;
        }

        Log.info("UDP", "closed");
    }

    public void disconnect() {
        close();
    }

    public boolean isConnected() {
        return acked && sock != null && !sock.isClosed();
    }
}
