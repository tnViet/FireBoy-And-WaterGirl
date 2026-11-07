package fw.server.db;

import fw.common.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class Db {
    private static String url;
    private static String user;
    private static String pass;

    private Db() {}

    // Lưu thông tin kết nối, chạy schema.sql để tạo bảng còn thiếu và nâng cấp CSDL cũ
    public static void init(String jdbcUrl, String username, String password) throws Exception {
        url = jdbcUrl;
        user = username;
        pass = password;

        Log.info("DB", "Initializing database", "url", jdbcUrl);

        try (Connection con = get()) {
            runSchema(con);
            migrate(con);
            Log.info("DB", "Schema initialized");
        } catch (SQLException e) {
            Log.error("DB", "Schema init failed", "state", e.getSQLState(), "code", e.getErrorCode(), "msg", e.getMessage());
            throw e;
        }
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    /**
     * Nâng cấp CSDL đã tạo từ bản cũ: CREATE TABLE IF NOT EXISTS không thêm cột vào bảng có sẵn,
     * nên tự kiểm tra rồi thêm. Ván cũ có partner_id = NULL (không rõ chơi cùng ai).
     */
    private static void migrate(Connection con) throws SQLException {
        String check = "SELECT 1 FROM information_schema.COLUMNS " +
                       "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'level_completions' AND COLUMN_NAME = 'partner_id'";
        boolean has;
        try (Statement st = con.createStatement(); java.sql.ResultSet rs = st.executeQuery(check)) {
            has = rs.next();
        }
        if (!has) {
            try (Statement st = con.createStatement()) {
                st.execute("ALTER TABLE level_completions ADD COLUMN partner_id INT NULL, " +
                           "ADD FOREIGN KEY (partner_id) REFERENCES accounts(account_id)");
            }
            Log.info("DB", "Migrated: level_completions.partner_id added");
        }
    }

    // Đọc schema.sql trong classpath, tách thành từng câu lệnh theo dấu ; ở cuối dòng rồi chạy lần lượt
    private static void runSchema(Connection con) throws Exception {
        InputStream is = Db.class.getClassLoader().getResourceAsStream("schema.sql");
        if (is == null) {
            throw new IllegalStateException("schema.sql not found in resources");
        }

        List<String> statements = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--")) continue;

                sb.append(line).append(" ");

                if (line.endsWith(";")) {
                    statements.add(sb.toString());
                    sb.setLength(0);
                }
            }
        }

        try (Statement stmt = con.createStatement()) {
            for (String sql : statements) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql);
                }
            }
        }
    }
}
