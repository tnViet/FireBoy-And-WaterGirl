package fw.client;

import fw.common.game.Level;

import java.util.ArrayList;
import java.util.List;

public final class Model {
    // Tài khoản đang đăng nhập
    public static int acc;
    public static String name;
    public static String token;

    // Dữ liệu sảnh
    public static List<LevelInfo> levels = new ArrayList<>();
    public static List<PlayerInfo> onlineList = new ArrayList<>();
    public static List<RoomInfoRow> roomList = new ArrayList<>();

    // Dữ liệu phòng
    public static RoomData room;

    // Dữ liệu trận đang chơi
    public static GameData game;

    // Lịch sử màn mình đã hoàn thành (mới nhất trước) và thống kê từng màn theo acc
    public static List<HistoryRow> history = new ArrayList<>();
    public static java.util.Map<Integer, List<StatRow>> stats = new java.util.HashMap<>();

    /** partner: tên người chơi cùng, null nếu ván cũ chưa lưu */
    public record HistoryRow(String level, double sec, String at, String partner) {
        public String partnerText() {
            return partner == null || partner.isBlank() ? "Không rõ" : partner;
        }

        /** "06/11/2026 14:35" -> "14:35 06/11" (giờ + ngày/tháng, bỏ năm cho gọn) */
        public String when() {
            String[] p = at == null ? new String[0] : at.trim().split(" ");
            if (p.length == 2 && p[0].length() >= 5) return p[1] + " " + p[0].substring(0, 5);
            return at;
        }
    }
    public record StatRow(int lv, String level, int done, Double best, int tries) {}
    public record ChatLine(int acc, String name, String text) {}

    private Model() {}

    public static boolean isHost() {
        return room != null && room.host == acc;
    }

    public static void clear() {
        acc = 0;
        name = null;
        token = null;
        levels.clear();
        onlineList.clear();
        roomList.clear();
        room = null;
        game = null;
        history.clear();
        stats.clear();
    }

    /** Màu nhân vật của 1 acc trong phòng hiện tại: "FIRE", "WATER" hoặc null nếu không còn trong phòng */
    public static String elementOf(int acc) {
        if (room == null) return null;
        for (RoomData.Member m : room.members) {
            if (m.acc() == acc) return m.el();
        }
        return null;
    }

    public record LevelInfo(int lv, String name) {}

    public record PlayerInfo(int acc, String name, String status) {}

    public record RoomInfoRow(String room, String host, int n, String status) {}

    public static class RoomData {
        public String room;
        public int host;
        public int lv;
        public List<Member> members = new ArrayList<>();

        public record Member(int acc, String name, String el) {}
    }

    public static class GameData {
        public String room;
        public int lv;
        public Level level;
        public boolean won = false;
    }
}
