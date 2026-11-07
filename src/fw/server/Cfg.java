package fw.server;

import fw.common.util.Log;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public final class Cfg {
    private static final Properties props = new Properties();

    private Cfg() {}

    // Đọc server.properties: ưu tiên file bên ngoài, không có thì lấy trong classpath
    public static void load() throws Exception {
        // Ưu tiên file server.properties nằm cạnh chỗ chạy server
        try (InputStream is = new FileInputStream("server.properties")) {
            props.load(is);
            Log.info("Cfg", "loaded from file system");
            return;
        } catch (Exception e) {
            // Không có file bên ngoài thì đọc trong classpath
        }

        // Đọc trong classpath: src/fw/server/resources/ trước, sau đó gốc classpath
        for (String path : new String[]{ "fw/server/resources/server.properties", "server.properties" }) {
            try (InputStream is = Cfg.class.getClassLoader().getResourceAsStream(path)) {
                if (is != null) {
                    props.load(is);
                    Log.info("Cfg", "loaded from classpath", "path", path);
                    return;
                }
            }
        }

        throw new IllegalStateException("server.properties not found");
    }

    public static String get(String key) {
        // Tham số -Dkey=value khi chạy được ưu tiên hơn file cấu hình
        String sys = System.getProperty(key);
        if (sys != null) {
            return sys;
        }
        return props.getProperty(key);
    }

    public static int getInt(String key, int defaultValue) {
        String val = get(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
