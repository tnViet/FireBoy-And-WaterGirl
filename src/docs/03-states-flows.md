# 03 - Máy trạng thái và luồng end-to-end

Bổ sung cho `02-protocol.md`. Mọi quy tắc ở đây là bắt buộc, dùng để code và để dò lỗi khi trạng thái bị lệch.

## 1. Trạng thái phía server

### 1.1 Ba tầng trạng thái

| Tầng | Lưu ở | Giá trị |
|---|---|---|
| Kết nối (S0..S3) | suy ra từ `Sess` | S0 chưa đăng nhập, S1 lobby, S2 trong phòng chờ, S3 trong trận |
| Phòng | `Room.phase` | `IDLE`, `LOADING`, `RUNNING` |
| Màn đang chơi | `World.phase` | `RUN`, `WON`, `LOST` |

Liên hệ:
- `Room.phase` = `IDLE` thì phòng `WAITING` (JSON), các thành viên ở S2.
- `Room.phase` = `LOADING` hoặc `RUNNING` thì phòng `PLAYING` (JSON), các thành viên ở S3.
- `LOADING`: từ lúc `START_GAME` được chấp nhận đến lúc cả 2 bắt tay UDP xong (`GAME_GO`). Gồm cả bước ghi DB và chờ UDP_HELLO.
- `RUNNING`: `Loop` đang tick. `World` chỉ tồn tại ở `RUNNING` (và ở cuối `LOADING` sau khi tạo xong).

### 1.2 Phòng

```
IDLE --START_GAME/RESTART hợp lệ--> LOADING --đủ 2 UDP_HELLO--> RUNNING
LOADING --quá HELLO_WAIT_MS--> (abort UDP_TIMEOUT) --> IDLE
LOADING/RUNNING --1 người rời hoặc mất kết nối--> (abort PEER_LEFT) --> IDLE
RUNNING --World.phase = WON--> (LEVEL_COMPLETE) --> IDLE
RUNNING --World.phase = LOST--> (LEVEL_FAILED) --> IDLE
RUNNING --không nhận UDP quá UDP_TO_MS--> (abort UDP_TIMEOUT) --> IDLE
RUNNING --Loop ném lỗi--> (abort SERVER_ERROR) --> IDLE
```

Mỗi trận có `Room.matchId` (số nguyên tăng dần). Mọi tác vụ bất đồng bộ của một trận (tick, hẹn giờ HELLO, ghi DB) giữ `matchId` lúc tạo và chỉ hành động nếu `room.matchId` vẫn bằng giá trị đó. Nhờ vậy tác vụ cũ không phá trận mới.

### 1.3 Quy trình kết thúc trận (`Room.endMatch`)

Luôn chạy trong `synchronized (room)`, đúng thứ tự:
1. Hủy các tác vụ hẹn giờ và `Loop` của trận (`ScheduledFuture.cancel`).
2. Gửi message kết thúc (`LEVEL_COMPLETE`, `LEVEL_FAILED` hoặc `GAME_ABORT`) cho thành viên còn kết nối.
3. `phase = IDLE`, `world = null`, `matchId++`.
4. Với mỗi thành viên: `status = IDLE`, xóa `udp`, `input`, `lastSeq`, `lastUdpMs`.
5. Gửi `ROOM_INFO` cho thành viên.
6. Thu thập thông tin cần ghi DB (nếu thắng) vào biến cục bộ.

Sau khi nhả khóa: broadcast `ROOM_LIST`, `ONLINE_LIST`; nếu thắng thì `dbExec.submit(() -> ResultDao.onWin(...))`.

## 2. Trạng thái phía client

### 2.1 Màn hình

| Trạng thái | Màn hình | Ghi chú |
|---|---|---|
| `LOGIN` | `LoginScreen` | Trạng thái đầu |
| `LOBBY` | `LobbyScreen` | Danh sách online, phòng |
| `ROOM` | `RoomScreen` | Phòng chờ |
| `LOADING` | `GameScreen` (hiện "Đang chờ...") | Từ `GAME_START` đến `GAME_GO` |
| `PLAY` | `GameScreen` | Đang chơi |
| `RESULT` | `GameScreen` (lớp phủ kết quả) | Sau `LEVEL_COMPLETE` hoặc `LEVEL_FAILED` |
| `RANK` | `RankScreen` | Xem bảng xếp hạng, quay lại màn trước đó |

