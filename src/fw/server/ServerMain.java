package fw.server;

import fw.common.util.Log;
import fw.server.db.Db;
import fw.server.db.LevelDao;
import fw.server.net.TcpServer;
import fw.server.net.UdpServer;
import fw.server.svc.Levels;
import fw.server.svc.Match;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class ServerMain {
    // Khởi động server: đọc cấu hình, khởi tạo DB và các màn, tạo các luồng, mở cổng UDP và TCP
    public static void main(String[] args) {
        try {
            Log.info("SRV", "starting");

            // Đọc cấu hình server.properties
            Cfg.load();

            // Kết nối DB, tạo bảng nếu chưa có
            String dbUrl = Cfg.get("db.url");
            String dbUser = Cfg.get("db.user");
            String dbPass = Cfg.get("db.pass");

            Db.init(dbUrl, dbUser, dbPass);

            // Đồng bộ các file màn vào DB
            LevelDao.sync();

            // Nạp các màn từ DB vào bộ nhớ
            Levels.load();

            // Đọc cổng TCP/UDP
            int tcpPort = Cfg.getInt("tcp.port", 5000);
            int udpPort = Cfg.getInt("udp.port", 5001);

            // Luồng chạy vòng lặp game theo lịch, và 1 luồng riêng cho việc ghi DB
            int cores = Runtime.getRuntime().availableProcessors();
            ScheduledExecutorService sched = Executors.newScheduledThreadPool(
                Math.max(2, cores),
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("fw-sched");
                    return t;
                }
            );

            ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("fw-db");
                return t;
            });

            // Giao các luồng cho Match dùng
            Match.init(sched, dbExec);

            // Mở cổng UDP (input, snapshot)
            UdpServer.start(udpPort);

            // Mở cổng TCP (đăng nhập, phòng, chat, sự kiện)
            TcpServer.start(tcpPort);

            // Khi tắt server: dừng mạng, dừng các luồng, đóng log
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Log.info("SRV", "shutting down");
                TcpServer.stop();
                UdpServer.stop();
                sched.shutdown();
                dbExec.shutdown();
            }));

            Log.info("SRV", "started", "tcp", tcpPort, "udp", udpPort, "levels", Levels.all().size());

            // Giữ luồng chính sống để server tiếp tục chạy
            Thread.currentThread().join();

        } catch (Exception e) {
            Log.error("SRV", "startup failed", "error", e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
