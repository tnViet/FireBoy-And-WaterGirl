package fw.common.game;

import com.google.gson.JsonObject;
import fw.common.net.Msg;

/**
 * Sự kiện phát sinh trong 1 tick (lấy chìa, vào cửa, gạt cần, nút đổi, cơ quan chạy/dừng, chết, thắng, thua).
 * Các hàm tạo phụ ứng với từng nhóm sự kiện, chỉ điền các trường cần thiết.
 */
public record Ev(String type, String id, int acc, String info, boolean on, double sec) {
    public Ev(String type, String id, int acc) {
        this(type, id, acc, null, false, 0);
    }

    public Ev(String type, String id, boolean on) {
        this(type, id, 0, null, on, 0);
    }

    public Ev(String type, int acc, String info) {
        this(type, null, acc, info, false, 0);
    }

    public Ev(String type, double sec) {
        this(type, null, 0, null, false, sec);
    }

    // Chuyển sự kiện thành tin TCP gửi cho client; mỗi loại sự kiện chỉ lấy các trường cần thiết
    public Msg toMsg(int lvId) {
        JsonObject data = new JsonObject();
        switch (type) {
            case "KEY_COLLECTED":
                data.addProperty("id", id);
                data.addProperty("acc", acc);
                break;
            case "DOOR_PASSED":
                data.addProperty("id", id);
                data.addProperty("acc", acc);
                break;
            case "LEVER_TOGGLED":
                data.addProperty("id", id);
                data.addProperty("on", on);
                break;
            case "PLATE_CHANGED":
                data.addProperty("id", id);
                data.addProperty("on", on);
                break;
            case "MECH_MOVING":
                data.addProperty("id", id);
                data.addProperty("moving", on);
                break;
            case "PLAYER_DIED":
                data.addProperty("acc", acc);
                data.addProperty("cause", info);
                break;
            case "LEVEL_COMPLETE":
                data.addProperty("lv", lvId);
                data.addProperty("sec", sec);
                break;
            case "LEVEL_FAILED":
                data.addProperty("cause", "DEAD");
                data.addProperty("acc", acc);
                break;
        }
        return new Msg(type, data);
    }
}