### 2.2 Bảng chuyển trạng thái

| Từ | Sự kiện | Sang | Việc cần làm |
|---|---|---|---|
| `LOGIN` | nhận `LOGIN_SUCCESS` | `LOBBY` | Lưu `acc`, `name`, `token` vào `Model` |
| `LOGIN` | nhận `REGISTER_SUCCESS` | `LOGIN` | Hiện "Đăng ký thành công" |
| `LOGIN` | nhận `LOGIN_FAILED` hoặc `REGISTER_FAILED` | `LOGIN` | Hiện thông báo theo `code` |
| `LOBBY` | nhận `ROOM_INFO` | `ROOM` | |
| `LOBBY` | bấm Xếp hạng | `RANK` | Gửi `GET_RANK` |
| `LOBBY` | bấm Đăng xuất, nhận `LOGOUT_SUCCESS` | `LOGIN` | Xóa `Model` |
| `ROOM` | nhận `GAME_START` | `LOADING` | Mở `UdpConn`, bắt đầu gửi `UDP_HELLO` |
| `ROOM` | bấm Thoát, nhận `LEFT_ROOM` | `LOBBY` | |
| `ROOM` | nhận `ROOM_INFO` | `ROOM` | Cập nhật hiển thị |
| `ROOM` | nhận `GAME_ABORT` | `ROOM` | Hiện lý do (trận bị hủy trước khi bắt đầu, ví dụ người kia rời lúc server đang ghi DB) |
| `LOADING` | nhận `GAME_GO` | `PLAY` | Bắt đầu gửi `INPUT` |
| `LOADING` | nhận `GAME_ABORT` (không phải `RESTART`) | `ROOM` | Đóng `UdpConn`, hiện lý do |
| `LOADING` hoặc `PLAY` | nhận `GAME_ABORT` với `RESTART` | `LOADING` | Đóng `UdpConn` cũ, chờ `GAME_START` mới |
| `PLAY` | nhận `LEVEL_COMPLETE` hoặc `LEVEL_FAILED` | `RESULT` | Đóng `UdpConn`, hiện kết quả |
| `PLAY` | nhận `GAME_ABORT` (không phải `RESTART`) | `ROOM` | Đóng `UdpConn`, hiện lý do |
| `PLAY` | bấm Thoát, nhận `LEFT_ROOM` | `LOBBY` | Đóng `UdpConn` |
| `PLAY` | chủ phòng bấm Chơi lại | `PLAY` | Gửi `RESTART` |
| `RESULT` | nhận `GAME_START` | `LOADING` | |
| `RESULT` | bấm Về phòng | `ROOM` | |
| `RESULT` | bấm Thoát, nhận `LEFT_ROOM` | `LOBBY` | |
| `RANK` | bấm Quay lại | màn trước đó | |
| bất kỳ | TCP mất kết nối | `LOGIN` | Hộp thoại "Mất kết nối", xóa `Model`, đóng `UdpConn` |

Quy tắc xử lý `ROOM_INFO`, `ROOM_LIST`, `ONLINE_LIST`: luôn cập nhật `Model`. Chỉ đổi màn hình khi ở `LOBBY` (sang `ROOM`). Ở `LOADING`, `PLAY`, `RESULT` thì chỉ cập nhật `Model`, không đổi màn. Màn `RESULT` đọc `Model.room.host` để hiện nút của chủ phòng, nên tự cập nhật khi chủ phòng đổi.

Lớp phủ `RESULT`:
- Chủ phòng: nút Chơi lại (gửi `START_GAME`), nút Màn kế (gửi `SELECT_LEVEL` với `lv` kế tiếp rồi `START_GAME`, gửi liền nhau, server xử lý tuần tự trên cùng kết nối), nút Về phòng, nút Thoát.
- Người còn lại: dòng "Chờ chủ phòng", nút Về phòng, nút Thoát.
- Nếu không còn màn kế tiếp thì ẩn nút Màn kế.

## 3. Luồng chi tiết

Ký hiệu: `C1` chủ phòng, `C2` người vào sau, `S` server, `DB` MySQL. `>` là TCP, `=` là UDP.

