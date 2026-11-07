package fw.server.net;

import fw.common.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TcpServer {
    private static ServerSocket serverSocket;
    private static Thread acceptThread;
    private static final AtomicBoolean running = new AtomicBoolean(false);

    private TcpServer() {}

    // Mở cổng TCP và chạy luồng nhận kết nối: mỗi client được 1 ClientHandler chạy trên luồng riêng
    public static void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        running.set(true);

        acceptThread = new Thread(() -> {
            Log.info("TCP", "listening", "port", port);

            while (running.get()) {
                try {
                    Socket sock = serverSocket.accept();
                    Log.info("TCP", "accepted", "remote", sock.getRemoteSocketAddress());

                    ClientHandler handler = new ClientHandler(sock);
                    Thread handlerThread = new Thread(handler, "fw-tcp-" + sock.getRemoteSocketAddress());
                    handlerThread.setDaemon(true);
                    handlerThread.start();

                } catch (IOException e) {
                    if (running.get()) {
                        Log.error("TCP", "accept error", "error", e.getMessage());
                    }
                }
            }
        }, "fw-accept");

        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public static void stop() {
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // Socket đã đóng sẵn, bỏ qua
            }
        }
        Log.info("TCP", "stopped");
    }
}
