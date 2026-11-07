package fw.common.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fw.common.net.Const;
import fw.common.net.Json;
import fw.common.net.Msg;
import fw.common.net.MsgType;

import java.util.HashMap;
import java.util.Map;

/**
 * Snapshot: ảnh chụp trạng thái World mà server gửi qua UDP 30 lần/giây.
 * Chỉ gồm dữ liệu thay đổi trong ván (vị trí, trạng thái bật/tắt); phần tĩnh client tự lấy từ Level.
 */
public class Snap {
    /**
     * Đóng gói World thành JSON: tick, số thứ tự input cuối server đã nhận của từng người (ack),
     * người chơi, đá, cơ quan (chỉ gửi tiến độ p), chìa, cửa, cần gạt, nút.
     * Tọa độ làm tròn để gói nhỏ; trả về null nếu vượt giới hạn 1 gói UDP.
     */
    public static String write(World w, int[] ack) {
        JsonObject data = new JsonObject();
        data.addProperty("tick", w.tick);

        JsonObject ackObj = new JsonObject();
        if (ack != null && ack.length == 2) {
            ackObj.addProperty(String.valueOf(w.pl[0].acc), ack[0]);
            ackObj.addProperty(String.valueOf(w.pl[1].acc), ack[1]);
        }
        data.add("ack", ackObj);

        JsonArray players = new JsonArray();
        for (Player p : w.pl) {
            JsonObject po = new JsonObject();
            po.addProperty("acc", p.acc);
            po.addProperty("x", round(p.x, 1));
            po.addProperty("y", round(p.y, 1));
            po.addProperty("face", p.face);
            po.addProperty("air", !p.onGround);
            po.addProperty("alive", p.alive);
            po.addProperty("done", p.done);
            players.add(po);
        }
        data.add("players", players);

        JsonArray rocks = new JsonArray();
        for (Rock r : w.rocks) {
            JsonObject ro = new JsonObject();
            ro.addProperty("id", r.id);
            ro.addProperty("x", round(r.x, 1));
            ro.addProperty("y", round(r.y, 1));
            rocks.add(ro);
        }
        data.add("rocks", rocks);

        JsonArray elevs = new JsonArray();
        JsonArray gates = new JsonArray();
        for (Mech m : w.mechs) {
            JsonObject mo = new JsonObject();
            mo.addProperty("id", m.id);
            mo.addProperty("p", round(m.p, 3));
            if (m.kind == MechKind.ELEV) {
                elevs.add(mo);
            } else {
                gates.add(mo);
            }
        }
        data.add("elevs", elevs);
        data.add("gates", gates);

        JsonArray keys = new JsonArray();
        for (Key k : w.keys) {
            JsonObject ko = new JsonObject();
            ko.addProperty("id", k.id);
            ko.addProperty("got", k.got);
            keys.add(ko);
        }
        data.add("keys", keys);

        JsonArray doors = new JsonArray();
        for (Door d : w.doors) {
            JsonObject dobj = new JsonObject();
            dobj.addProperty("id", d.id);
            dobj.addProperty("open", d.open);
            JsonArray passed = new JsonArray();
            for (int acc : d.passed) {
                passed.add(acc);
            }
            dobj.add("passed", passed);
            doors.add(dobj);
        }
        data.add("doors", doors);

        JsonArray levers = new JsonArray();
        for (Lever lv : w.levers) {
            JsonObject lo = new JsonObject();
            lo.addProperty("id", lv.id);
            lo.addProperty("on", lv.on);
            levers.add(lo);
        }
        data.add("levers", levers);

        JsonArray plates = new JsonArray();
        for (Plate p : w.plates) {
            JsonObject po = new JsonObject();
            po.addProperty("id", p.id);
            po.addProperty("on", p.on);
            plates.add(po);
        }
        data.add("plates", plates);

        Msg msg = new Msg(MsgType.SNAP, data);
        String json = Json.str(msg);

        if (json.length() > Const.MAX_UDP) {
            return null;
        }

        return json;
    }

    public static SnapData read(String json) {
        Msg msg = Json.parse(json);
        return new SnapData(msg.data);
    }

    private static double round(float val, int digits) {
        double mult = Math.pow(10, digits);
        return Math.round(val * mult) / mult;
    }

    // Snapshot đã đọc ở client: giữ nguyên các mảng JSON để GamePanel lấy theo id
    public static class SnapData {
        public final int tick;
        public final Map<Integer, Integer> ack;
        public final JsonArray players;
        public final JsonArray rocks;
        public final JsonArray elevs;
        public final JsonArray gates;
        public final JsonArray keys;
        public final JsonArray doors;
        public final JsonArray levers;
        public final JsonArray plates;

        public SnapData(JsonObject data) {
            this.tick = data.get("tick").getAsInt();
            this.ack = new HashMap<>();
            JsonObject ackObj = data.getAsJsonObject("ack");
            for (String key : ackObj.keySet()) {
                ack.put(Integer.parseInt(key), ackObj.get(key).getAsInt());
            }
            this.players = data.getAsJsonArray("players");
            this.rocks = data.getAsJsonArray("rocks");
            this.elevs = data.getAsJsonArray("elevs");
            this.gates = data.getAsJsonArray("gates");
            this.keys = data.getAsJsonArray("keys");
            this.doors = data.getAsJsonArray("doors");
            this.levers = data.getAsJsonArray("levers");
            this.plates = data.getAsJsonArray("plates");
        }
    }
}
