# 06 - Server

Đọc trước: `01-overview.md` (luồng, khóa), `02-protocol.md`, `03-states-flows.md`. File này mô tả từng lớp, trường, hàm của module `server`.

## 1. Cấu hình (`server.properties`)

```
tcp.port=5000
udp.port=5001
db.url=jdbc:mysql://localhost:3306/fw?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8
db.user=fw
db.pass=fw
```

`Cfg.get(key)` đọc file từ thư mục chạy hoặc từ classpath. Có thể ghi đè bằng thuộc tính JVM cùng tên (`-Dtcp.port=6000`).

## 2. Khởi động (`ServerMain.main`)

1. `Cfg.load()`.
2. `Db.init(url, user, pass)` rồi thử kết nối, lỗi thì log ERROR và thoát.
3. `LevelDao.sync()` đồng bộ file màn vào DB (xem file DB), sau đó `Levels.load()` nạp mọi màn vào bộ nhớ: `Map<Integer, LevelRow>` với `LevelRow(int id, String name, String json, Level level)`.
4. Tạo `ScheduledExecutorService sched` (số luồng = `max(2, số lõi CPU)`), `ExecutorService dbExec` (1 luồng), cả hai là luồng daemon đặt tên (`fw-sched-N`, `fw-db`).
5. `UdpServer.start(udpPort)` (1 luồng `fw-udp`).
6. `TcpServer.start(tcpPort)` (luồng `fw-accept`).
7. Đăng ký shutdown hook: đóng `ServerSocket`, `DatagramSocket`, `sched`, `dbExec`.
8. Log INFO `SRV started tcp=.. udp=.. levels=..`.

## 3. `Sess` (phiên của một người đã đăng nhập)

```java
final class Sess {
  final int acc; final String name; final String token; final ClientHandler h;
  volatile Room room;                 // null nếu ở lobby
  volatile String status = "IDLE";    // "IDLE" hoặc "PLAYING"
  // --- UDP, dùng trong trận ---
  volatile InetSocketAddress udp;     // null đến khi bắt tay xong
  volatile Input input = Input.NONE;  // INPUT mới nhất
  volatile int lastSeq;               // seq INPUT gần nhất đã nhận
  volatile long lastUdpMs;            // System.currentTimeMillis() lúc nhận gói UDP hợp lệ gần nhất
}
```

`token` sinh bằng `SecureRandom` 16 byte đổi sang hex (32 ký tự) khi đăng nhập.

## 4. `Online` (danh sách người đã đăng nhập)

```java
final class Online {
  static boolean add(Sess s)         // ConcurrentHashMap.putIfAbsent(acc, s) == null
  static void remove(Sess s)         // remove(acc, s)
  static Sess get(int acc)
  static Collection<Sess> all()
  static void push()                 // gửi ONLINE_LIST cho mọi Sess
}
```

`push()` dựng `Msg` một lần, chuyển thành chuỗi một lần, gọi `h.sendLine(line)` cho từng `Sess`. Danh sách trong `ONLINE_LIST` sắp xếp theo `name`.

## 5. `ClientHandler`

Mỗi kết nối TCP có một đối tượng `ClientHandler` và 2 luồng: luồng đọc (`run`) và luồng ghi.

```java
final class ClientHandler implements Runnable {
  final Socket sock;
  volatile Sess sess;                         // null khi chưa đăng nhập (S0)
  private final BlockingQueue<String> out = new LinkedBlockingQueue<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  void run();                                 // vòng đọc
  void send(Msg m);                           // m -> chuỗi -> sendLine
  void sendLine(String line);                 // out.offer(line); không bao giờ chặn
  void reply(Msg req, String type, JsonObject data);   // gói có rid của req
  void err(Msg req, String code, String msg);          // ERR {code, cmd, msg} có rid
  void close();                               // idempotent
}
```

