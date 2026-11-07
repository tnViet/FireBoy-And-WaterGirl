# 01 - Tổng quan, công nghệ, kiến trúc

Tài liệu đặc tả để giao cho AI code. Quy tắc chung cho AI:
- Mọi tên lớp, hàm, biến, trường JSON viết bằng tiếng Anh, ngắn gọn (xem mục 5).
- Không tự đổi tên, không tự đổi giao thức (giao thức ở `02-protocol.md`). Muốn đổi thì dừng và hỏi.
- Mỗi lần chỉ làm đúng phần được giao.

## 1. Mục tiêu

Game 2 người phối hợp (Lửa và Nước) qua màn. Một server, nhiều client, giao tiếp bằng Java Socket (TCP và UDP). Server giữ trạng thái chính của màn chơi, client chỉ gửi input và vẽ lại.

## 2. Công nghệ

| Mục | Chọn |
|---|---|
| JDK | 17, đặt `maven.compiler.release` = 17. Dùng được record, switch expression, text block. Không dùng virtual thread, pattern matching cho switch. |
| Build | Maven nhiều module: parent `fw` + `common`, `server`, `client` |
| JSON | Gson 2.10.1 |
| DB | MySQL 8, JDBC `com.mysql:mysql-connector-j:8.4.0`, không ORM, dùng `PreparedStatement` |
| Băm mật khẩu | `at.favre.lib:bcrypt:0.10.2` |
| GUI | Swing + Java2D. Viết UI bằng code, không dùng GUI Builder, không file `.form` |
| Âm thanh | `javax.sound.sampled`, chỉ file `.wav` PCM 16-bit |
| Test | JUnit 5.10.2 (common và server) |
| Log | Lớp tự viết `Log` (không dùng log4j) |
| IDE | VS Code hoặc NetBeans, cùng mở `pom.xml` |

Số phiên bản là gợi ý. Nếu Maven không tìm thấy thì chọn bản gần nhất cùng major.

Phụ thuộc module: `server` và `client` phụ thuộc `common`. `common` chỉ phụ thuộc Gson, không phụ thuộc Swing, JDBC hay socket.

## 3. Cấu trúc project

```
fw/
  pom.xml
  docs/                      (các file đặc tả)
  .vscode/launch.json        (chạy 1 server + 2 client cùng lúc)
  common/
    pom.xml
    src/main/java/fw/common/util/    Log NetSim
    src/main/java/fw/common/net/     Msg Json MsgType Err Const
    src/main/java/fw/common/game/    Level LevelLoader World Player Rock Key Door
                                     Lever Plate Mech Zone Rect Input
                                     Ev Snap Phys Rec Sim Element ZoneKind MechKind
    src/main/resources/levels/       index.txt lv1.json lv2.json ...
    src/test/java/fw/common/game/    test vật lý, không cần mạng
  server/
    pom.xml
    src/main/java/fw/server/         ServerMain TcpServer ClientHandler UdpServer
                                     Sess Online Rooms Room Loop Levels Cfg
    src/main/java/fw/server/svc/     AuthSvc RoomSvc RankSvc
    src/main/java/fw/server/db/      Db AccountDao LevelDao ResultDao Pass
    src/main/resources/              server.properties schema.sql
  client/
    pom.xml
    src/main/java/fw/client/         ClientMain Bot Win Model Cfg
    src/main/java/fw/client/net/     TcpConn UdpConn
    src/main/java/fw/client/ui/      Screen LoginScreen LobbyScreen RoomScreen
                                     GameScreen RankScreen Canvas
    src/main/java/fw/client/game/    View Interp Keys Assets Sfx Dbg
    src/main/resources/              client.properties img/ snd/
```

Package gốc: `fw.common`, `fw.server`, `fw.client`.

## 4. Các lớp chính và trách nhiệm

### common

