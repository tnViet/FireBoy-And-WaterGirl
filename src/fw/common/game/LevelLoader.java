package fw.common.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fw.common.net.Const;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Đọc file màn (JSON, đơn vị tile) thành Level (đơn vị pixel) và kiểm tra màn có hợp lệ không.
 * Màn sai (thiếu tường viền, trùng id, cần gạt trỏ tới cơ quan không tồn tại...) sẽ ném LevelException.
 */
public class LevelLoader {
    // Đọc màn từ thư mục /levels trong classpath
    public static Level load(String filename) throws LevelException {
        try {
            InputStream is = LevelLoader.class.getResourceAsStream("/levels/" + filename);
            if (is == null) {
                throw new LevelException("File not found: " + filename);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            return parse(filename, root);
        } catch (Exception e) {
            if (e instanceof LevelException) {
                throw (LevelException) e;
            }
            throw new LevelException("Failed to load " + filename + ": " + e.getMessage());
        }
    }

    /** Đọc level từ nội dung JSON (server lưu JSON của level trong DB) */
    public static Level fromJson(String json) throws LevelException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return parse(root.has("name") ? root.get("name").getAsString() : "level", root);
        } catch (Exception e) {
            if (e instanceof LevelException) {
                throw (LevelException) e;
            }
            throw new LevelException("Invalid level JSON: " + e.getMessage());
        }
    }

    /**
     * Phân tích JSON: lưới ô tường ('#' là tường, '.' là ô trống), điểm xuất phát, rồi từng loại vật thể.
     * Mọi id dùng chung 1 tập để không trùng nhau; tọa độ tile được nhân TILE ra pixel.
     */
    private static Level parse(String filename, JsonObject root) throws LevelException {
        String name = root.get("name").getAsString();
        JsonArray tilesArray = root.getAsJsonArray("tiles");

        if (tilesArray.size() == 0) {
            throw new LevelException(filename + ": tiles is empty");
        }

        int rows = tilesArray.size();
        int cols = tilesArray.get(0).getAsString().length();
        boolean[][] solid = new boolean[rows][cols];

        for (int r = 0; r < rows; r++) {
            String line = tilesArray.get(r).getAsString();
            if (line.length() != cols) {
                throw new LevelException(filename + ": inconsistent row length at row " + r);
            }
            for (int c = 0; c < cols; c++) {
                char ch = line.charAt(c);
                if (ch != '#' && ch != '.') {
                    throw new LevelException(filename + ": invalid tile character '" + ch + "' at row " + r + " col " + c);
                }
                solid[r][c] = (ch == '#');
            }
        }

        // Viền ngoài bản đồ phải là tường kín để không ai rơi ra ngoài
        for (int c = 0; c < cols; c++) {
            if (!solid[0][c] || !solid[rows - 1][c]) {
                throw new LevelException(filename + ": border not solid");
            }
        }
        for (int r = 0; r < rows; r++) {
            if (!solid[r][0] || !solid[r][cols - 1]) {
                throw new LevelException(filename + ": border not solid");
            }
        }

        // Điểm xuất phát: 2 nhân vật khác ô, đứng trên nền; nhân vật đặt giữa ô theo chiều ngang, sát đáy ô
        JsonObject spawnObj = root.getAsJsonObject("spawn");
        JsonObject fireSp = spawnObj.getAsJsonObject("FIRE");
        JsonObject waterSp = spawnObj.getAsJsonObject("WATER");

        int fx = fireSp.get("x").getAsInt();
        int fy = fireSp.get("y").getAsInt();
        int wx = waterSp.get("x").getAsInt();
        int wy = waterSp.get("y").getAsInt();

        if (fx == wx && fy == wy) {
            throw new LevelException(filename + ": spawn positions are identical");
        }

        checkSpawn(filename, solid, rows, cols, fx, fy, "FIRE");
        checkSpawn(filename, solid, rows, cols, wx, wy, "WATER");

        float fireX = fx * Const.TILE + (Const.TILE - Phys.PW) / 2;
        float fireY = (fy + 1) * Const.TILE - Phys.PH;
        float waterX = wx * Const.TILE + (Const.TILE - Phys.PW) / 2;
        float waterY = (wy + 1) * Const.TILE - Phys.PH;

        Level.SpawnDef spawnFire = new Level.SpawnDef(fireX, fireY);
        Level.SpawnDef spawnWater = new Level.SpawnDef(waterX, waterY);

        // Mỗi màn đúng 1 chìa và 1 cửa; chìa đặt giữa ô
        JsonArray keysArr = root.getAsJsonArray("keys");
        JsonArray doorsArr = root.getAsJsonArray("doors");

        if (keysArr.size() != 1) {
            throw new LevelException(filename + ": must have exactly 1 key");
        }
        if (doorsArr.size() != 1) {
            throw new LevelException(filename + ": must have exactly 1 door");
        }

        Set<String> ids = new HashSet<>();
        List<Level.KeyDef> keys = new ArrayList<>();
        for (JsonElement el : keysArr) {
            JsonObject obj = el.getAsJsonObject();
            String id = obj.get("id").getAsString();
            if (!ids.add(id)) {
                throw new LevelException(filename + ": duplicate id " + id);
            }
            int kx = obj.get("x").getAsInt();
            int ky = obj.get("y").getAsInt();
            float px = kx * Const.TILE + (Const.TILE - Phys.KEY_S) / 2;
            float py = ky * Const.TILE + (Const.TILE - Phys.KEY_S) / 2;
            keys.add(new Level.KeyDef(id, px, py));
        }

        List<Level.DoorDef> doors = new ArrayList<>();
        for (JsonElement el : doorsArr) {
            JsonObject obj = el.getAsJsonObject();
            String id = obj.get("id").getAsString();
            if (!ids.add(id)) {
                throw new LevelException(filename + ": duplicate id " + id);
            }
            float dx = getFloat(obj, "x") * Const.TILE;
            float dy = getFloat(obj, "y") * Const.TILE;
            float dw = getFloat(obj, "w") * Const.TILE;
            float dh = getFloat(obj, "h") * Const.TILE;
            doors.add(new Level.DoorDef(id, dx, dy, dw, dh));
        }

        JsonArray rocksArr = root.getAsJsonArray("rocks");
        if (rocksArr.size() > 6) {
            throw new LevelException(filename + ": too many rocks (max 6)");
        }
        List<Level.RockDef> rocks = new ArrayList<>();
        for (JsonElement el : rocksArr) {
            JsonObject obj = el.getAsJsonObject();
            String id = obj.get("id").getAsString();
            if (!ids.add(id)) {
                throw new LevelException(filename + ": duplicate id " + id);
            }
            int rx = obj.get("x").getAsInt();
            int ry = obj.get("y").getAsInt();
            rocks.add(new Level.RockDef(id, rx * Const.TILE, ry * Const.TILE));
        }

        // Vùng nguy hiểm: loại phải là LAVA, WATER hoặc POISON
        JsonArray zonesArr = root.getAsJsonArray("zones");
        List<Zone> zones = new ArrayList<>();
        for (JsonElement el : zonesArr) {
            JsonObject obj = el.getAsJsonObject();
            String id = obj.get("id").getAsString();
            if (!ids.add(id)) {
                throw new LevelException(filename + ": duplicate id " + id);
            }
            String kindStr = obj.get("kind").getAsString();
            ZoneKind kind;
            try {
                kind = ZoneKind.valueOf(kindStr);
            } catch (IllegalArgumentException e) {
                throw new LevelException(filename + ": invalid zone kind " + kindStr);
            }
            float zx = getFloat(obj, "x") * Const.TILE;
            float zy = getFloat(obj, "y") * Const.TILE;
            float zw = getFloat(obj, "w") * Const.TILE;
            float zh = getFloat(obj, "h") * Const.TILE;
            zones.add(new Zone(id, kind, zx, zy, zw, zh));
        }

        // Cần gạt: đặt giữa ô, sát đáy ô; target là id cơ quan mà nó điều khiển
        JsonArray leversArr = root.getAsJsonArray("levers");
        if (leversArr.size() > 6) {
            throw new LevelException(filename + ": too many levers (max 6)");
        }
        List<Level.LeverDef> levers = new ArrayList<>();
        for (JsonElement el : leversArr) {
            JsonObject obj = el.getAsJsonObject();
            String id = obj.get("id").getAsString();
            if (!ids.add(id)) {
                throw new LevelException(filename + ": duplicate id " + id);
            }
            int lx = obj.get("x").getAsInt();
            int ly = obj.get("y").getAsInt();
            String target = obj.get("target").getAsString();
            float px = lx * Const.TILE + (Const.TILE - Phys.LEVER_W) / 2;
            float py = (ly + 1) * Const.TILE - Phys.LEVER_H;
            levers.add(new Level.LeverDef(id, px, py, target));
        }

        JsonArray platesArr = root.getAsJsonArray("plates");
        if (platesArr.size() > 6) {
            throw new LevelException(filename + ": too many plates (max 6)");
        }
        List<Level.PlateDef> plates = new ArrayList<>();
        for (JsonElement el : platesArr) {
            JsonObject obj = el.getAsJsonObject();
            String id = obj.get("id").getAsString();
            if (!ids.add(id)) {
                throw new LevelException(filename + ": duplicate id " + id);
            }
            float px = getFloat(obj, "x") * Const.TILE;
            float py = getFloat(obj, "y") * Const.TILE;
            float pw = getFloat(obj, "w") * Const.TILE;
            float ph = getFloat(obj, "h") * Const.TILE;
            String target = obj.get("target").getAsString();
            plates.add(new Level.PlateDef(id, px, py, pw, ph, target));
        }

        JsonArray elevsArr = root.getAsJsonArray("elevators");
        if (elevsArr.size() > 4) {
            throw new LevelException(filename + ": too many elevators (max 4)");
        }
        List<Level.MechDef> elevators = parseMechs(filename, elevsArr, ids);

        JsonArray gatesArr = root.getAsJsonArray("gates");
        if (gatesArr.size() > 6) {
            throw new LevelException(filename + ": too many gates (max 6)");
        }
        List<Level.MechDef> gates = parseMechs(filename, gatesArr, ids);

        // Mọi cần gạt và nút bấm phải trỏ tới một thang máy hoặc cổng chắn có thật
        Set<String> mechIds = new HashSet<>();
        for (Level.MechDef m : elevators) mechIds.add(m.id);
        for (Level.MechDef m : gates) mechIds.add(m.id);

        for (Level.LeverDef l : levers) {
            if (!mechIds.contains(l.target)) {
                throw new LevelException(filename + ": lever " + l.id + " targets unknown mech " + l.target);
            }
        }
        for (Level.PlateDef p : plates) {
            if (!mechIds.contains(p.target)) {
                throw new LevelException(filename + ": plate " + p.id + " targets unknown mech " + p.target);
            }
        }

        return new Level(name, cols, rows, solid, spawnFire, spawnWater, keys, doors, rocks, zones, levers, plates, elevators, gates);
    }

    // Điểm xuất phát phải nằm trong bản đồ, ở ô trống và ngay trên một ô tường
    private static void checkSpawn(String filename, boolean[][] solid, int rows, int cols, int x, int y, String el) throws LevelException {
        if (x < 0 || x >= cols || y < 0 || y >= rows) {
            throw new LevelException(filename + ": " + el + " spawn out of bounds");
        }
        if (solid[y][x]) {
            throw new LevelException(filename + ": " + el + " spawn on solid tile");
        }
        if (y + 1 >= rows || !solid[y + 1][x]) {
            throw new LevelException(filename + ": " + el + " spawn has no ground below");
        }
    }

    // Đọc thang máy / cổng chắn: trục chạy x hoặc y, quãng đường khác 0, tốc độ 0,5 đến 4 tile/giây
    private static List<Level.MechDef> parseMechs(String filename, JsonArray arr, Set<String> ids) throws LevelException {
        List<Level.MechDef> mechs = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            String id = obj.get("id").getAsString();
            if (!ids.add(id)) {
                throw new LevelException(filename + ": duplicate id " + id);
            }
            float mx = getFloat(obj, "x") * Const.TILE;
            float my = getFloat(obj, "y") * Const.TILE;
            float mw = getFloat(obj, "w") * Const.TILE;
            float mh = getFloat(obj, "h") * Const.TILE;
            String axisStr = obj.get("axis").getAsString();
            if (!axisStr.equals("x") && !axisStr.equals("y")) {
                throw new LevelException(filename + ": invalid axis " + axisStr + " for mech " + id);
            }
            char axis = axisStr.charAt(0);
            float dist = getFloat(obj, "dist") * Const.TILE;
            if (Math.abs(dist) < 0.01f) {
                throw new LevelException(filename + ": dist is zero for mech " + id);
            }
            float speed = getFloat(obj, "speed") * Const.TILE;
            if (speed < 0.5f * Const.TILE || speed > 4f * Const.TILE) {
                throw new LevelException(filename + ": speed out of range for mech " + id);
            }
            mechs.add(new Level.MechDef(id, mx, my, mw, mh, axis, dist, speed));
        }
        return mechs;
    }

    // Đọc số có thể là số nguyên hoặc số thực (ví dụ y = 15.5)
    private static float getFloat(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return el.getAsFloat();
        }
        return el.getAsInt();
    }
}
