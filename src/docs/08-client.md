# 08 - Client

Đọc trước: `01-overview.md`, `02-protocol.md`, `03-states-flows.md` (mục 2 là máy trạng thái màn hình). File này mô tả lớp, trường, hàm của module `client`.

## 1. Cấu hình

`client.properties`:

```
server.host=127.0.0.1
tcp.port=5000
```

Cổng UDP lấy từ `GAME_START.udpPort`. Dùng `127.0.0.1` thay vì `localhost` để tránh lệch IPv4 và IPv6.

`settings.properties` (tùy chọn, tạo cạnh file chạy, có giá trị mặc định nếu thiếu):

```
music.volume=0.7
sfx.volume=0.8
key.left=A
key.right=D
key.jump=W
```

Phím phụ luôn có: mũi tên trái, phải, lên và phím cách (nhảy). Tương ứng `Settings` trong tài liệu BTL.

## 2. Quy tắc luồng

- Mọi thao tác Swing chạy trên EDT.
- `TcpConn` có 1 luồng đọc. Mỗi `Msg` nhận được chuyển sang EDT bằng `SwingUtilities.invokeLater(() -> Win.dispatch(msg))`.
- `UdpConn` có 1 luồng đọc. `SNAP` đi thẳng vào `Interp.push` (an toàn luồng, không qua EDT). `UDP_ACK`, `PONG` cập nhật biến `volatile`.
- Không bao giờ gọi mạng chặn trên EDT. `TcpConn.send` đưa vào hàng đợi và có luồng ghi riêng (giống server).
- Việc vẽ gọi `Interp` để lấy trạng thái nội suy, không đọc trực tiếp từ luồng mạng.

## 3. `Model`

Lưu trên EDT, các màn hình đọc từ đây.

```java
final class Model {
  int acc; String name; String token;               // sau LOGIN_SUCCESS
  List<LevelInfo> levels;                            // record LevelInfo(int lv, String name)
  List<PlayerInfo> online;                           // record PlayerInfo(int acc, String name, String status)
  List<RoomInfoRow> rooms;                           // record RoomInfoRow(String room, String host, int n, String status)
  RoomData room;                                     // null nếu không ở trong phòng: room, host(acc), status, lv, members
  GameData game;                                     // dữ liệu trận hiện tại: room, lv, you, players, level (Level đã parse), udpPort
  boolean isHost()                                   // room != null && room.host == acc
  void reset()                                       // đặt lại tất cả (khi đăng xuất hoặc mất kết nối)
}
```

## 4. `TcpConn`

```java
final class TcpConn {
  void connect(String host, int port) throws IOException
  void send(String type, JsonObject data)             // tự thêm rid tăng dần, trả về rid
  int sendRid(...)                                    // dạng trả rid để màn hình ghép phản hồi nếu cần
  void close()
  Consumer<Msg> onMsg; Runnable onClosed;             // callback, gọi từ luồng đọc
}
```

- Khung dòng, mã hóa UTF-8, giới hạn dòng: theo `02-protocol.md` mục 1.2.
- Kết nối được mở khi bấm Đăng nhập hoặc Đăng ký lần đầu (không mở lúc khởi động). Lỗi kết nối thì hiện thông báo ở `LoginScreen`.
- Gửi `PING` mỗi `PING_MS` (dùng `javax.swing.Timer` hoặc `ScheduledExecutorService`).
- Đọc lỗi hoặc `null` thì gọi `onClosed` một lần. `Win` xử lý: hộp thoại "Mất kết nối", `Model.reset()`, đóng `UdpConn`, về `LoginScreen`.

## 5. `UdpConn`

```java
final class UdpConn {
  void open(InetAddress server, int port)             // new DatagramSocket(); connect(server, port)
  void hello(int room, ...)                           // bắt đầu gửi UDP_HELLO mỗi HELLO_MS cho đến khi acked hoặc go
  void startInput(Supplier<Input> src)                // gửi INPUT mỗi 1000/IN_HZ ms; gọi thêm sendNow khi phím đổi
  void sendNow()
  void startPing()                                    // mỗi 1 giây
  void close()
  volatile boolean acked;  volatile int pingMs;
}
```