| Lớp | Việc |
|---|---|
| `Log` | Ghi log có tag và mức (chuẩn log ở file debug) |
| `NetSim` | Giả lập mất gói và trễ cho UDP (bật bằng thuộc tính JVM, xem file debug) |
| `Msg` | Gói tin: `type` (String), `rid` (Integer, có thể null), `data` (JsonObject) |
| `Json` | Gson dùng chung: `parse(String)`, `str(Msg)` |
| `MsgType` | Hằng chuỗi tên message |
| `Err` | Hằng chuỗi mã lỗi |
| `Const` | Hằng số dùng chung (mục 7) |
| `Level` | Dữ liệu màn đã parse (địa hình, vật thể, spawn) |
| `LevelLoader` | Parse JSON thành `Level` và kiểm tra hợp lệ |
| `World` | Mô phỏng thuần, không mạng: giữ toàn bộ trạng thái động, có `step(Input[])` trả về danh sách `Ev` |
| `Player` `Rock` `Key` `Door` `Lever` `Plate` `Mech` | Trạng thái từng loại vật thể trong `World`. `Mech` là cơ quan di chuyển, có `kind` = `MechKind.ELEV` (thang máy) hoặc `MechKind.GATE` (cửa chắn) |
| `Zone` | Vùng nước, dung nham, độc (tĩnh) |
| `Rect` | Hình chữ nhật AABB (x, y, w, h) |
| `Input` | `record Input(boolean l, boolean r, boolean j)` |
| `Ev` | Sự kiện sinh ra trong 1 tick (loại, id, acc, thông tin thêm) |
| `Snap` | Chuyển `World` thành JSON snapshot (phía server) và đọc JSON snapshot (phía client) |
| `Phys` | Hằng số vật lý và hàm va chạm |
| `Rec` | Ghi và đọc lại input từng tick của một trận (để tái hiện lỗi vật lý) |
| `Sim` | Chương trình dòng lệnh chạy `World` offline với input theo kịch bản hoặc file `Rec`, in trạng thái từng tick |
| `Element` | enum `FIRE`, `WATER` |
| `ZoneKind` | enum `WATER`, `LAVA`, `POISON` |
| `MechKind` | enum `ELEV`, `GATE` |

### server

| Lớp | Việc |
|---|---|
| `ServerMain` | Đọc cấu hình, mở DB, khởi động `TcpServer` và `UdpServer` |
| `TcpServer` | Vòng `accept()`, mỗi kết nối tạo 1 `ClientHandler` |
| `ClientHandler` | 1 thread đọc TCP của 1 client, parse `Msg`, chuyển cho `svc`. Có hàng đợi gửi và 1 thread ghi riêng |
| `UdpServer` | 1 thread duy nhất nhận mọi gói UDP, tra phòng, ghi input |
| `Sess` | Phiên của 1 kết nối: `acc`, `name`, `status`, `token`, `handler`, `room`, `udpAddr` |
| `Online` | Danh sách `Sess` đang đăng nhập (`ConcurrentHashMap<Integer, Sess>`) |
| `Rooms` | Danh sách phòng (`ConcurrentHashMap<String, Room>`), tạo/xóa phòng |
| `Room` | Thành viên (kèm nguyên tố `el` của từng người), trạng thái phòng, `World` đang chơi, khóa của phòng |
| `Loop` | `Runnable` chạy tick của 1 phòng |
| `AuthSvc` | REGISTER, LOGIN, LOGOUT |
| `RoomSvc` | CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, SELECT_LEVEL, SWAP, START_GAME |
| `RankSvc` | GET_RANK |
| `Db` | Kết nối MySQL (pool đơn giản) |
| `AccountDao` `LevelDao` `ResultDao` | Truy vấn bảng tương ứng |
| `Pass` | Băm và kiểm tra mật khẩu |
| `Levels` | Bộ nhớ đệm các màn (đọc từ DB lúc khởi động): `get(lv)`, `all()` |
| `Cfg` | Đọc `server.properties` |

### client

| Lớp | Việc |
|---|---|
| `ClientMain` | Điểm vào GUI |
| `Bot` | Điểm vào không GUI: client giả chơi theo kịch bản để test server (xem file debug) |
| `Win` | `JFrame` chính, dùng `CardLayout` đổi màn hình |
| `Model` | Trạng thái ứng dụng: người chơi của mình, danh sách online, danh sách phòng, phòng hiện tại, danh sách màn |
| `TcpConn` | Kết nối TCP, thread đọc, `send(Msg)` |
| `UdpConn` | `DatagramSocket` duy nhất để gửi và nhận (xem `02-protocol.md`) |
| `Screen` | Interface màn hình: `show()`, `hide()`, `onMsg(Msg)` |
| `LoginScreen` `LobbyScreen` `RoomScreen` `GameScreen` `RankScreen` | Các màn hình. Kết quả màn (thắng/thua) là lớp phủ trong `GameScreen` |
| `Canvas` | Vùng vẽ game, chỉ vẽ `View` |
| `View` | Mô hình thế giới phía client, dựng từ SNAP |
| `Interp` | Nội suy giữa các snapshot |
| `Keys` | Đọc bàn phím thành `Input` |
| `Assets` | Ánh xạ loại vật thể và sự kiện sang file hình/âm thanh (AssetManifest trong tài liệu BTL) |
| `Sfx` | Phát âm thanh |
| `Dbg` | Lớp phủ debug (hitbox, seq, ping, tick) |
| `Cfg` | Đọc `client.properties` |

