package fw.client.render;

import fw.common.game.Snap;

public final class Interp {
    private static volatile Snap.SnapData latest = null;

    private Interp() {}

    public static void push(Snap.SnapData snap, long recvNs) {
        latest = snap;
    }

    public static Snap.SnapData get() {
        return latest;
    }

    public static void clear() {
        latest = null;
    }
}
