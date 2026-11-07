package fw.server.svc;

import fw.common.game.Level;
import fw.common.game.LevelLoader;
import fw.common.util.Log;
import fw.server.db.LevelDao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Levels {
    private static final Map<Integer, LevelDao.LevelRow> byId = new HashMap<>();
    private static final Map<String, LevelDao.LevelRow> byName = new HashMap<>();

    private Levels() {}

    // Nạp các màn từ DB vào bộ nhớ (tra theo id và theo tên), bỏ qua màn đã gỡ khỏi index.txt
    public static void load() throws Exception {
        long start = System.currentTimeMillis();

        List<LevelDao.LevelRow> rows = LevelDao.all();

        byId.clear();
        byName.clear();

        for (LevelDao.LevelRow row : rows) {
            // Bỏ qua màn cũ không còn trong index.txt (vẫn giữ trong DB vì có thể có kết quả chơi tham chiếu tới)
            if (!LevelDao.activeNames.isEmpty() && !LevelDao.activeNames.contains(row.name())) {
                continue;
            }
            // Nạp thử để chắc màn trong DB vẫn hợp lệ
            Level level = LevelLoader.fromJson(row.json());

            byId.put(row.id(), row);
            byName.put(row.name(), row);
        }

        long elapsed = System.currentTimeMillis() - start;
        Log.info("Levels", "loaded", "count", byId.size(), "ms", elapsed);
    }

    public static LevelDao.LevelRow get(int id) {
        return byId.get(id);
    }

    public static LevelDao.LevelRow getByName(String name) {
        return byName.get(name);
    }

    public static List<LevelDao.LevelRow> all() {
        // Sắp theo id để màn 1, 2, 3... luôn đúng thứ tự (HashMap không giữ thứ tự)
        return byId.values().stream()
                .sorted(java.util.Comparator.comparingInt(LevelDao.LevelRow::id))
                .toList();
    }
}