- Một `DatagramSocket` duy nhất cho cả gửi và nhận. Gọi `connect(serverAddr, port)` để chỉ nhận gói từ server.
- `seq` của `INPUT` bắt đầu từ 1 mỗi trận, tăng 1 mỗi gói.
- Thuật toán `JUMP_HOLD_MS`: khi phát hiện phím nhảy chuyển từ nhả sang giữ, lưu `jumpUntil = now + JUMP_HOLD_MS`. Giá trị `j` gửi đi là `phím đang giữ || now < jumpUntil`.
- Luồng đọc: `UDP_ACK` thì `acked = true`. `SNAP` thì `Snap.read` rồi `Interp.push(snap, System.nanoTime())`. `PONG` thì `pingMs = now - t`.
- `close()` dừng mọi bộ đếm giờ, đóng socket, luồng đọc thoát khi socket đóng.
- `NetSim` áp dụng khi gửi và khi nhận nếu bật.

## 6. `Keys`

Một `KeyListener` (hoặc `KeyBindings` của Swing) trên `Canvas`, trạng thái 3 cờ `l`, `r`, `j`.
- `Keys.read()` trả `Input` hiện tại.
- Gọi callback khi một cờ đổi (để `UdpConn.sendNow`).
- `Canvas` mất tiêu điểm (`focusLost`) thì xóa mọi cờ (tránh kẹt phím).
- Chỉ bật khi ở `PLAY`. Ở trạng thái khác, `Keys.read()` trả `Input.NONE`.

## 7. `View` và `Interp`

`View` giữ trạng thái vẽ của trận:
- Phần tĩnh từ `Level` (địa hình, vị trí ban đầu, định nghĩa vật).
- Phần động từ snapshot.

`Interp`:
- Giữ tối đa 8 snapshot gần nhất, mỗi cái kèm thời điểm nhận (`nanoTime`).
- `Interp.at(long nowNs)` trả `Frame` (vị trí nội suy): `renderTime = nowNs - INTERP_MS*1e6`. Tìm 2 snapshot `a`, `b` có `a.recv <= renderTime <= b.recv`, nội suy tuyến tính `x`, `y` của nhân vật và đá, `p` của cơ quan theo `t = (renderTime - a.recv) / (b.recv - a.recv)`.
- Nếu `renderTime` sớm hơn snapshot đầu thì dùng snapshot đầu. Muộn hơn snapshot cuối thì dùng snapshot cuối (không ngoại suy).
- Cờ rời rạc (`alive`, `done`, `face`, `air`, `got`, `open`, `passed`, `on`) lấy từ snapshot mới nhất.
- Bỏ snapshot có `tick` nhỏ hơn hoặc bằng snapshot đã nhận.
- Không có snapshot nào thì `Canvas` vẽ vị trí ban đầu từ `GAME_START`.

## 8. Màn hình

Mọi màn là `JPanel` cài `Screen { void show(); void hide(); void onMsg(Msg m); }`. `Win` giữ `CardLayout` với các thẻ `LOGIN`, `LOBBY`, `ROOM`, `GAME`, `RANK` và chuyển thẻ theo bảng ở `03-states-flows.md` mục 2.2. `Win.dispatch(Msg)` chuyển tin cho màn đang hiện, đồng thời cập nhật `Model` cho các tin dùng chung (`ONLINE_LIST`, `ROOM_LIST`, `LEVELS`, `ROOM_INFO`).

### 8.1 `LoginScreen`

| Thành phần | Chi tiết |
|---|---|
| Trường nhập | `username`, `password` (`JPasswordField`), `name` (chỉ dùng khi đăng ký) |
| Nút | Đăng nhập (gửi `LOGIN`), Đăng ký (gửi `REGISTER`) |
| Nhãn trạng thái | Hiện thông báo lỗi theo `code` (`BAD_LOGIN`: "Sai tên hoặc mật khẩu", `ALREADY_LOGGED_IN`: "Tài khoản đang đăng nhập nơi khác", `USER_EXISTS`: "Tên đã tồn tại", `BAD_INPUT`: thông báo từ `msg`) |
| Khi nút bấm | Khóa nút, gửi lệnh, mở nút lại khi có phản hồi |

Kiểm tra dữ liệu phía client theo 02 mục 1.4 trước khi gửi (server vẫn kiểm tra lại).

### 8.2 `LobbyScreen`

