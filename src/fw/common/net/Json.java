package fw.common.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Json {
    private static final Gson gson = new Gson();

    public static Msg parse(String line) {
        JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
        String type = obj.get("type").getAsString();
        Integer rid = obj.has("rid") ? obj.get("rid").getAsInt() : null;
        JsonObject data = obj.getAsJsonObject("data");
        return new Msg(type, rid, data);
    }

    public static String str(Msg msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", msg.type);
        if (msg.rid != null) {
            obj.addProperty("rid", msg.rid);
        }
        obj.add("data", msg.data);
        return gson.toJson(obj);
    }
}