## 5. Quy ước đặt tên

- Lớp: PascalCase, danh từ, ngắn (tối đa khoảng 12 ký tự).
- Hàm: camelCase, động từ, tối đa 2 từ (`send`, `step`, `join`, `loadLevel`).
- Biến, trường: camelCase, ưu tiên 1 từ hoặc viết tắt trong bảng dưới.
- Hằng: UPPER_SNAKE.
- Trường JSON: camelCase ngắn. Quy tắc cố định:
  - `acc` luôn là id tài khoản (số nguyên).
  - `id` luôn là id vật thể trong màn (chuỗi, ví dụ `"r1"`).
  - `room` là id phòng (chuỗi, ví dụ `"R1"`).
  - `lv` là id màn (số nguyên).
- Cột DB: snake_case, giữ như tài liệu thiết kế DB.
- Không dùng `import x.*` (tránh trùng tên với `java.awt`).
- Mỗi file một lớp cấp cao nhất.
- Boolean: trường đặt tên tính từ (`alive`, `done`, `on`), không tiền tố `is`.

Bảng viết tắt chuẩn (dùng thống nhất, không tự thêm viết tắt khác):

| Viết tắt | Nghĩa |
|---|---|
| `acc` | account |
| `rm` | room (biến cục bộ) |
| `lv` | level |
| `pl` | player (biến cục bộ) |
| `el` | element (FIRE/WATER) |
| `sess` | session |
| `msg` | message |
| `cfg` | config |
| `ev` | event |
| `ctl` | controller (plate hoặc lever điều khiển cơ quan) |
| `mech` | cơ quan (elevator hoặc gate) |
| `dx` `dy` | độ dịch chuyển |
| `vx` `vy` | vận tốc |
| `w` `h` | rộng, cao |
| `ms` `sec` | mili giây, giây |
| `seq` | số thứ tự gói |
| `ack` | seq đã nhận |
| `req` `res` | request, response |

## 6. Quyết định kiến trúc

### 6.1 Server giữ trạng thái chính, client chỉ gửi input

- Client gửi trạng thái phím (`l`, `r`, `j`), không gửi vị trí.
- Server chạy vật lý: vị trí, va chạm, đá, nút, cần gạt, thang máy, cửa chắn, vùng nguy hiểm, chìa khóa, cửa, thắng/thua.
- Server gửi snapshot đầy đủ cho cả 2 client (kể cả người vừa gửi input).
- Client không tự tính va chạm hay kết quả. Không có client-side prediction.

### 6.2 Tần số

| Việc | Tần số |
|---|---|
| Tick vật lý server | 60 Hz (dt cố định 1/60 giây) |
| Gửi SNAP (UDP) | 30 Hz (cứ 2 tick gửi 1 lần) |
| Gửi INPUT (UDP) | 30 Hz, và gửi ngay khi phím đổi |
| Vẽ client | 60 FPS |
| Nội suy client | trễ cố định 100 ms |
| PING TCP | 5 giây một lần |

### 6.3 Phân loại kênh

| Dữ liệu | Kênh | Ghi chú |
|---|---|---|
| Đăng ký, đăng nhập, đăng xuất | TCP | |
| Danh sách online, phòng, màn; tạo/vào/rời phòng; chọn màn; bắt đầu | TCP | Mọi push của lobby là bản đầy đủ, không phải delta |
| Sự kiện một lần: lấy chìa, qua cửa, chết, cần gạt, nút, cơ quan chạy, thắng, thua, hủy trận | TCP | Dùng để phát hiệu ứng và âm thanh |
| Bảng xếp hạng | TCP | |
| INPUT của client | UDP | Gói trạng thái, mất 1 gói không sao |
| SNAP của server | UDP | Luôn là bản đầy đủ. Gồm vị trí, sống/chết, đá, tiến trình cơ quan và cờ rời rạc (chìa, cửa, cần gạt, nút) |
| Bắt tay UDP, đo ping | UDP | |

