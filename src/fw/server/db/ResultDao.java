package fw.server.db;

import fw.common.util.Log;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ResultDao {
    private ResultDao() {}

    public record RowAll(String name, int done, int plays) {}
    public record RowLv(String name, double sec, int tries) {}
    /** 1 lần hoàn thành màn: id màn, tên màn, thời gian (giây), thời điểm hoàn thành */
    /** partner: tên người chơi cùng, null nếu ván cũ chưa lưu */
    public record RowHist(int lv, String level, double sec, String at, String partner) {}
    /** Thống kê 1 màn của 1 người: số lần hoàn thành, thời gian tốt nhất (null nếu chưa qua), số lần thử */
    public record RowStat(int lv, String level, int done, Double best, int tries) {}

    /** Các lần hoàn thành màn của 1 tài khoản, mới nhất trước (bảng level_completions) */
    public static List<RowHist> history(int acc, int limit) throws SQLException {
        // DATE_FORMAT ở MySQL: giữ đúng giờ đã lưu, tránh JDBC tự đổi múi giờ (serverTimezone=UTC)
        String sql = "SELECT c.level_id, l.level_name, c.completion_time, " +
                     "       DATE_FORMAT(c.completed_at, '%d/%m/%Y %H:%i') AS at_text, p.display_name " +
                     "FROM level_completions c JOIN levels l ON l.level_id = c.level_id " +
                     "LEFT JOIN accounts p ON p.account_id = c.partner_id " +
                     "WHERE c.account_id = ? " +
                     "ORDER BY c.completed_at DESC, c.completion_id DESC LIMIT ?";
        List<RowHist> result = new ArrayList<>();
        try (Connection con = Db.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, acc);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new RowHist(rs.getInt(1), rs.getString(2), rs.getDouble(3), rs.getString(4), rs.getString(5)));
                }
            }
        }
        return result;
    }

    /** Mỗi màn: số lần hoàn thành, thời gian tốt nhất, số lần thử của 1 tài khoản */
    public static List<RowStat> stats(int acc) throws SQLException {
        String sql = "SELECT l.level_id, l.level_name, COUNT(c.completion_id) AS done, " +
                     "       MIN(c.completion_time) AS best, COALESCE(MAX(s.attempts), 0) AS tries " +
                     "FROM levels l " +
                     "LEFT JOIN level_completions c ON c.level_id = l.level_id AND c.account_id = ? " +
                     "LEFT JOIN player_level_stats s ON s.level_id = l.level_id AND s.account_id = ? " +
                     "GROUP BY l.level_id, l.level_name ORDER BY l.level_id";
        List<RowStat> result = new ArrayList<>();
        try (Connection con = Db.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, acc);
            ps.setInt(2, acc);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double best = rs.getDouble("best");
                    result.add(new RowStat(rs.getInt(1), rs.getString(2), rs.getInt("done"),
                                           rs.wasNull() ? null : best, rs.getInt("tries")));
                }
            }
        }
        return result;
    }

    // Bắt đầu trận: tăng số lần thử màn này và tổng số lượt chơi của cả 2 người, trong 1 transaction
    public static void onStart(int acc1, int acc2, int lv) throws SQLException {
        long start = System.currentTimeMillis();

        try (Connection con = Db.get()) {
            con.setAutoCommit(false);

            try {
                String sql1 = "INSERT INTO player_level_stats(account_id, level_id, attempts) VALUES(?, ?, 1) " +
                             "ON DUPLICATE KEY UPDATE attempts = attempts + 1";
                String sql2 = "UPDATE player_results SET total_plays = total_plays + 1 WHERE account_id = ?";

                for (int acc : new int[]{acc1, acc2}) {
                    try (PreparedStatement ps = con.prepareStatement(sql1)) {
                        ps.setInt(1, acc);
                        ps.setInt(2, lv);
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps = con.prepareStatement(sql2)) {
                        ps.setInt(1, acc);
                        ps.executeUpdate();
                    }
                }

                con.commit();

                long elapsed = System.currentTimeMillis() - start;
                Log.debug("ResultDao", "onStart", "acc1", acc1, "acc2", acc2, "lv", lv, "ms", elapsed);

            } catch (SQLException e) {
                con.rollback();
                Log.error("ResultDao", "onStart failed", "state", e.getSQLState(), "code", e.getErrorCode());
                throw e;
            }
        }
    }

    /**
     * Thắng màn: mỗi người thêm 1 dòng hoàn thành (kèm người chơi cùng).
     * Lần đầu qua màn này thì tăng số màn đã hoàn thành. Tất cả trong 1 transaction, lỗi thì rollback.
     */
    public static void onWin(int acc1, int acc2, int lv, double sec) throws SQLException {
        long start = System.currentTimeMillis();

        try (Connection con = Db.get()) {
            con.setAutoCommit(false);

            try {
                String checkSql = "SELECT 1 FROM level_completions WHERE account_id = ? AND level_id = ? LIMIT 1";
                String insertSql = "INSERT INTO level_completions(account_id, level_id, completion_time, partner_id) VALUES(?, ?, ?, ?)";
                String updateSql = "UPDATE player_results SET levels_completed = levels_completed + 1, last_completed_at = NOW() WHERE account_id = ?";

                for (int acc : new int[]{acc1, acc2}) {
                    boolean first;

                    try (PreparedStatement ps = con.prepareStatement(checkSql)) {
                        ps.setInt(1, acc);
                        ps.setInt(2, lv);
                        try (ResultSet rs = ps.executeQuery()) {
                            first = !rs.next();
                        }
                    }

                    try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                        ps.setInt(1, acc);
                        ps.setInt(2, lv);
                        ps.setDouble(3, sec);
                        // Người chơi cùng là người còn lại trong cặp
                        ps.setInt(4, acc == acc1 ? acc2 : acc1);
                        ps.executeUpdate();
                    }

                    if (first) {
                        try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                            ps.setInt(1, acc);
                            ps.executeUpdate();
                        }
                    }
                }

                con.commit();

                long elapsed = System.currentTimeMillis() - start;
                Log.debug("ResultDao", "onWin", "acc1", acc1, "acc2", acc2, "lv", lv, "sec", sec, "ms", elapsed);

            } catch (SQLException e) {
                con.rollback();
                Log.error("ResultDao", "onWin failed", "state", e.getSQLState(), "code", e.getErrorCode());
                throw e;
            }
        }
    }

    /**
     * Xếp hạng tổng: nhiều màn hoàn thành hơn đứng trên; bằng nhau thì ít lượt chơi hơn,
     * rồi ai đạt trước đứng trên
     */
    public static List<RowAll> rankAll(int limit) throws SQLException {
        long start = System.currentTimeMillis();

        String sql = "SELECT a.display_name, r.levels_completed, r.total_plays " +
                    "FROM player_results r JOIN accounts a ON a.account_id = r.account_id " +
                    "WHERE r.levels_completed > 0 " +
                    "ORDER BY r.levels_completed DESC, r.total_plays ASC, r.last_completed_at ASC, r.account_id ASC " +
                    "LIMIT ?";

        List<RowAll> result = new ArrayList<>();

        try (Connection con = Db.get();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new RowAll(
                        rs.getString("display_name"),
                        rs.getInt("levels_completed"),
                        rs.getInt("total_plays")
                    ));
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        Log.debug("ResultDao", "rankAll", "count", result.size(), "ms", elapsed);

        return result;
    }

    /**
     * Xếp hạng 1 màn theo thời gian nhanh nhất: mỗi người chỉ lấy lần tốt nhất
     * (ROW_NUMBER theo từng tài khoản), bằng thời gian thì ai đạt trước đứng trên
     */
    public static List<RowLv> rankLevel(int lv, int limit) throws SQLException {
        long start = System.currentTimeMillis();

        String sql = "WITH ranked AS ( " +
                    "  SELECT c.account_id, c.completion_time, c.completed_at, " +
                    "         ROW_NUMBER() OVER (PARTITION BY c.account_id " +
                    "                            ORDER BY c.completion_time ASC, c.completed_at ASC) AS rn " +
                    "  FROM level_completions c " +
                    "  WHERE c.level_id = ? " +
                    ") " +
                    "SELECT a.display_name, r.completion_time, COALESCE(s.attempts, 0) AS tries " +
                    "FROM ranked r " +
                    "JOIN accounts a ON a.account_id = r.account_id " +
                    "LEFT JOIN player_level_stats s ON s.account_id = r.account_id AND s.level_id = ? " +
                    "WHERE r.rn = 1 " +
                    "ORDER BY r.completion_time ASC, tries ASC, r.completed_at ASC, r.account_id ASC " +
                    "LIMIT ?";

        List<RowLv> result = new ArrayList<>();

        try (Connection con = Db.get();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, lv);
            ps.setInt(2, lv);
            ps.setInt(3, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new RowLv(
                        rs.getString("display_name"),
                        rs.getDouble("completion_time"),
                        rs.getInt("tries")
                    ));
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        Log.debug("ResultDao", "rankLevel", "lv", lv, "count", result.size(), "ms", elapsed);

        return result;
    }
}
