package fw.server.db;

import fw.common.util.Log;

import java.sql.*;

public final class AccountDao {
    private AccountDao() {}

    public record Acc(int id, String hash, String name) {}

    /**
     * Tên hiển thị đã có người dùng chưa. Collation utf8mb4_general_ci của MySQL/MariaDB
     * so sánh không phân biệt hoa/thường, nên "An" và "an" bị coi là trùng.
     */
    public static boolean nameTaken(String displayName) throws SQLException {
        String sql = "SELECT 1 FROM accounts WHERE display_name = ? LIMIT 1";
        try (Connection con = Db.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, displayName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // Tạo tài khoản và dòng thống kê đi kèm trong 1 transaction; trả về id tài khoản mới
    public static int create(String username, String hash, String displayName) throws SQLException {
        long start = System.currentTimeMillis();

        try (Connection con = Db.get()) {
            con.setAutoCommit(false);

            try {
                int accountId;

                // Thêm tài khoản, lấy id tự tăng
                String sql = "INSERT INTO accounts(username, password_hash, display_name) VALUES(?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, username);
                    ps.setString(2, hash);
                    ps.setString(3, displayName);
                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new SQLException("Failed to get generated account_id");
                        }
                        accountId = rs.getInt(1);
                    }
                }

                // Tạo sẵn dòng thống kê cho tài khoản mới
                String sql2 = "INSERT INTO player_results(account_id) VALUES(?)";
                try (PreparedStatement ps = con.prepareStatement(sql2)) {
                    ps.setInt(1, accountId);
                    ps.executeUpdate();
                }

                con.commit();

                long elapsed = System.currentTimeMillis() - start;
                Log.debug("AccountDao", "create", "username", username, "acc", accountId, "ms", elapsed);

                return accountId;

            } catch (SQLException e) {
                con.rollback();
                Log.error("AccountDao", "create failed", "username", username, "state", e.getSQLState(), "code", e.getErrorCode());
                throw e;
            }
        }
    }

    // Tìm tài khoản theo tên đăng nhập (lấy id, mật khẩu đã băm, tên hiển thị); null nếu không có
    public static Acc find(String username) throws SQLException {
        long start = System.currentTimeMillis();

        String sql = "SELECT account_id, password_hash, display_name FROM accounts WHERE username = ?";

        try (Connection con = Db.get();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Acc acc = new Acc(
                        rs.getInt("account_id"),
                        rs.getString("password_hash"),
                        rs.getString("display_name")
                    );

                    long elapsed = System.currentTimeMillis() - start;
                    Log.debug("AccountDao", "find", "username", username, "found", true, "ms", elapsed);

                    return acc;
                } else {
                    long elapsed = System.currentTimeMillis() - start;
                    Log.debug("AccountDao", "find", "username", username, "found", false, "ms", elapsed);

                    return null;
                }
            }

        } catch (SQLException e) {
            Log.error("AccountDao", "find failed", "username", username, "state", e.getSQLState(), "code", e.getErrorCode());
            throw e;
        }
    }
}