### 3.1 Đăng ký và đăng nhập

```
C1 > REGISTER
S    kiểm tra dữ liệu (02 mục 1.4) --sai--> REGISTER_FAILED BAD_INPUT
S -> DB: AccountDao.create (accounts + player_results, 1 transaction)
        trùng username --> REGISTER_FAILED USER_EXISTS
S  > REGISTER_SUCCESS
C1 > LOGIN
S -> DB: AccountDao.find(username)   không có hoặc Pass.check sai --> LOGIN_FAILED BAD_LOGIN
S    Online.add(sess) bằng putIfAbsent  đã có --> LOGIN_FAILED ALREADY_LOGGED_IN
S  > LOGIN_SUCCESS, LEVELS, ONLINE_LIST, ROOM_LIST      (cho C1)
S  > ONLINE_LIST                                         (cho mọi client khác)
```

### 3.2 Tạo phòng, vào phòng, swap, chọn màn

```
C1 > CREATE_ROOM        S: Rooms.create (C1 host, el=FIRE, lv=màn nhỏ nhất)
S  > ROOM_INFO (C1), ROOM_LIST (tất cả)
C2 > JOIN_ROOM {room}   S: thêm C2 với el đối lập
S  > ROOM_INFO (C1 và C2), ROOM_LIST (tất cả)
C1 > SWAP               S: đổi el của cả 2
S  > ROOM_INFO (C1 và C2)
C1 > SELECT_LEVEL {lv}
S  > ROOM_INFO (C1 và C2)
```

### 3.3 Bắt đầu trận

```
C1 > START_GAME
S    [lock] kiểm tra: host, đủ 2 người, phase = IDLE, màn tồn tại; phase = LOADING; ghi matchId, chốt (acc1, acc2, lv) [unlock]
S -> DB: ResultDao.onStart (attempts++, total_plays++)     lỗi --> phase = IDLE, ERR DB_ERROR
S    [lock] kiểm tra matchId còn đúng và đủ 2 người --> tạo World, status PLAYING, hẹn giờ HELLO_WAIT_MS [unlock]
S  > GAME_START (C1, C2)
S  > ROOM_LIST, ONLINE_LIST (tất cả)
C1, C2 = UDP_HELLO (lặp mỗi HELLO_MS)
S    [UdpServer] kiểm tra token, ghi Sess.udp, trả = UDP_ACK
S    khi đủ 2 người đã có udp: phase = RUNNING, đặt lastUdpMs = now, hủy hẹn giờ HELLO, lên lịch Loop
S  > GAME_GO (C1, C2)
C1, C2 = INPUT (30 Hz) ... S = SNAP (30 Hz)
```

Nếu một người rời trong lúc ghi DB (giữa 2 lần khóa), bước kiểm tra `matchId` ở lần khóa thứ hai thất bại: không tạo `World`, không gửi `GAME_START`. `RoomSvc.leave` đã tự xử lý (phase về `IDLE`, người còn lại nhận `GAME_ABORT`).

### 3.4 Lấy chìa khóa và vào cửa

```
Loop tick: World.step --> Ev KEY_COLLECTED
S  > KEY_COLLECTED {id, acc}                 (C1, C2)
S  = SNAP có keys[].got = true, doors[].open = true
Loop tick: người giữ chìa vào cửa --> Ev DOOR_PASSED
S  > DOOR_PASSED {id, acc}
Loop tick: người kia vào cửa --> Ev DOOR_PASSED, World.phase = WON, Ev LEVEL_COMPLETE
S  > DOOR_PASSED, LEVEL_COMPLETE {lv, sec}
S    Room.endMatch ... ROOM_INFO, ROOM_LIST, ONLINE_LIST
S -> DB (dbExec): ResultDao.onWin
```

### 3.5 Chết

```
Loop tick: World.step --> Ev PLAYER_DIED, Ev LEVEL_FAILED, World.phase = LOST
S  > PLAYER_DIED {acc, cause}, LEVEL_FAILED {cause, acc}
S    Room.endMatch ... (không ghi thắng vào DB)
```

### 3.5b Chơi lại giữa trận

```
C1 > RESTART
S    [lock] kiểm tra host và phase != IDLE; endMatch với GAME_ABORT RESTART [unlock]
S  > GAME_ABORT {reason: RESTART} (C1, C2)
S    chạy tiếp quy trình 3.3 từ bước ghi DB
S  > GAME_START (C1, C2)
```