Luồng đọc (`run`):
1. `sock.setSoTimeout(TCP_TO_MS)`, `setTcpNoDelay(true)`.
2. Tạo `BufferedReader` UTF-8 và khởi động luồng ghi (`fw-w-<id>`): `while (!closed) { line = out.take(); write(line + "\n"); flush(); }`, lỗi IO thì `close()`.
3. Lặp: đọc dòng bằng hàm `Lines.read(reader, MAX_LINE)` (đọc từng ký tự, quá `MAX_LINE` thì ném lỗi và `close()`; `null` nghĩa là peer đóng; `SocketTimeoutException` nghĩa là quá `TCP_TO_MS`).
4. Parse: `Json.parse(line)` lỗi thì `err(null, BAD_JSON, ...)`, đếm lỗi liên tiếp, quá 3 thì `close()`. Parse được thì đặt lại bộ đếm.
5. `dispatch(msg)` (mục 5.1). Mọi ngoại lệ trong `dispatch` bắt ở đây: log ERROR kèm stack trace, trả `ERR INTERNAL`, không đóng kết nối.
6. Khối `finally`: `close()`.

`close()`: nếu `closed.compareAndSet(false, true)` thì (a) đóng socket, (b) nếu `sess != null`: `RoomSvc.leave(sess)` nếu `sess.room != null`, `Online.remove(sess)`, `Online.push()`, (c) log INFO `TCP closed acc=..`.

### 5.1 `dispatch`

```
switch (msg.type):
  PING                       -> reply PONG
  REGISTER, LOGIN            -> AuthSvc.register / AuthSvc.login      (S0 mới được; xem 02 mục 3)
  LOGOUT                     -> AuthSvc.logout
  GET_RANK                   -> RankSvc.get
  CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, SELECT_LEVEL, SWAP, START_GAME, RESTART -> RoomSvc.xxx
  default                    -> err BAD_TYPE
```

Trước khi gọi dịch vụ, `dispatch` kiểm tra trạng thái theo bảng ở `02-protocol.md` mục 3 và trả `NOT_LOGGED_IN` hoặc `BAD_STATE`. Kiểm tra trạng thái ở một chỗ duy nhất (`dispatch`), các dịch vụ không lặp lại kiểm tra này (trừ các kiểm tra cần khóa phòng).

Các dịch vụ có dạng `static void xxx(ClientHandler h, Msg m)`. Đọc trường bằng helper `m.str("name")`, `m.num("lv")` (ném `BadInput` nếu thiếu hoặc sai kiểu, `dispatch` bắt và trả `BAD_INPUT` kèm tên trường).

## 6. `AuthSvc`

| Hàm | Việc |
|---|---|
| `register(h, m)` | Kiểm tra `username`, `password`, `name` (02 mục 1.4). `Pass.hash(password)`. `AccountDao.create(username, hash, name)`. Trùng username (`SQLIntegrityConstraintViolationException`) thì `REGISTER_FAILED USER_EXISTS`. Lỗi SQL khác thì `REGISTER_FAILED DB_ERROR`. Thành công thì `REGISTER_SUCCESS` |
| `login(h, m)` | `AccountDao.find(username)`. Không có hoặc `Pass.check` sai thì `LOGIN_FAILED BAD_LOGIN` (cùng một mã cho cả hai trường hợp). Tạo `Sess`, `Online.add` thất bại thì `LOGIN_FAILED ALREADY_LOGGED_IN`. Thành công: gán `h.sess`, gửi `LOGIN_SUCCESS`, `LEVELS`, `ONLINE_LIST`, `ROOM_LIST` cho người này, rồi `Online.push()` cho mọi người |
| `logout(h, m)` | Nếu đang trong phòng thì `RoomSvc.leave`. `Online.remove`, `h.sess = null`, gửi `LOGOUT_SUCCESS`, `Online.push()` |

Không bao giờ log mật khẩu. Log `username` ở mức INFO.

## 7. `Rooms`, `Room`

```java
final class Rooms {
  static Room create(Sess host);     // id "R" + counter, thêm vào map
  static Room get(String id);
  static void remove(Room r);        // chỉ gọi khi giữ khóa phòng và phòng đã trống
  static void push();                // gửi ROOM_LIST cho mọi Sess, sắp theo id số
}

final class Room {
  final String id;
  final List<Mem> mem = new ArrayList<>(2);    // thứ tự vào phòng
  volatile int host;                            // acc của chủ phòng
  int lv;                                       // id màn đã chọn
  Phase phase = Phase.IDLE;                     // IDLE, LOADING, RUNNING
  int matchId;
  World world;                                  // chỉ khác null khi LOADING (cuối) và RUNNING
  ScheduledFuture<?> tick, helloTimer;
  long goMs;
  static final class Mem { final Sess s; Element el; }
  // các hàm trợ giúp (gọi khi đang giữ khóa phòng):
  Mem find(int acc); Mem other(int acc); boolean full();
  Msg info();                                    // ROOM_INFO data, el tính từ Mem.el
}
```

