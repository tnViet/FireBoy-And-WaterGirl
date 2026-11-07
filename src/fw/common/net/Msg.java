package fw.common.net;

import com.google.gson.JsonObject;

public class Msg {
    public final String type;
    public Integer rid;
    public final JsonObject data;

    public Msg(String type, Integer rid, JsonObject data) {
        this.type = type;
        this.rid = rid;
        this.data = data;
    }

    public Msg(String type, JsonObject data) {
        this(type, null, data);
    }

    public String str(String key) {
        if (!data.has(key) || !data.get(key).isJsonPrimitive()) {
            throw new RuntimeException("Missing or invalid field: " + key);
        }
        return data.get(key).getAsString();
    }

    public int num(String key) {
        if (!data.has(key) || !data.get(key).isJsonPrimitive()) {
            throw new RuntimeException("Missing or invalid field: " + key);
        }
        return data.get(key).getAsInt();
    }
}
