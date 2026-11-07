package fw.common.game;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Rec {
    private final PrintWriter writer;

    public Rec(String filename) throws IOException {
        writer = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
    }

    public void writeHeader(String levelName, int lvId) {
        writer.println("level=" + levelName);
        writer.println("lvId=" + lvId);
    }

    public void writeTick(int tick, Input[] inputs) {
        writer.printf("%d,%d,%d,%d,%d,%d,%d%n",
            tick,
            inputs[0].l() ? 1 : 0,
            inputs[0].r() ? 1 : 0,
            inputs[0].j() ? 1 : 0,
            inputs[1].l() ? 1 : 0,
            inputs[1].r() ? 1 : 0,
            inputs[1].j() ? 1 : 0);
    }

    public void close() {
        if (writer != null) {
            writer.close();
        }
    }
}
