package fw.common.game;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Công cụ chạy thử World không cần mạng/giao diện: nạp màn, chạy theo chuỗi input rồi in vị trí từng tick.
 * Input lấy từ file ghi lại (rec) hoặc kịch bản dạng "r*30,rj*5,l*10" (phím * số tick).
 */
public class Sim {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: Sim <level.json> [rec.txt | --script \"commands\"]");
            System.exit(1);
        }

        String levelFile = args[0];
        Level lv = LevelLoader.load(levelFile.substring(levelFile.lastIndexOf('/') + 1));

        List<Input[]> inputs = new ArrayList<>();

        if (args.length > 1) {
            if (args[1].equals("--script") && args.length > 2) {
                inputs = parseScript(args[2]);
            } else {
                inputs = loadRec(args[1]);
            }
        }

        if (inputs.isEmpty()) {
            System.err.println("No inputs provided");
            System.exit(1);
        }

        World world = new World(lv, 1, 1, 2);

        for (int i = 0; i < inputs.size(); i++) {
            Input[] in = inputs.get(i);
            List<Ev> events = world.step(in);

            System.out.printf("tick=%d p0=(%.1f,%.1f) onGround=%b vy=%.1f p1=(%.1f,%.1f) onGround=%b vy=%.1f%n",
                world.tick,
                world.pl[0].x, world.pl[0].y, world.pl[0].onGround, world.pl[0].vy,
                world.pl[1].x, world.pl[1].y, world.pl[1].onGround, world.pl[1].vy);

            for (Ev ev : events) {
                System.out.println("  event: " + ev.type());
            }

            if (world.phase != World.Phase.RUN) {
                System.out.println("Game ended: " + world.phase);
                break;
            }
        }
    }

    // Đọc file ghi input: mỗi dòng "tick,l0,r0,j0,l1,r1,j1" (1 = đang giữ phím), bỏ qua dòng tiêu đề
    private static List<Input[]> loadRec(String filename) throws Exception {
        List<Input[]> inputs = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("level=") || line.startsWith("lvId=")) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length == 7) {
                    Input i0 = new Input(parts[1].equals("1"), parts[2].equals("1"), parts[3].equals("1"));
                    Input i1 = new Input(parts[4].equals("1"), parts[5].equals("1"), parts[6].equals("1"));
                    inputs.add(new Input[]{i0, i1});
                }
            }
        }
        return inputs;
    }

    // Đọc kịch bản: mỗi lệnh gồm các phím l/r/j và số tick lặp lại; chỉ điều khiển nhân vật Lửa
    private static List<Input[]> parseScript(String script) {
        List<Input[]> inputs = new ArrayList<>();
        String[] commands = script.split(",");

        for (String cmd : commands) {
            cmd = cmd.trim();
            int repeat = 1;
            if (cmd.contains("*")) {
                String[] parts = cmd.split("\\*");
                cmd = parts[0];
                repeat = Integer.parseInt(parts[1]);
            }

            for (int i = 0; i < repeat; i++) {
                boolean l = cmd.contains("l");
                boolean r = cmd.contains("r");
                boolean j = cmd.contains("j");
                Input in = new Input(l, r, j);
                inputs.add(new Input[]{in, new Input(false, false, false)});
            }
        }

        return inputs;
    }
}