Quy tắc cho client: snapshot là nguồn đúng về trạng thái hiển thị. Sự kiện TCP chỉ dùng để kích hoạt hiệu ứng và âm thanh, không dùng để sửa trạng thái.

### 6.4 Luồng (thread)

Server:
- 1 thread `TcpServer` (accept).
- Mỗi client: 1 thread đọc (`ClientHandler`) và 1 thread ghi (gửi từ hàng đợi). Mọi nơi gọi `handler.send(msg)` chỉ đưa vào hàng đợi, không bao giờ chặn.
- 1 thread `UdpServer`.
- 1 `ScheduledExecutorService` chạy `Loop` của tất cả phòng đang chơi.
- 1 `ExecutorService` 1 thread (`dbExec`) ghi DB khi kết thúc màn, để không chặn `Loop`.

Client:
- EDT (Swing) vẽ và xử lý UI.
- 1 thread đọc TCP (đẩy sang EDT bằng `SwingUtilities.invokeLater`).
- 1 thread đọc UDP (ghi snapshot mới nhất vào `AtomicReference`/hàng đợi nhỏ cho `Interp`).

### 6.5 Quy tắc khóa phía server

- Mỗi `Room` có 1 khóa: `synchronized (room)`.
- `Loop` giữ khóa phòng trong suốt 1 tick.
- Mọi thay đổi thành viên, trạng thái phòng (join, leave, start, abort) phải giữ khóa phòng.
- `World` chỉ được đọc/ghi khi giữ khóa phòng.
- Ngoại lệ: input của từng người chơi lưu bằng trường `volatile Input` trong `Sess` (UdpServer ghi, Loop đọc, không cần khóa).
- Không giữ khóa phòng khi làm việc chậm (DB, ghi socket). Gửi socket chỉ là đưa vào hàng đợi nên an toàn.
- Thứ tự khóa nếu cần nhiều khóa: `Rooms` rồi `Room`. Không bao giờ lấy ngược lại.

### 6.6 Nguồn dữ liệu màn

Các file `levels/*.json` trong `common` là nguồn gốc. Khi server khởi động, `LevelDao` đồng bộ file vào bảng `levels` (thêm mới hoặc cập nhật theo `level_name`). Sửa file rồi khởi động lại server là đủ để thử màn mới. Định dạng JSON màn nằm ở file đặc tả level.

## 7. Hằng số dùng chung (`Const`)

| Tên | Giá trị | Ý nghĩa |
|---|---|---|
| `TCP_PORT` | 5000 | Cổng TCP mặc định |
| `UDP_PORT` | 5001 | Cổng UDP mặc định |
| `TICK_HZ` | 60 | Tick vật lý |
| `SNAP_HZ` | 30 | Tần số SNAP |
| `IN_HZ` | 30 | Tần số INPUT |
| `MAX_LINE` | 262144 | Độ dài tối đa 1 dòng TCP (byte) |
| `MAX_UDP` | 1400 | Kích thước tối đa 1 datagram (byte) |
| `PING_MS` | 5000 | Chu kỳ PING TCP |
| `TCP_TO_MS` | 15000 | Server coi client mất kết nối nếu không nhận dòng nào |
| `UDP_TO_MS` | 5000 | Trong trận, không nhận UDP từ 1 người quá thời gian này thì hủy trận |
| `HELLO_MS` | 200 | Chu kỳ client gửi UDP_HELLO |
| `HELLO_WAIT_MS` | 8000 | Server chờ cả 2 bắt tay UDP xong |
| `JUMP_HOLD_MS` | 100 | Client giữ cờ `j` ít nhất chừng này sau khi bấm nhảy |
| `INTERP_MS` | 100 | Độ trễ nội suy phía client |
| `TILE` | 32 | Kích thước 1 ô (pixel) |

Hằng số vật lý (trọng lực, tốc độ, lực nhảy...) nằm trong `Phys`, định nghĩa ở file đặc tả lõi game.

## 8. Quyết định gameplay tạm chọn

Các điểm đề chưa nói rõ, tạm chọn như sau. Muốn đổi thì sửa tại đây trước khi code.