Khóa phòng: `synchronized (room)`. Xem `01-overview.md` mục 6.5.

Cấu trúc `ROOM_LIST`: `n` = `mem.size()`, `host` = tên chủ phòng, `status` = `phase == IDLE ? "WAITING" : "PLAYING"`.

## 8. `RoomSvc`

Mọi hàm giữ khóa phòng khi đọc/ghi `Room`. Gửi tin cho `Sess` (đưa vào hàng đợi) được phép khi đang giữ khóa.

| Hàm | Việc (theo `02-protocol.md` mục 3 và `03-states-flows.md`) |
|---|---|
| `create(h, m)` | `Rooms.create(sess)`: `mem = [Mem(sess, FIRE)]`, `host = acc`, `lv` = id màn nhỏ nhất. `sess.room = room`. Gửi `ROOM_INFO` (có `rid`). Sau khi nhả khóa: `Rooms.push()` |
| `join(h, m)` | `Rooms.get(room)` null thì `ROOM_NOT_FOUND`. Giữ khóa: `phase != IDLE` thì `BAD_STATE`, `full()` thì `ROOM_FULL`. Thêm `Mem(sess, đối lập với mem[0].el)`. Gửi `ROOM_INFO` cho cả 2 (người vào có `rid`). Nhả khóa rồi `Rooms.push()` |
| `leave(sess)` | Xem mục 8.1. Hàm này cũng được `ClientHandler.close()` và `AuthSvc.logout` gọi |
| `leaveMsg(h, m)` | Gọi `leave(sess)`, rồi gửi `LEFT_ROOM` (có `rid`) |
| `selectLevel(h, m)` | Kiểm tra chủ phòng (`NOT_HOST`), `phase == IDLE`, `Levels.get(lv)` tồn tại (`LEVEL_NOT_FOUND`). Đặt `room.lv`. Gửi `ROOM_INFO` cho cả 2 |
| `swap(h, m)` | Chủ phòng, `phase == IDLE`. Đảo `el` của mọi `Mem` (FIRE thành WATER và ngược lại). Gửi `ROOM_INFO` cho cả 2 |
| `start(h, m)` | Quy trình `Match.start` (mục 9.1) |
| `restart(h, m)` | Chủ phòng, `phase != IDLE`. `endMatch(ABORT RESTART)` rồi `Match.start` |

### 8.1 `leave(Sess s)`

```
Room r = s.room; if (r == null) return;
synchronized (r) {
  r.mem.remove(r.find(s.acc)); s.room = null; s.status = "IDLE";
  if (r.phase != IDLE) Match.endMatch(r, ABORT, "PEER_LEFT");     // gửi GAME_ABORT cho người còn lại, đưa phòng về IDLE
  if (r.mem.isEmpty()) Rooms.remove(r);
  else { r.host = r.mem.get(0).s.acc; gửi ROOM_INFO cho người còn lại; }
}
Rooms.push(); Online.push();
```

Khi `ClientHandler.close()` gọi, socket đã đóng nên các lần gửi cho chính `s` sẽ bị bỏ qua (hàng đợi đã đóng). `sendLine` sau khi `closed` thì không làm gì.

## 9. Trận đấu (`Match`)

`Match` là lớp tĩnh gom logic bắt đầu, vào trận, kết thúc. Mọi hàm gọi khi **không** giữ khóa trừ khi ghi chú khác.

### 9.1 `Match.start(Sess host, Msg req)`

Theo `03-states-flows.md` mục 3.3:

