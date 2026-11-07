package fw.common.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Log {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static Level level = Level.INFO;
    private static PrintWriter file;

    public enum Level {
        ERROR(0), WARN(1), INFO(2), DEBUG(3), TRACE(4);
        final int val;
        Level(int val) { this.val = val; }
    }

    static {
        String lvl = System.getProperty("fw.log", "INFO");
        try {
            level = Level.valueOf(lvl);
        } catch (IllegalArgumentException e) {
            level = Level.INFO;
        }

        String filename = System.getProperty("fw.logfile");
        if (filename != null) {
            try {
                Path logDir = Path.of("logs");
                if (!Files.exists(logDir)) {
                    Files.createDirectories(logDir);
                }
                file = new PrintWriter(new BufferedWriter(new FileWriter("logs/" + filename, false)), true);
            } catch (IOException e) {
                System.err.println("Failed to open log file: " + e.getMessage());
            }
        }
    }

    public static void error(String tag, String msg, Object... kvPairs) {
        log(Level.ERROR, tag, msg, kvPairs);
    }

    public static void error(String tag, String msg, Throwable t) {
        log(Level.ERROR, tag, msg);
        if (t != null) {
            t.printStackTrace(System.err);
            if (file != null) {
                t.printStackTrace(file);
            }
        }
    }

    public static void warn(String tag, String msg, Object... kvPairs) {
        log(Level.WARN, tag, msg, kvPairs);
    }

    public static void info(String tag, String msg, Object... kvPairs) {
        log(Level.INFO, tag, msg, kvPairs);
    }

    public static void debug(String tag, String msg, Object... kvPairs) {
        log(Level.DEBUG, tag, msg, kvPairs);
    }

    public static void trace(String tag, String msg, Object... kvPairs) {
        log(Level.TRACE, tag, msg, kvPairs);
    }

    // Ghi 1 dòng log dạng "giờ MỨC [tag] nội dung khóa=giá trị..." ra màn hình và file (nếu có)
    private static void log(Level lvl, String tag, String msg, Object... kvPairs) {
        if (lvl.val > level.val) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(msg);

        // Ghép các cặp khóa=giá trị phía sau câu log
        for (int i = 0; i < kvPairs.length; i += 2) {
            if (i + 1 < kvPairs.length) {
                sb.append(" ").append(kvPairs[i]).append("=").append(kvPairs[i + 1]);
            }
        }

        String time = LocalTime.now().format(TIME_FMT);
        String line = String.format("%s %-5s [%s] %s", time, lvl.name(), tag, sb.toString());
        System.out.println(line);
        if (file != null) {
            file.println(line);
        }
    }

    public static void close() {
        if (file != null) {
            file.close();
        }
    }
}
