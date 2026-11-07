package fw.client.ui;

import fw.client.Model;
import fw.client.net.TcpConn;
import fw.client.net.UdpConn;
import fw.client.render.Interp;
import fw.common.net.Msg;
import fw.common.net.MsgType;
import fw.common.util.Log;
import fw.common.game.LevelLoader;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Properties;

public class ClientApp extends JFrame {
    private static final String TAG = "CLI";

    public static TcpConn tcp;
    public static UdpConn udp;

    private final CardLayout cardLayout;
    private final JPanel mainPanel;
    private final LoginScreen loginScreen;
    private final RegisterScreen registerScreen;
    private final LobbyScreen lobbyScreen;
    private final RoomScreen roomScreen;
    private final GameScreen gameScreen;
    private final RankDialog rankDialog;
    private final HistoryDialog historyDialog;

    private enum Screen {
        LOGIN, REGISTER, LOBBY, ROOM, GAME
    }

    private final String serverHost;
    private final int udpPort;

    /**
     * Dựng cửa sổ chính: các màn hình xếp chồng bằng CardLayout (đăng nhập, đăng ký, sảnh, phòng, trận),
     * hai hộp thoại xếp hạng/lịch sử, thanh tiêu đề tự vẽ; sau đó mở kết nối TCP tới server.
     */
    public ClientApp(String host, int tcpPort, int udpPort) {
        super("Fireboy & Watergirl");
        this.serverHost = host;
        this.udpPort = udpPort;

        // Dựng giao diện trước
        Theme.installDefaults();
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        loginScreen = new LoginScreen();
        registerScreen = new RegisterScreen();
        loginScreen.onGoRegister = () -> showScreen(Screen.REGISTER);
        registerScreen.onGoLogin = () -> showScreen(Screen.LOGIN);
        lobbyScreen = new LobbyScreen();
        roomScreen = new RoomScreen();
        gameScreen = new GameScreen();
        rankDialog = new RankDialog(this);
        historyDialog = new HistoryDialog(this);
        lobbyScreen.onRank = rankDialog::open;
        roomScreen.onRank = rankDialog::open;
        roomScreen.onHistory = historyDialog::open;

        mainPanel.add(loginScreen, Screen.LOGIN.name());
        mainPanel.add(registerScreen, Screen.REGISTER.name());
        mainPanel.add(lobbyScreen, Screen.LOBBY.name());
        mainPanel.add(roomScreen, Screen.ROOM.name());
        mainPanel.add(gameScreen, Screen.GAME.name());

        add(mainPanel);
        // thanh tiêu đề tự vẽ thay cho thanh của Windows
        WindowChrome.install(this);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Khung game 1024x576 + thanh chat/nút bên dưới
        setSize(1100, 800);
        setMinimumSize(new Dimension(1060, 760));
        setLocationRelativeTo(null);

        // Giao diện xong mới khởi tạo kết nối
        tcp = new TcpConn();
        tcp.onMsg = this::onMessage;
        tcp.onClosed = this::onDisconnected;
        udp = new UdpConn();
        instance = this;
        this.tcpPort = tcpPort;

        showScreen(Screen.LOGIN);
        reconnect();
    }

    private static ClientApp instance;
    private final int tcpPort;

    /** Kết nối (lại) TCP tới server. LoginScreen gọi trước khi gửi LOGIN/REGISTER nếu đang mất kết nối. */
    public static boolean reconnect() {
        ClientApp app = instance;
        try {
            tcp.connect(app.serverHost, app.tcpPort);
            app.loginScreen.showStatus(" ");
            app.registerScreen.showStatus(" ");
            return true;
        } catch (IOException e) {
            Log.error(TAG, "TCP connect failed", "error", e.getMessage());
            String text = "Không kết nối được server " + app.serverHost + ":" + app.tcpPort;
            app.loginScreen.showStatus(text, false);
            app.registerScreen.showStatus(text, false);
            return false;
        }
    }

