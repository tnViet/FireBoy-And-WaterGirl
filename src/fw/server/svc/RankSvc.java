package fw.server.svc;

import fw.common.net.Err;
import fw.common.net.Msg;
import fw.common.net.MsgType;
import fw.common.util.Log;
import fw.server.db.ResultDao;
import fw.server.net.ClientHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Xử lý GET_RANK: trả bảng xếp hạng tổng (ALL) hoặc theo từng màn (LEVEL).
 */
public final class RankSvc {
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private RankSvc() {}

    /**
     * GET_RANK: kind = ALL (xếp theo số màn đã qua) hoặc LEVEL (xếp theo thời gian 1 màn).
     * Giới hạn số dòng trong khoảng cho phép; lỗi DB thì trả ERR.
     */
    public static void get(ClientHandler h, Msg m) {
        String kind = m.data.has("kind") ? m.data.get("kind").getAsString() : "ALL";
        int limit = m.data.has("limit") ? m.data.get("limit").getAsInt() : DEFAULT_LIMIT;
        limit = Math.max(1, Math.min(MAX_LIMIT, limit));

        JsonObject data = new JsonObject();
        JsonArray rows = new JsonArray();
        data.addProperty("kind", kind);

        try {
            switch (kind) {
                case "ALL" -> {
                    // Xếp theo số màn hoàn thành
                    int pos = 1;
                    for (ResultDao.RowAll r : ResultDao.rankAll(limit)) {
                        JsonObject row = new JsonObject();
                        row.addProperty("pos", pos++);
                        row.addProperty("name", r.name());
                        row.addProperty("done", r.done());
                        row.addProperty("plays", r.plays());
                        rows.add(row);
                    }
                }
                case "LEVEL" -> {
                    // Xếp theo thời gian hoàn thành tốt nhất của 1 màn
                    if (!m.data.has("lv")) {
                        h.err(m, Err.BAD_INPUT, "lv required");
                        return;
                    }
                    int lv = m.num("lv");
                    if (Levels.get(lv) == null) {
                        h.err(m, Err.LEVEL_NOT_FOUND, "level not found");
                        return;
                    }
                    data.addProperty("lv", lv);
                    int pos = 1;
                    for (ResultDao.RowLv r : ResultDao.rankLevel(lv, limit)) {
                        JsonObject row = new JsonObject();
                        row.addProperty("pos", pos++);
                        row.addProperty("name", r.name());
                        row.addProperty("sec", r.sec());
                        row.addProperty("tries", r.tries());
                        rows.add(row);
                    }
                }
                default -> {
                    h.err(m, Err.BAD_INPUT, "kind must be ALL or LEVEL");
                    return;
                }
            }
        } catch (Exception e) {
            Log.error("RankSvc", "query failed", "kind", kind, "error", e.getMessage());
            h.err(m, Err.DB_ERROR, "database error");
            return;
        }

        data.add("rows", rows);
        h.reply(m, MsgType.RANK, data);
    }

    /** GET_HISTORY: các màn chính mình đã hoàn thành, mới nhất trước */
    public static void history(ClientHandler h, Msg m) {
        int limit = m.data.has("limit") ? m.data.get("limit").getAsInt() : 50;
        limit = Math.max(1, Math.min(MAX_LIMIT, limit));
        JsonArray rows = new JsonArray();
        try {
            for (ResultDao.RowHist r : ResultDao.history(h.sess.acc, limit)) {
                JsonObject row = new JsonObject();
                row.addProperty("lv", r.lv());
                row.addProperty("level", r.level());
                row.addProperty("sec", r.sec());
                row.addProperty("at", r.at());
                if (r.partner() != null) row.addProperty("partner", r.partner());
                rows.add(row);
            }
        } catch (Exception e) {
            Log.error("RankSvc", "history failed", "error", e.getMessage());
            h.err(m, Err.DB_ERROR, "database error");
            return;
        }
        JsonObject data = new JsonObject();
        data.add("rows", rows);
        h.reply(m, MsgType.HISTORY, data);
    }

    /** GET_STATS {acc}: thống kê từng màn của 1 người (dùng cho thẻ người chơi trong phòng) */
    public static void stats(ClientHandler h, Msg m) {
        int acc = m.data.has("acc") ? m.num("acc") : h.sess.acc;
        JsonArray rows = new JsonArray();
        try {
            for (ResultDao.RowStat r : ResultDao.stats(acc)) {
                // bỏ màn cũ không còn dùng
                if (Levels.get(r.lv()) == null) continue;
                JsonObject row = new JsonObject();
                row.addProperty("lv", r.lv());
                row.addProperty("level", r.level());
                row.addProperty("done", r.done());
                if (r.best() != null) row.addProperty("best", r.best());
                row.addProperty("tries", r.tries());
                rows.add(row);
            }
        } catch (Exception e) {
            Log.error("RankSvc", "stats failed", "error", e.getMessage());
            h.err(m, Err.DB_ERROR, "database error");
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty("acc", acc);
        data.add("rows", rows);
        h.reply(m, MsgType.STATS, data);
    }
}
