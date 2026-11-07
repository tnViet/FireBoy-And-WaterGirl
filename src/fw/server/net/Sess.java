package fw.server.net;

import fw.common.game.Input;
import fw.server.svc.Room;

import java.net.InetSocketAddress;

public final class Sess {
    public final int acc;
    public final String name;
    public final String token;
    public final ClientHandler h;

    public volatile Room room;
    public volatile String status = "IDLE";

    // UDP fields used during match
    public volatile InetSocketAddress udp;
    public volatile Input input = Input.NONE;
    public volatile int lastSeq;
    public volatile long lastUdpMs;

    public Sess(int acc, String name, String token, ClientHandler h) {
        this.acc = acc;
        this.name = name;
        this.token = token;
        this.h = h;
    }
}
