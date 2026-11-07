package fw.server.net;

import fw.common.game.Input;
import fw.common.net.Json;
import fw.common.net.Msg;
import fw.common.net.MsgType;
import fw.common.util.Log;
import fw.server.svc.Match;
import fw.server.svc.Room;
import fw.server.svc.Rooms;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Cổng UDP của server: nhận UDP_HELLO, INPUT, PING từ client và gửi snapshot / PONG.
 * Mỗi gói là 1 tin JSON, tối đa 1400 byte để không bị chia nhỏ trên đường truyền.
 */
public final class UdpServer implements Runnable {
    private static DatagramSocket sock;
    private static Thread udpThread;
    private static volatile boolean running = false;

    private UdpServer() {}

    public static void start(int port) throws Exception {
        sock = new DatagramSocket(port);
        running = true;

        UdpServer server = new UdpServer();
        udpThread = new Thread(server, "fw-udp");
        udpThread.setDaemon(true);
        udpThread.start();

        Log.info("UDP", "listening", "port", port);
    }

    public static void stop() {
        running = false;
        if (sock != null) {
            sock.close();
        }
        Log.info("UDP", "stopped");
    }

    public static void send(InetSocketAddress to, Msg msg) {
        if (sock == null || sock.isClosed()) return;

        try {
            String json = Json.str(msg);
            byte[] data = json.getBytes(StandardCharsets.UTF_8);

            if (data.length > 1400) {
                Log.error("UDP", "message too large", "size", data.length);
                return;
            }

            DatagramPacket pkt = new DatagramPacket(data, data.length, to);
            sock.send(pkt);

        } catch (Exception e) {
            Log.warn("UDP", "send failed", "error", e.getMessage());
        }
    }

    public static void send(InetSocketAddress to, byte[] data) {
        if (sock == null || sock.isClosed()) return;

        try {
            if (data.length > 1400) {
                Log.error("UDP", "data too large", "size", data.length);
                return;
            }

            DatagramPacket pkt = new DatagramPacket(data, data.length, to);
            sock.send(pkt);

        } catch (Exception e) {
            Log.warn("UDP", "send failed", "error", e.getMessage());
        }
    }

    @Override
    // Luồng nhận gói UDP: chạy tới khi server dừng, lỗi 1 gói không làm dừng luồng
    public void run() {
        byte[] buffer = new byte[2048];

        while (running) {
            try {
                DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                sock.receive(pkt);

                InetSocketAddress src = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
                String json = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);

                handlePacket(src, json);

            } catch (Exception e) {
                if (running) {
                    Log.warn("UDP", "receive error", "error", e.getMessage());
                }
            }
        }
    }

    /**
     * Xử lý 1 gói: người gửi phải đang online và ở đúng phòng ghi trong gói.
     * HELLO kiểm tra token; INPUT chỉ nhận từ đúng địa chỉ đã HELLO và số thứ tự mới hơn gói trước
     * (gói đến muộn bị bỏ); mọi gói hợp lệ đều làm mới mốc chống mất kết nối.
     */
    private void handlePacket(InetSocketAddress src, String json) {
        try {
            Msg msg = Json.parse(json);

            String roomId = msg.str("room");
            int acc = msg.num("acc");

            Room r = Rooms.get(roomId);
            Sess s = Online.get(acc);

            if (r == null || s == null || s.room != r) {
                Log.debug("UDP", "invalid room/sess", "room", roomId, "acc", acc);
                return;
            }

            switch (msg.type) {
                case MsgType.UDP_HELLO -> {
                    String token = msg.str("token");
                    if (!token.equals(s.token)) {
                        Log.warn("UDP", "bad token", "acc", acc);
                        return;
                    }
                    Match.onHello(r, s, src);
                }

                case MsgType.INPUT -> {
                    synchronized (r) {
                        if (r.phase != Room.Phase.RUNNING) {
                            return;
                        }

                        if (!src.equals(s.udp)) {
                            Log.debug("UDP", "wrong src", "acc", acc);
                            return;
                        }

                        int seq = msg.num("seq");
                        if (seq <= s.lastSeq) {
                            return;
                        }

                        boolean l = msg.data.has("l") && msg.data.get("l").getAsBoolean();
                        boolean r2 = msg.data.has("r") && msg.data.get("r").getAsBoolean();
                        boolean j = msg.data.has("j") && msg.data.get("j").getAsBoolean();

                        s.input = new Input(l, r2, j);
                        s.lastSeq = seq;
                        s.lastUdpMs = System.currentTimeMillis();
                    }
                }

                case MsgType.PING -> {
                    if (!src.equals(s.udp)) {
                        return;
                    }

                    s.lastUdpMs = System.currentTimeMillis();

                    com.google.gson.JsonObject data = new com.google.gson.JsonObject();
                    if (msg.data.has("t")) {
                        data.addProperty("t", msg.data.get("t").getAsLong());
                    }
                    send(src, new Msg(MsgType.PONG, null, data));
                }

                default -> Log.debug("UDP", "unknown type", "type", msg.type);
            }

        } catch (Exception e) {
            Log.warn("UDP", "handle error", "error", e.getMessage());
        }
    }
}
