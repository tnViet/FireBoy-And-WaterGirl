package fw.server.db;

import fw.common.game.Level;
import fw.common.game.LevelLoader;
import fw.common.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class LevelDao {
    private LevelDao() {}

    public record LevelRow(int id, String name, String json) {}

    /** Tên các màn đang có trong levels/index.txt (màn cũ trong DB nhưng đã bỏ khỏi index sẽ bị ẩn) */
    public static final java.util.Set<String> activeNames = new java.util.HashSet<>();

    /**
     * Đồng bộ màn từ thư mục levels vào DB lúc server khởi động: đọc danh sách trong index.txt,
     * nạp thử từng màn rồi thêm mới hoặc cập nhật theo tên màn. Ghi nhớ tên các màn đang dùng.
     */
    public static void sync() throws Exception {
        long start = System.currentTimeMillis();

        InputStream is = LevelDao.class.getClassLoader().getResourceAsStream("levels/index.txt");
        if (is == null) {
            throw new IllegalStateException("levels/index.txt not found");
        }

        List<String> files = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    files.add(line);
                }
            }
        }

        int synced = 0;

        try (Connection con = Db.get()) {
            String sql = "INSERT INTO levels(level_name, data) VALUES(?, ?) " +
                        "ON DUPLICATE KEY UPDATE data = VALUES(data)";

            for (String file : files) {
                String path = "levels/" + file;
                InputStream levelStream = LevelDao.class.getClassLoader().getResourceAsStream(path);
                if (levelStream == null) {
                    throw new IllegalStateException("Level file not found: " + path);
                }

                StringBuilder json = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(levelStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        json.append(line);
                    }
                }

                String jsonStr = json.toString();

                // Nạp thử để chắc màn hợp lệ trước khi ghi vào DB
                Level level = LevelLoader.fromJson(jsonStr);
                activeNames.add(level.name);

                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, level.name);
                    ps.setString(2, jsonStr);
                    ps.executeUpdate();
                    synced++;
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        Log.info("LevelDao", "sync complete", "count", synced, "ms", elapsed);
    }

    // Lấy mọi màn trong DB theo thứ tự id
    public static List<LevelRow> all() throws SQLException {
        long start = System.currentTimeMillis();

        String sql = "SELECT level_id, level_name, data FROM levels ORDER BY level_id";
        List<LevelRow> result = new ArrayList<>();

        try (Connection con = Db.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(new LevelRow(
                    rs.getInt("level_id"),
                    rs.getString("level_name"),
                    rs.getString("data")
                ));
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        Log.debug("LevelDao", "all", "count", result.size(), "ms", elapsed);

        return result;
    }
}