### 3.6 Rời phòng giữa trận

```
C2 > LEAVE_ROOM
S    [lock] bỏ C2 khỏi phòng, C2.room = null, C2.status = IDLE
S    phase != IDLE: người còn lại nhận GAME_ABORT PEER_LEFT, endMatch
S    C1 thành chủ phòng (giữ el), gửi ROOM_INFO cho C1 [unlock]
S  > LEFT_ROOM (C2)
S  > ROOM_LIST, ONLINE_LIST (tất cả)
```

### 3.7 Mất kết nối đột ngột

Giống 3.6 nhưng được kích hoạt bởi `ClientHandler.close()` (IOException, `readLine` trả `null`, quá `TCP_TO_MS`), sau đó:
- Bỏ `Sess` khỏi `Online`.
- Broadcast `ONLINE_LIST`.
- Không gửi `LEFT_ROOM` hay `LOGOUT_SUCCESS` (socket đã đóng).

### 3.8 Timeout UDP

```
Loop tick: now - lastUdpMs của một người > UDP_TO_MS
S  > GAME_ABORT {reason: UDP_TIMEOUT} (cả 2)
S    endMatch
```

## 4. Tình huống đồng thời và cách chốt

| Tình huống | Cách xử lý |
|---|---|
| 2 người cùng `JOIN_ROOM` vào phòng còn 1 chỗ | Kiểm tra và thêm thành viên trong `synchronized (room)`. Người sau nhận `ROOM_FULL` |
| `JOIN_ROOM` đúng lúc chủ phòng `START_GAME` | `JOIN_ROOM` thấy `phase != IDLE` trả `BAD_STATE`. Nếu `JOIN_ROOM` vào trước thì `START_GAME` thấy đủ 2 người |
| `START_GAME` bấm hai lần | Lần hai thấy `phase = LOADING`, trả `BAD_STATE` |
| Người kia rời trong lúc chủ phòng `START_GAME` | Xem 3.3, kiểm tra `matchId` |
| Cả 2 `LEAVE_ROOM` cùng lúc | Tuần tự nhờ khóa phòng. Người thứ hai thấy phòng trống thì xóa phòng. `Rooms.remove` chỉ gọi khi giữ khóa phòng và phòng đã trống |
| Hai tab đăng nhập cùng tài khoản | `Online.add` dùng `putIfAbsent`, kẻ đến sau nhận `ALREADY_LOGGED_IN` |
| Lệnh phòng gửi sau khi người dùng đã bị đá khỏi phòng | `Sess.room == null` nên trả `BAD_STATE` |
| `INPUT` đến sau khi trận kết thúc | `UdpServer` thấy phòng không `RUNNING` nên bỏ qua |
| `Loop` chạy trễ vài tick | `scheduleAtFixedRate` chạy bù. `World.step` luôn dùng dt cố định, không đo thời gian thực |
| Hai sự kiện cùng tick (một người chết, người kia vào cửa) | Thứ tự trong `World.step`: nguy hiểm trước, cửa sau. Chết thắng |

## 5. Bất biến (dùng để viết assert và dò lỗi)

1. `Room.members.size() <= 2` và 2 người có `el` khác nhau (khi đủ 2).
2. `Room.host` luôn là một thành viên của phòng.
3. Phòng trống thì không còn trong `Rooms`.
4. `Sess.room != null` khi và chỉ khi `Sess` nằm trong `members` của đúng phòng đó.
5. `Sess.status` = `PLAYING` khi và chỉ khi phòng của nó có `phase != IDLE`.
6. `Room.world != null` khi `phase = RUNNING`.
7. `Sess.udp != null` cho cả 2 thành viên khi `phase = RUNNING`.
8. Tài khoản `acc` có tối đa 1 `Sess` trong `Online`.
9. Mỗi `acc` có không quá 1 `Sess.room` tại một thời điểm.

Server nên có hàm `Rooms.check()` (chỉ chạy khi bật `-Dfw.assert=true`) kiểm tra các bất biến trên, gọi sau mỗi thao tác phòng. Vi phạm thì log ERROR kèm trạng thái đầy đủ.