1. `synchronized (r)`: kiểm tra chủ phòng (`NOT_HOST`), `phase == IDLE` (`BAD_STATE`), `mem.size() == 2` (`NEED_2`), `Levels.get(r.lv) != null` (`LEVEL_NOT_FOUND`). Đặt `phase = LOADING`, `int mid = ++r.matchId`, sao chép `acc1`, `acc2`, `lv`.
2. Ngoài khóa: `ResultDao.onStart(acc1, acc2, lv)`. Lỗi: `synchronized (r)` nếu `r.matchId == mid` thì `phase = IDLE`; gửi `ERR DB_ERROR`; return.
3. `synchronized (r)`: nếu `r.matchId != mid` hoặc `r.mem.size() != 2` thì return (trận đã bị hủy). Ngược lại: tạo `World w = new World(level, lv, accFire, accWater)` (người có `el` FIRE là `accFire`), `r.world = w`; mỗi `Sess`: `status = "PLAYING"`, xóa `udp`, `input = NONE`, `lastSeq = 0`. Gửi `GAME_START` cho cả 2 (người bấm có `rid = req.rid`). Hẹn giờ: `r.helloTimer = sched.schedule(() -> Match.helloTimeout(r, mid), HELLO_WAIT_MS, MILLISECONDS)`.
4. Ngoài khóa: `Rooms.push()`, `Online.push()`.

### 9.2 `Match.onHello(Room r, Sess s, InetSocketAddress src)` (gọi từ `UdpServer`, tự lấy khóa)

```
synchronized (r) {
  if (r.phase == IDLE) return;                       // trận không còn
  s.udp = src; s.lastUdpMs = now;                    // chấp nhận cả khi lặp (cùng src)
  trả UDP_ACK
  if (r.phase == LOADING && cả 2 Mem có s.udp != null) go(r);
}
```

### 9.3 `Match.go(Room r)` (đang giữ khóa)

```
r.phase = RUNNING; r.helloTimer.cancel(false); r.goMs = now;
for each mem: s.lastUdpMs = now;
gửi GAME_GO cho cả 2;
int mid = r.matchId;
r.tick = sched.scheduleAtFixedRate(new Loop(r, mid), 0, 1_000_000_000L / TICK_HZ, NANOSECONDS);
```

### 9.4 `Match.helloTimeout(Room r, int mid)`

`synchronized (r)`: nếu `r.matchId == mid && r.phase == LOADING` thì `endMatch(r, ABORT, "UDP_TIMEOUT")`, sau đó nhả khóa và `Rooms.push()`, `Online.push()`.

### 9.5 `Match.endMatch(Room r, Kind k, String info)` (đang giữ khóa)

Theo `03-states-flows.md` mục 1.3. `k` là `WON`, `LOST` hoặc `ABORT`. Message gửi cho thành viên:
- `WON`: các `Ev` đã gửi trong tick, thêm không gì (vì `LEVEL_COMPLETE` nằm trong `Ev`). Ghi DB: sau khi nhả khóa gọi `dbExec.submit(() -> ResultDao.onWin(acc1, acc2, lv, sec))`. Lỗi trong tác vụ này chỉ log ERROR.
- `LOST`: các `Ev` đã gửi (gồm `LEVEL_FAILED`).
- `ABORT`: gửi `GAME_ABORT {reason: info}` cho thành viên còn kết nối.

Hàm trả về một đối tượng `After` (việc cần làm sau khi nhả khóa: `Rooms.push()`, `Online.push()`, ghi DB). Người gọi chạy `After.run()` ngay sau khi thoát `synchronized`.

## 10. `Loop` (một lần tick của một phòng)

```java
final class Loop implements Runnable {
  Loop(Room r, int mid)
  public void run() {
    try { tick(); }
    catch (Throwable t) {           // BẮT BUỘC: ngoại lệ thoát ra ngoài sẽ làm scheduleAtFixedRate ngừng vĩnh viễn
      Log.error("LOOP", "room=" + r.id, t);
      endWithError();               // endMatch(ABORT, "SERVER_ERROR")
    }
  }
}
```

`tick()`:

```
After after = null; List<Out> outs = new ArrayList<>();   // Out = (InetSocketAddress, byte[]) cần gửi UDP
synchronized (r) {
  if (r.matchId != mid || r.phase != RUNNING) { cancel chính mình; return; }
  long now = currentTimeMillis();
  for (Mem m : r.mem) if (now - m.s.lastUdpMs > UDP_TO_MS) { after = endMatch(r, ABORT, "UDP_TIMEOUT"); break; }
  if (after == null) {
    Input[] in = { inputOf(FIRE), inputOf(WATER) };            // lấy từ Mem.s.input theo el
    List<Ev> evs = r.world.step(in);
    for (Ev e : evs) gửi e.toMsg(r.lv) cho cả 2 (TCP);
    if (r.world.tick % (TICK_HZ / SNAP_HZ) == 0) {
      String snap = Snap.write(r.world, acks);                 // acks lấy từ Mem.s.lastSeq
      if (snap != null) for (Mem m : r.mem) outs.add(new Out(m.s.udp, bytes(snap)));
    }
    if (r.world.phase != RUN) after = endMatch(r, r.world.phase == WON ? WON : LOST, null);
  }
}
for (Out o : outs) UdpServer.send(o.addr, o.bytes);      // gửi UDP ngoài khóa
if (after != null) after.run();
```

Mỗi 300 tick (5 giây), `Loop` log INFO thống kê: `LOOP room=R1 ticks=300 avgUs=.. maxUs=..`. Nếu 1 tick mất quá 16 ms, log WARN `LOOP slow tick us=..`.

## 11. `UdpServer`

```java
final class UdpServer implements Runnable {
  static DatagramSocket sock;           // new DatagramSocket(port), bind mọi địa chỉ (0.0.0.0)
  static void send(InetSocketAddress to, byte[] data)
  public void run()                     // vòng nhận
}
```

Vòng nhận (buffer 2048 byte, 1 luồng duy nhất): `sock.receive(pkt)` → `String s = new String(data, 0, len, UTF_8)` → `Json.parse` (lỗi thì WARN và bỏ qua) → đọc `type`, `data.room`, `data.acc`:

```
Room r = Rooms.get(room); Sess s = Online.get(acc);
nếu r == null hoặc s == null hoặc s.room != r:   bỏ qua, log DEBUG
UDP_HELLO: kiểm tra data.token == s.token, sai thì WARN "bad token acc=.."; đúng thì Match.onHello(r, s, src)
INPUT:     synchronized (r) { phase == RUNNING; src.equals(s.udp); seq > s.lastSeq; thì s.input = Input(l,r,j); s.lastSeq = seq; s.lastUdpMs = now }
PING:      kiểm tra src.equals(s.udp), trả PONG {t}; cập nhật s.lastUdpMs = now
```

Mọi nhánh bỏ qua đều log DEBUG kèm lý do (xem file debug). Lỗi trong vòng nhận bắt `Throwable`, log ERROR, tiếp tục vòng lặp (không để luồng chết).

`NetSim` áp dụng cho `send` (và nhận) nếu bật thuộc tính mô phỏng.

## 12. `RankSvc`

`get(h, m)`: đọc `kind`, `lv`, `limit` (mặc định 10, giới hạn 1 đến 100). `ALL` thì `ResultDao.rankAll(limit)`. `LEVEL` thì kiểm tra `Levels.get(lv)` rồi `ResultDao.rankLevel(lv, limit)`. Đổi kết quả sang `RANK` (02 mục 4.1), thêm `pos` từ 1.

## 13. Quy tắc luồng và khóa (tóm tắt để review)

1. Chỉ `Loop` gọi `World.step`. Chỉ truy cập `World` khi giữ `synchronized (room)`.
2. Không gọi DB hoặc ghi socket chặn khi giữ khóa phòng. DB nặng: ở `start` (ngoài khóa) và `dbExec` (kết thúc trận).
3. Gửi TCP luôn qua `sendLine`/`send` (hàng đợi), nên an toàn khi giữ khóa.
4. Gửi UDP thực hiện ngoài khóa phòng.
5. Thứ tự khóa: `Rooms` (nếu cần) rồi `Room`. Không có hàm nào giữ khóa phòng rồi gọi hàm lấy khóa phòng khác.
6. Mọi tác vụ định thời kiểm tra `matchId`.
7. `Throwable` bị bắt ở: vòng đọc `ClientHandler`, `Loop.run`, vòng nhận `UdpServer`, tác vụ `dbExec`.
8. `Sess.input`, `lastSeq`, `lastUdpMs`, `udp`, `room`, `status` là `volatile`.