| Thành phần | Chi tiết |
|---|---|
| Bảng người chơi online | Cột: tên, trạng thái (Rảnh/Đang chơi). Dữ liệu từ `Model.online` |
| Bảng phòng | Cột: mã phòng, chủ phòng, số người (n/2), trạng thái. Dữ liệu từ `Model.rooms` |
| Nút | Tạo phòng (`CREATE_ROOM`), Vào phòng (`JOIN_ROOM`, chỉ bật khi chọn phòng `WAITING` có `n` < 2), Xếp hạng, Đăng xuất (`LOGOUT`) |
| Tiêu đề | Hiện tên người chơi |

Cập nhật bảng mỗi khi nhận `ONLINE_LIST` hoặc `ROOM_LIST` (thay toàn bộ, giữ lại dòng đang chọn nếu còn).

### 8.3 `RoomScreen`

| Thành phần | Chi tiết |
|---|---|
| Hai ô thành viên | Tên, nguyên tố (biểu tượng Lửa/Nước), nhãn "Chủ phòng" |
| Chọn màn | `JComboBox` từ `Model.levels`, chỉ chủ phòng bật. Đổi giá trị thì gửi `SELECT_LEVEL` |
| Nút Đổi vai | Chỉ chủ phòng bật, gửi `SWAP` |
| Nút Bắt đầu | Chỉ chủ phòng và đủ 2 người mới bật, gửi `START_GAME` |
| Nút Thoát | Gửi `LEAVE_ROOM` |

### 8.4 `GameScreen`

Bố cục: `Canvas` ở giữa, thanh trên cùng hiện đồng hồ (từ `tick/60` của snapshot mới nhất, định dạng `mm:ss.cc`), tên màn, nút Thoát, nút Chơi lại (chỉ chủ phòng).

Trạng thái trong màn:

| Trạng thái | Hiển thị |
|---|---|
| `LOADING` | Chữ "Đang chờ đối thủ...", không vẽ trận |
| `PLAY` | Vẽ trận, bật `Keys` |
| `RESULT` | Vẽ khung cuối cùng mờ đi, lớp phủ kết quả theo `03-states-flows.md` mục 2.2 |

Phím tắt: `Esc` mở hộp xác nhận Thoát. `F3` bật hoặc tắt `Dbg`. Nút Chơi lại gửi `RESTART` (sau hộp xác nhận).

Khi vào `LOADING`: `UdpConn.open(...)`, `hello(...)`. Khi nhận `GAME_GO`: `startInput`, `startPing`, chuyển `PLAY`. Khi rời `PLAY` hoặc `LOADING` (bất kỳ lý do nào): `UdpConn.close()`.

Lớp phủ kết quả:
- `LEVEL_COMPLETE`: "Hoàn thành màn! Thời gian: {sec}s", phát âm thanh kết thúc.
- `LEVEL_FAILED`: "Thất bại: {tên người chết}" cùng nguyên nhân (hồ nước, dung nham, chất độc).
- Nút theo vai trò (chủ phòng hoặc không).

### 8.5 `RankScreen`

Hai thẻ: Tổng (`GET_RANK` kind `ALL`) và Theo màn (hộp chọn màn, `GET_RANK` kind `LEVEL`). Bảng cột theo `RANK` (xem `02-protocol.md` mục 4.1). Nút Quay lại.

## 9. `Canvas` (vẽ game)

- `JPanel`, `paintComponent` vẽ bằng `Graphics2D`. Bật `RenderingHints` làm mịn cho ảnh, tắt cho khối màu để cạnh sắc nét.
- Vòng vẽ: `javax.swing.Timer` 16 ms gọi `repaint()`.
- Tỉ lệ: không gian logic `cols*32` x `rows*32`. Vẽ có `AffineTransform` co dãn vừa khung, giữ tỉ lệ, viền đen hai bên.
- Thứ tự vẽ: nền, ô tường, vùng (nước/dung nham/độc, có hoạt hình lượn sóng đơn giản), cửa (mở/đóng), nút, cần gạt (hai trạng thái), cơ quan, đá, chìa khóa (nếu `got` = false), nhân vật (không vẽ nếu `done`), tên trên đầu nhân vật, đồng hồ.
- Nhân vật của chính mình (`Model.game.you`) có mũi tên nhỏ phía trên đầu để nhận ra.
- Hoạt hình nhân vật: ba trạng thái (đứng, chạy, nhảy) từ `air` và độ dịch chuyển giữa 2 snapshot, lật theo `face`. Nếu có sprite sheet thì chạy theo thời gian, nếu không thì dùng khối màu (xem mục 10).
- Hiệu ứng nhẹ: hạt nhỏ khi nhặt chìa, nhấp nháy khi cơ quan chạy. Không bắt buộc ở phiên bản đầu.