    /** TCP bị đóng (server tắt, rớt mạng...): dọn trạng thái và đưa người chơi về màn hình đăng nhập */
    private void onDisconnected() {
        boolean wasLoggedIn = Model.name != null;
        stopGame();
        Model.clear();
        SwingUtilities.invokeLater(() -> {
            rankDialog.setVisible(false);
            // chat tổng của phiên cũ không giữ lại cho tài khoản sau
            lobbyScreen.chat().clear();
            loginScreen.setEnabled(true);
            registerScreen.setEnabled(true);
            loginScreen.clearPassword();
            loginScreen.showStatus("Mất kết nối tới server. Bấm Đăng nhập để kết nối lại.", false);
            if (wasLoggedIn) {
                PixelDialog.message(this, "Mất kết nối", "Mất kết nối tới server.", PixelDialog.Kind.WARNING);
            }
        });
        showScreen(Screen.LOGIN);
    }

    // Chuyển màn hình (luôn chạy trên luồng giao diện) và làm mới dữ liệu của màn hình vừa hiện
    private void showScreen(Screen screen) {
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainPanel, screen.name());

            switch (screen) {
                case LOGIN -> loginScreen.setEnabled(true);
                case REGISTER -> registerScreen.setEnabled(true);
                case LOBBY -> {
                    lobbyScreen.updateWelcome(Model.name);
                    lobbyScreen.updateRooms();
                    lobbyScreen.updateHistory();
                    // vừa chơi xong thì lịch sử đã đổi
                    if (tcp != null) HistoryDialog.request();
                }
                case ROOM -> {
                    roomScreen.updateRoom();
                    // vừa chơi xong thì thống kê/lịch sử đã đổi
                    requestRoomData();
                }
                case GAME -> gameScreen.startRendering();
            }
        });
    }

    /** Xin thống kê từng màn của mọi người trong phòng + lịch sử của mình */
    private void requestRoomData() {
        if (Model.room == null) return;
        for (Model.RoomData.Member m : Model.room.members) {
            JsonObject data = new JsonObject();
            data.addProperty("acc", m.acc());
            tcp.send(MsgType.GET_STATS, data);
        }
        HistoryDialog.request();
    }

    /** Chạy trên thread đọc TCP. Mọi thao tác UI đều chuyển sang EDT qua invokeLater. */
    private void onMessage(Msg msg) {
        try {
            switch (msg.type) {
                case MsgType.REGISTER_SUCCESS -> handleRegister(msg);
                case MsgType.LOGIN_SUCCESS -> handleLoginSuccess(msg);
                case MsgType.LOGOUT_SUCCESS -> handleLogoutSuccess(msg);
                case MsgType.LEVELS -> handleLevels(msg);
                case MsgType.ONLINE_LIST -> handleOnlineList(msg);
                case MsgType.ROOM_LIST -> handleRoomList(msg);
                case MsgType.ROOM_INFO -> handleRoomInfo(msg);
                case MsgType.LEFT_ROOM -> handleLeftRoom(msg);
                case MsgType.RANK -> SwingUtilities.invokeLater(() -> rankDialog.onRank(msg));
                case MsgType.VOTE_STATE -> SwingUtilities.invokeLater(() -> gameScreen.onVotes(msg));
                case MsgType.HISTORY -> handleHistory(msg);
                case MsgType.STATS -> handleStats(msg);
                case MsgType.CHAT -> handleChat(msg);
                case MsgType.LOBBY_CHAT -> handleLobbyChat(msg);
                case MsgType.GAME_START -> handleGameStart(msg);
                case MsgType.GAME_GO -> handleGameGo(msg);
                case MsgType.LEVEL_COMPLETE -> handleLevelComplete(msg);
                case MsgType.LEVEL_FAILED -> handleLevelFailed(msg);
                case MsgType.GAME_ABORT -> handleGameAbort(msg);
                case MsgType.ERR -> handleError(msg);
                // Các sự kiện trong game: hiệu ứng/âm thanh đã được GamePanel tự phát hiện từ snapshot UDP,
                // ở đây chỉ ghi log để tiện debug
                case MsgType.KEY_COLLECTED, MsgType.DOOR_PASSED, MsgType.LEVER_TOGGLED,
                     MsgType.PLATE_CHANGED, MsgType.MECH_MOVING, MsgType.PLAYER_DIED, MsgType.PONG ->
                    Log.debug(TAG, "event", "type", msg.type);
                default -> Log.warn(TAG, "Unknown message type", "type", msg.type);
            }
        } catch (Exception e) {
            Log.error(TAG, "Error handling message", "type", msg.type, "error", e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Tài khoản ───────────────────────────────────────────────────────

    /** Đăng ký thành công: chuyển sang màn hình Đăng nhập, điền sẵn tên đăng nhập */
    private void handleRegister(Msg msg) {
        SwingUtilities.invokeLater(() -> {
            String username = registerScreen.username();
            registerScreen.clear();
            registerScreen.setEnabled(true);
            loginScreen.prefill(username);
            loginScreen.showStatus("Đăng ký thành công! Nhập mật khẩu để đăng nhập.", true);
            showScreen(Screen.LOGIN);
        });
    }

    private void handleLoginSuccess(Msg msg) {
        Model.acc = msg.num("acc");
        Model.name = msg.str("name");
        Model.token = msg.str("token");
        Log.info(TAG, "Logged in", "acc", Model.acc, "name", Model.name);
        showScreen(Screen.LOBBY);
    }

    private void handleLogoutSuccess(Msg msg) {
        stopGame();
        Model.clear();
        SwingUtilities.invokeLater(() -> {
            rankDialog.setVisible(false);
            // chat tổng của phiên cũ không giữ lại cho tài khoản sau
            lobbyScreen.chat().clear();
            loginScreen.clearPassword();
            loginScreen.showStatus("Đã đăng xuất.", true);
        });
        showScreen(Screen.LOGIN);
    }

    // ── Lobby ───────────────────────────────────────────────────────────

    private void handleLevels(Msg msg) {
        JsonArray arr = msg.data.getAsJsonArray("levels");
        Model.levels.clear();
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            Model.levels.add(new Model.LevelInfo(
                obj.get("lv").getAsInt(),
                obj.get("name").getAsString()
            ));
        }
    }

    private void handleOnlineList(Msg msg) {
        JsonArray arr = msg.data.getAsJsonArray("players");
        Model.onlineList.clear();
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            Model.onlineList.add(new Model.PlayerInfo(
                obj.get("acc").getAsInt(),
                obj.get("name").getAsString(),
                obj.get("status").getAsString()
            ));
        }
        // Sảnh không còn hiện danh sách online, chỉ giữ dữ liệu trong Model
    }

    private void handleRoomList(Msg msg) {
        JsonArray arr = msg.data.getAsJsonArray("rooms");
        Model.roomList.clear();
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            Model.roomList.add(new Model.RoomInfoRow(
                obj.get("room").getAsString(),
                obj.get("host").getAsString(),
                obj.get("n").getAsInt(),
                obj.get("status").getAsString()
            ));
        }
        SwingUtilities.invokeLater(lobbyScreen::updateRooms);
    }

    // ── Phòng ───────────────────────────────────────────────────────────

    /**
     * ROOM_INFO: cập nhật thông tin phòng trong Model, so số người với lần trước để biết
     * mình vừa vào phòng, có người mới vào hay người kia vừa rời, rồi báo trong khung chat và vẽ lại phòng.
     */
    private void handleRoomInfo(Msg msg) {
        boolean firstTime = (Model.room == null);
        if (firstTime) {
            Model.room = new Model.RoomData();
        }
        int oldCount = Model.room.members.size();
        Model.room.room = msg.str("room");
        Model.room.host = msg.num("host");
        Model.room.lv = msg.num("lv");

        JsonArray members = msg.data.getAsJsonArray("members");
        Model.room.members.clear();
        for (JsonElement el : members) {
            JsonObject obj = el.getAsJsonObject();
            Model.room.members.add(new Model.RoomData.Member(
                obj.get("acc").getAsInt(),
                obj.get("name").getAsString(),
                obj.get("el").getAsString()
            ));
        }

        boolean peerLeft = !firstTime && Model.room.members.size() < oldCount;
        boolean peerJoined = !firstTime && Model.room.members.size() > oldCount;
        if (firstTime) {
            SwingUtilities.invokeLater(() -> {
                roomScreen.chat().clear();
                gameScreen.chat().clear();
            });
            chatSystem("Bạn đã vào phòng " + Model.room.room + ".");
        }
        if (peerJoined) {
            Model.RoomData.Member newest = Model.room.members.get(Model.room.members.size() - 1);
            chatSystem(newest.name() + " đã vào phòng.");
        }
        if (peerLeft) chatSystem("Người chơi kia đã rời phòng.");
        // có người mới -> xin thống kê của họ
        if (firstTime || peerJoined) requestRoomData();

        SwingUtilities.invokeLater(() -> {
            roomScreen.updateRoom();
            if (firstTime) {
                // vừa tạo / vào phòng
                showScreen(Screen.ROOM);
            } else if (peerLeft) {
                // Người kia bấm "Thoát" ở bảng kết quả -> mình quay về giao diện phòng chờ người mới
                if (gameScreen.isEndShown()) {
                    gameScreen.hideEnd();
                    stopGame();
                    showScreen(Screen.ROOM);
                }
                roomScreen.showStatus("Người chơi kia đã rời phòng. Đang chờ người chơi mới...");
            }
        });
    }

    // ── Lịch sử, thống kê, chat ─────────────────────────────────────────

    private void handleHistory(Msg msg) {
        java.util.List<Model.HistoryRow> rows = new java.util.ArrayList<>();
        for (JsonElement el : msg.data.getAsJsonArray("rows")) {
            JsonObject r = el.getAsJsonObject();
            rows.add(new Model.HistoryRow(r.get("level").getAsString(), r.get("sec").getAsDouble(),
                                          r.get("at").getAsString(),
                                          r.has("partner") ? r.get("partner").getAsString() : null));
        }
        SwingUtilities.invokeLater(() -> {
            Model.history = rows;
            historyDialog.refresh();
            roomScreen.updateHistory();
            lobbyScreen.updateHistory();
        });
    }

    private void handleStats(Msg msg) {
        int acc = msg.num("acc");
        java.util.List<Model.StatRow> rows = new java.util.ArrayList<>();
        for (JsonElement el : msg.data.getAsJsonArray("rows")) {
            JsonObject r = el.getAsJsonObject();
            rows.add(new Model.StatRow(r.get("lv").getAsInt(), r.get("level").getAsString(),
                r.get("done").getAsInt(), r.has("best") ? r.get("best").getAsDouble() : null,
                r.get("tries").getAsInt()));
        }
        SwingUtilities.invokeLater(() -> {
            Model.stats.put(acc, rows);
            // vẽ lại thẻ người chơi với số liệu mới
            roomScreen.updateRoom();
        });
    }

    /** Tin chat hiện ở cả khung chat trong phòng và khung chat nhỏ trong trận */
    private void handleChat(Msg msg) {
        Model.ChatLine line = new Model.ChatLine(msg.num("acc"), msg.str("name"), msg.str("text"));
        SwingUtilities.invokeLater(() -> {
            roomScreen.chat().append(line);
            gameScreen.chat().append(line);
        });
    }

    /** Chat tổng: chỉ hiện ở khung chat của sảnh */
    private void handleLobbyChat(Msg msg) {
        Model.ChatLine line = new Model.ChatLine(msg.num("acc"), msg.str("name"), msg.str("text"));
        SwingUtilities.invokeLater(() -> lobbyScreen.chat().append(line));
    }

    private void chatSystem(String text) {
        SwingUtilities.invokeLater(() -> {
            roomScreen.chat().system(text);
            gameScreen.chat().system(text);
        });
    }

    private void handleLeftRoom(Msg msg) {
        stopGame();
        SwingUtilities.invokeLater(gameScreen::hideEnd);
        Model.room = null;
        Model.game = null;
        showScreen(Screen.LOBBY);
    }

    // ── Trận đấu ────────────────────────────────────────────────────────

    /**
     * GAME_START: nạp màn từ file JSON để vẽ địa hình, mở UDP và gửi UDP_HELLO, chuyển sang màn chơi.
     * Trận chỉ chạy khi server nhận đủ HELLO của 2 người và gửi GAME_GO.
     */
    private void handleGameStart(Msg msg) {
        // phòng trường hợp chơi lại: đóng UDP cũ trước
        stopGame();

        Model.game = new Model.GameData();
        Model.game.lv = msg.num("lv");
        // bỏ phiếu "Màn tiếp theo" làm đổi màn
        if (Model.room != null) Model.room.lv = Model.game.lv;

        // Load level từ resources (client cũng có file JSON để vẽ địa hình)
        try {
            String filename = levelFile(Model.game.lv);
            Model.game.level = LevelLoader.load(filename);
        } catch (Exception e) {
            Log.error(TAG, "Failed to load level", "error", e.getMessage());
            return;
        }

        // Bắt đầu UDP handshake
        try {
            InetAddress addr = InetAddress.getByName(serverHost);
            udp.open(addr, udpPort);
            udp.hello(Model.room.room, Model.acc, Model.token);
        } catch (Exception e) {
            Log.error(TAG, "UDP open failed", "error", e.getMessage());
            return;
        }

        SwingUtilities.invokeLater(() -> {
            gameScreen.hideEnd();
            gameScreen.updateControls();
        });
        showScreen(Screen.GAME);
    }

    /**
     * Tìm file level ứng với id màn server gửi: lấy tên màn từ LEVELS, rồi dò các file trong
     * levels/index.txt xem file nào có "name" trùng. (id trong DB không chắc trùng số thứ tự file.)
     */
    private static String levelFile(int lv) {
        String name = null;
        for (Model.LevelInfo info : Model.levels) {
            if (info.lv() == lv) { name = info.name(); break; }
        }
        try (InputStream in = ClientApp.class.getResourceAsStream("/levels/index.txt")) {
            if (in != null) {
                String[] lines = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .trim().split("\\R");
                for (String line : lines) {
                    String file = line.trim();
                    if (file.isEmpty() || file.startsWith("#")) continue;
                    try {
                        if (LevelLoader.load(file).name.equals(name)) return file;
                    } catch (Exception ignored) {}
                }
            }
        } catch (IOException ignored) {}
        return "lv" + lv + ".json";
    }

    private void handleGameGo(Msg msg) {
        // Server xác nhận cả 2 đã bắt tay UDP → bắt đầu gửi input
        udp.startInput(gameScreen::getInput);
        udp.startPing();
        Log.info(TAG, "Game GO - starting input");
    }

    /** Dừng vẽ + đóng UDP + xóa snapshot cũ */
    private void stopGame() {
        gameScreen.stopRendering();
        udp.disconnect();
        Interp.clear();
    }

    private void handleLevelComplete(Msg msg) {
        double sec = msg.data.get("sec").getAsDouble();
        if (Model.game != null) Model.game.won = true;
        boolean hasNext = Model.room != null && nextLevelId(Model.room.lv) > 0;
        showEndLater(true, "Hoàn thành màn trong " + String.format("%.2f", sec) + " giây!"
                + (hasNext ? "" : " Đây là màn cuối cùng."), hasNext);
    }

    private void handleLevelFailed(Msg msg) {
        boolean meDied = msg.data.has("acc") && msg.data.get("acc").getAsInt() == Model.acc;
        showEndLater(false, meDied ? "Bạn đã chết. Thử lại nhé!" : "Người chơi kia đã chết. Thử lại nhé!", false);
    }

    /**
     * Sau khi kết thúc màn: giữ nguyên màn chơi (khung hình cuối vẫn hiện, hiệu ứng vẫn chạy),
     * chỉ đóng UDP và hiện bảng kết quả để 2 người bỏ phiếu Chơi lại / Màn tiếp theo / Thoát.
     */
    private void showEndLater(boolean won, String text, boolean hasNext) {
        // Đợi 1 chút cho hiệu ứng/âm thanh chết hoặc về đích kịp hiện
        Timer t = new Timer(1200, e -> {
            udp.disconnect();
            gameScreen.showEnd(won, text, hasNext);
        });
        t.setRepeats(false);
        t.start();
    }

    /** id màn đứng sau màn hiện tại trong danh sách LEVELS, hoặc -1 nếu là màn cuối */
    private static int nextLevelId(int lv) {
        for (int i = 0; i < Model.levels.size() - 1; i++) {
            if (Model.levels.get(i).lv() == lv) return Model.levels.get(i + 1).lv();
        }
        return -1;
    }

    private void handleGameAbort(Msg msg) {
        String reason = msg.str("reason");
        stopGame();
        // GAME_START mới sẽ tới ngay sau
        if ("RESTART".equals(reason)) return;

        String text = switch (reason) {
            case "PEER_LEFT"   -> "Người chơi kia đã thoát khỏi phòng.";
            case "UDP_TIMEOUT" -> "Mất kết nối UDP với server.";
            default            -> "Trận đấu bị hủy: " + reason;
        };
        showScreen(Screen.ROOM);
        SwingUtilities.invokeLater(() ->
            PixelDialog.message(this, "Trận đấu kết thúc", text, PixelDialog.Kind.WARNING));
    }

    // Tin ERR: lỗi đăng nhập/đăng ký hiện ngay dưới form, lỗi khác hiện hộp thông báo
    private void handleError(Msg msg) {
        String code = msg.data.has("code") ? msg.data.get("code").getAsString() : "";
        String cmd = msg.data.has("cmd") ? msg.data.get("cmd").getAsString() : "";
        String error = errorText(code, msg.str("msg"));
        Log.error(TAG, "Server error", "cmd", cmd, "code", code, "msg", error);
        SwingUtilities.invokeLater(() -> {
            // Lỗi đăng ký / đăng nhập hiện ngay dưới form tương ứng và mở khóa form để nhập lại
            switch (cmd) {
                case MsgType.REGISTER -> {
                    registerScreen.setEnabled(true);
                    registerScreen.showStatus(error, false);
                }
                case MsgType.LOGIN -> {
                    loginScreen.setEnabled(true);
                    loginScreen.showStatus(error, false);
                }
                default -> PixelDialog.message(this, "Lỗi", error, PixelDialog.Kind.ERROR);
            }
        });
    }

    /** Đổi mã lỗi của server sang câu tiếng Việt dễ hiểu */
    private static String errorText(String code, String fallback) {
        return switch (code) {
            case "USER_EXISTS"       -> "Tên đăng nhập này đã có người dùng";
            case "NAME_EXISTS"       -> "Tên hiển thị này đã có người dùng, hãy chọn tên khác";
            case "BAD_LOGIN"         -> "Sai tên đăng nhập hoặc mật khẩu";
            case "ALREADY_LOGGED_IN" -> "Tài khoản này đang đăng nhập ở nơi khác";
            case "ROOM_FULL"         -> "Phòng đã đủ 2 người";
            case "ROOM_NOT_FOUND"    -> "Không tìm thấy phòng";
            case "NOT_HOST"          -> "Chỉ chủ phòng mới làm được việc này";
            case "NEED_2"            -> "Cần đủ 2 người chơi";
            case "DB_ERROR"          -> "Lỗi cơ sở dữ liệu trên server";
            default                  -> fallback;
        };
    }

    // Điểm chạy client: đọc địa chỉ server từ client.properties (ghi đè được bằng -Dserver.host) rồi mở cửa sổ
    public static void main(String[] args) {
        try {
            // Đọc cấu hình client.properties
            Properties props = new Properties();
            InputStream res = ClientApp.class.getResourceAsStream("/fw/client/resources/client.properties");
            if (res == null) res = ClientApp.class.getResourceAsStream("/client.properties");
            try (InputStream in = res) {
                if (in == null) {
                    throw new IOException("client.properties not found");
                }
                props.load(in);
            }

            // Cho phép ghi đè bằng -Dserver.host=... khi chạy trên máy khác
            String host = System.getProperty("server.host", props.getProperty("server.host", "127.0.0.1"));
            int tcpPort = Integer.parseInt(props.getProperty("tcp.port", "5000"));
            int udpPort = Integer.parseInt(props.getProperty("udp.port", "5001"));

            Log.info(TAG, "Starting client", "host", host, "tcp", tcpPort, "udp", udpPort);

            // Dùng giao diện mặc định của hệ điều hành cho các thành phần chưa tự vẽ
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Không đặt được thì dùng giao diện mặc định của Java
            }

            // Tạo và hiện cửa sổ trên luồng giao diện
            SwingUtilities.invokeLater(() -> {
                ClientApp app = new ClientApp(host, tcpPort, udpPort);
                app.setVisible(true);
            });

        } catch (Exception e) {
            Log.error(TAG, "Failed to start", "error", e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