1. Bất kỳ nhân vật nào chết (Lửa chạm nước, Nước chạm dung nham, ai chạm độc) thì màn thua ngay (`LEVEL_FAILED`). Chủ phòng bấm bắt đầu lại hoặc thoát.
2. Nguyên tố (`el`) lưu theo phòng, không gắn cố định với chủ phòng.
   - Phòng mới: chủ phòng (người tạo) là Lửa. Người vào sau nhận nguyên tố đối lập.
   - Chủ phòng bấm Swap (`SWAP`) để đổi: cả 2 người đổi nguyên tố cho nhau. Nếu phòng chỉ có chủ phòng thì chủ phòng đổi giữa Lửa và Nước.
   - Chỉ swap được khi phòng `WAITING` (trước khi bắt đầu, và sau khi hết một trận).
   - Khi chủ phòng rời, người còn lại thành chủ phòng và giữ nguyên tố hiện tại.
3. Mỗi màn có đúng 1 chìa khóa và 1 cửa. Cửa khóa cho đến khi chìa được lấy. Người giữ chìa phải vào cửa trước, người còn lại chỉ vào được sau khi người giữ chìa đã vào.
4. Người đã vào cửa chuyển sang trạng thái `done`: ẩn, không va chạm, không chết.
5. Cần gạt đổi trạng thái khi có nhân vật chạm vào (cạnh chuyển từ không chạm sang chạm), có thời gian chờ 0,5 giây. Nút (`Plate`) đang nhấn khi có nhân vật hoặc đá đè lên.
6. Mọi vật rắn đang di chuyển (thang máy, nhân vật, đá) mang theo vật đang đứng trên nó. Đỉnh đầu nhân vật là một mặt phẳng di chuyển được.
   - A đứng trên B khi đáy hitbox của A chạm đỉnh hitbox của B và khoảng x của A giao với khoảng x của B (giao một phần là đủ).
   - Mỗi tick, B dịch (dx, dy) thì A dịch cùng (dx, dy) trước khi A tự di chuyển. Xử lý theo thứ tự từ dưới lên: thang máy mang người dưới, người dưới mang người trên. Vì vậy thang máy chạy thì 2 người đứng chồng vẫn đi cùng.
   - A vẫn tự đi và nhảy trên B. Khi B đi sang trái, A vẫn đứng được chừng nào khoảng x còn giao với B. Hết giao nhau thì A rơi.
   - Nhân vật đã `done` không mang ai (đã bị loại khỏi va chạm).
   - Chi tiết thuật toán nằm ở file đặc tả lõi game.
7. Thời gian hoàn thành tính từ lúc server gửi `GAME_GO`, đo bằng số tick chia 60.
8. Rời phòng hoặc mất kết nối giữa trận: người còn lại nhận `GAME_ABORT`, lượt chơi vẫn tính vào số lần chơi, không tính hoàn thành.
9. Mỗi tài khoản chỉ đăng nhập một nơi tại một thời điểm (đăng nhập lần 2 bị từ chối).
10. Không có reconnect. Mất kết nối giữa trận là hủy trận.
11. Chủ phòng có thể bấm Chơi lại giữa trận (`RESTART`) để thoát bế tắc (ví dụ đá bị đẩy vào góc không kéo ra được). Tính là một lượt chơi mới.
12. Mọi màn có đúng 1 chìa khóa và 1 cửa (bộ nạp màn kiểm tra điều kiện này).

## 9. Ngoài phạm vi

Client-side prediction, NAT traversal, mã hóa đường truyền (mật khẩu đi dạng thường qua TCP, chấp nhận trong bài tập), chat, khán giả, reconnect.

## 10. Ánh xạ tên trong tài liệu BTL sang code

| Tài liệu BTL | Trong code |
|---|---|
| `RoomState` | `World` (common, mô phỏng thuần) cộng `Room` (server, runtime) |
| `OnlinePlayer` | `Sess` |
| `PlayerSession` | `Player` (trong `World`: x, y, sống/chết) cộng các trường mạng trong `Sess` (`udpAddr`, input) |
| `RoomManager` | `Rooms` |
| `UdpListenerThread` | `UdpServer` |
| `ButtonState` | `Plate` (tránh trùng `java.awt.Button`) |
| `RockState` `KeyState` `DoorState` `LeverState` | `Rock` `Key` `Door` `Lever` |
| `ElevatorState` `GateState` | `Mech` (trường `kind` phân biệt) |
| `GameCanvasView` | `Canvas` |
| `AssetManifest` | `Assets` |
| `START_GAME_CMD` | `START_GAME` |
| `PLAYER_POSITION` | `INPUT` (client gửi), `SNAP` (server gửi) |
| `ROOM_DISBANDED` | Không dùng. Thay bằng `ROOM_INFO` và `GAME_ABORT` |