## 10. `Assets` và `Sfx`

`Assets` đọc `assets.properties` trong resources:

```
sprite.FIRE=img/fire.png
sprite.WATER=img/water.png
sprite.ROCK=img/rock.png
sprite.WATER_ZONE=img/water_zone.png
sprite.LAVA_ZONE=img/lava_zone.png
sprite.POISON_ZONE=img/poison_zone.png
sprite.KEY=img/key.png
sprite.DOOR=img/door.png
sprite.LEVER=img/lever.png
sprite.PLATE=img/plate.png
sprite.ELEVATOR=img/elevator.png
sprite.GATE=img/gate.png
sprite.TILE=img/tile.png
sound.PLAYER_DIED=snd/die.wav
sound.SWITCH_TOGGLED=snd/switch.wav
sound.MECHANISM_MOVED=snd/mech.wav
sound.KEY_COLLECTED=snd/key.wav
sound.LEVEL_COMPLETE=snd/win.wav
```

- `Assets.sprite(ObjectType)` trả `BufferedImage` hoặc `null`. Nạp lười, lưu bộ nhớ đệm. Thiếu file thì log WARN một lần, trả `null`.
- **Bắt buộc**: client phải chạy được khi không có file hình nào. Khi `sprite` là `null`, `Canvas` vẽ khối màu: Lửa đỏ, Nước xanh dương, đá xám, hồ nước xanh nhạt, dung nham cam, hồ độc xanh lá, chìa khóa vàng, cửa nâu, cần gạt tím, nút ghi, thang máy xanh đậm, cửa chắn xám đậm, ô tường xám tối.
- `Sfx.play(EventType)` tra `soundMap`, nạp `AudioInputStream` vào `Clip` (cache `Clip` theo sự kiện, mỗi lần phát: dừng, `setFramePosition(0)`, `start()`). Thiếu file hoặc lỗi thiết bị âm thanh thì log WARN một lần và bỏ qua, không ném lỗi. Âm lượng bằng `FloatControl.Type.MASTER_GAIN` theo `sfx.volume`.

Bảng ánh xạ sự kiện TCP sang âm thanh (`Win.dispatch` gọi `Sfx.play`):

| Sự kiện TCP | `EventType` |
|---|---|
| `PLAYER_DIED` | `PLAYER_DIED` |
| `LEVER_TOGGLED` | `SWITCH_TOGGLED` |
| `PLATE_CHANGED` (với `on` = true) | `SWITCH_TOGGLED` |
| `MECH_MOVING` (với `moving` = true) | `MECHANISM_MOVED` |
| `KEY_COLLECTED` | `KEY_COLLECTED` |
| `LEVEL_COMPLETE` | `LEVEL_COMPLETE` |

Âm thanh chỉ phát từ sự kiện TCP, không suy ra từ khác biệt giữa các snapshot.

## 11. `Dbg` (lớp phủ debug, bật bằng F3)

Vẽ lên `Canvas`, góc trên trái, chữ nhỏ có nền mờ:
- `fps` (vẽ), `snap/s` (số snapshot nhận mỗi giây), `tick` (của snapshot mới nhất), `ping` (ms), `ackSeq` (từ `ack[you]`) và `seq` đã gửi, độ lệch giữa hai số này, kích thước hàng đợi `Interp`.
- Vẽ viền hitbox của mọi nhân vật (24x30), đá, cơ quan, vùng, và đường quét của cơ quan.
- Hiện `acc`, `el`, `alive`, `air`, `x`, `y` của từng nhân vật.
- Hiện trạng thái màn hình hiện tại và `matchId`/`room`.

## 12. `Bot` (client không GUI)

Xem `09-debug.md` mục 6. Dùng `TcpConn`, `UdpConn`, `Interp` của client thật, không dùng Swing.

## 13. Quy tắc cho AI khi code client

1. Không thao tác Swing ngoài EDT.
2. Không gọi `World.step` ở client. Client chỉ đọc `Snap`.
3. Mọi trạng thái hiển thị đến từ `Model` và `Interp`. Màn hình không tự giữ bản sao dữ liệu của server.
4. Mọi nhánh mất kết nối đều đi về một hàm `Win.onDisconnect()`.
5. Thiếu tài nguyên (hình, âm thanh) không được làm chương trình lỗi.
