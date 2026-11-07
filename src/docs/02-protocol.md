# 02 - Đặc tả giao thức

Đây là hợp đồng giữa client và server. Không đổi tên message, tên trường, thứ tự gửi nếu chưa sửa file này. Hằng chuỗi nằm trong `MsgType` và `Err` (common).

Quy tắc trường JSON: `acc` là id tài khoản (số), `id` là id vật thể (chuỗi), `room` là id phòng (chuỗi), `lv` là id màn (số).

## 1. Quy tắc chung

### 1.1 Gói tin

Mọi gói (TCP và UDP) là 1 object JSON:

```json
{"type":"LOGIN","rid":1,"data":{"username":"an","password":"secret1"}}
```

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `type` | String | Bắt buộc. Tên lệnh |
| `rid` | int | Tùy chọn. Client đặt ở request, server trả lại đúng giá trị này trong phản hồi trực tiếp (kể cả `ERR`). Push của server không có `rid`. Client tăng dần từ 1 |
| `data` | object | Bắt buộc, có thể là `{}` |

Trường lạ trong `data` bị bỏ qua. Trường thiếu hoặc sai kiểu thì trả `ERR` với `code` = `BAD_INPUT` và `msg` nêu tên trường.

### 1.2 TCP

- Mã hóa UTF-8. Mỗi gói là 1 dòng JSON kết thúc bằng `\n`. JSON không được chứa ký tự xuống dòng thô (Gson mặc định đã đảm bảo).
- Độ dài dòng tối đa `MAX_LINE`. Vượt quá thì server đóng kết nối.
- Đọc bằng `BufferedReader.readLine()`, ghi bằng `PrintWriter` hoặc `BufferedWriter` rồi `flush()` sau mỗi gói.
- Mọi lần ghi vào socket đi qua `send(Msg)` (đưa vào hàng đợi, 1 thread ghi). Không ghi trực tiếp từ nơi khác.
- Dòng không phải JSON hợp lệ: trả `ERR BAD_JSON`, giữ kết nối. Quá 3 dòng hỏng liên tiếp thì đóng kết nối.
- `type` không tồn tại: trả `ERR BAD_TYPE`.
- Server đặt `setSoTimeout(TCP_TO_MS)`. Hết thời gian mà không nhận dòng nào thì coi là mất kết nối. Client gửi `PING` mỗi `PING_MS`.

### 1.3 UDP

- Mỗi datagram là đúng 1 gói JSON, UTF-8, không có ký tự phân cách. Tối đa `MAX_UDP` byte (server gửi SNAP lớn hơn thì ghi log ERROR và không gửi).
- Client dùng đúng 1 `DatagramSocket` để vừa gửi vừa nhận, tạo khi nhận `GAME_START`, đóng khi trận kết thúc. Server trả lời về địa chỉ nguồn của gói nhận được.
- Gói hỏng hoặc không hợp lệ bị bỏ qua và ghi log mức WARN, không trả lỗi.
- Mọi gói UDP từ client có `room` và `acc` trong `data`.

### 1.4 Dữ liệu hợp lệ

| Trường | Quy tắc |
|---|---|
| `username` | 3 đến 20 ký tự, chỉ `A-Z a-z 0-9 _` |
| `password` | 6 đến 50 ký tự |
| `name` | 1 đến 20 ký tự, không ký tự điều khiển |
| `limit` | 1 đến 100, mặc định 10 |

### 1.5 Phản hồi lỗi chung

```json
{"type":"ERR","rid":7,"data":{"code":"BAD_STATE","cmd":"START_GAME","msg":"room is not WAITING"}}
```

`cmd` là `type` của request gây lỗi. `msg` là mô tả cho người debug (tiếng Anh, ngắn).

## 2. Mã lỗi (`Err`)

| Mã | Khi nào |
|---|---|
| `BAD_JSON` | Dòng không phải JSON |
| `BAD_TYPE` | `type` không tồn tại |
| `BAD_INPUT` | Thiếu hoặc sai trường, vi phạm mục 1.4 |
| `NOT_LOGGED_IN` | Chưa đăng nhập mà gửi lệnh cần đăng nhập |
| `BAD_STATE` | Lệnh không hợp lệ ở trạng thái hiện tại (mục 5) |
| `BAD_LOGIN` | Sai username hoặc mật khẩu |
| `ALREADY_LOGGED_IN` | Tài khoản đang online ở nơi khác |
| `USER_EXISTS` | Username đã tồn tại |
| `ROOM_NOT_FOUND` | Không có phòng với `room` đó |
| `ROOM_FULL` | Phòng đã đủ 2 người |
| `NOT_HOST` | Lệnh chỉ chủ phòng được gửi |
| `NEED_2` | Bắt đầu khi phòng chưa đủ 2 người |
| `LEVEL_NOT_FOUND` | Không có màn với `lv` đó |
| `DB_ERROR` | Lỗi cơ sở dữ liệu |
| `INTERNAL` | Lỗi không lường trước ở server (ghi log đầy đủ stack trace) |

## 3. TCP: client gửi lên server

Quy ước trạng thái: S0 = chưa đăng nhập, S1 = đã đăng nhập, chưa vào phòng (lobby), S2 = trong phòng, phòng đang `WAITING`, S3 = trong phòng, phòng đang `PLAYING`.

| Type | Trạng thái được phép | `data` | Phản hồi thành công | Lỗi có thể |
|---|---|---|---|---|
| `REGISTER` | S0 | `username`, `password`, `name` | `REGISTER_SUCCESS {}` | `REGISTER_FAILED` với `code` = `USER_EXISTS`, `BAD_INPUT` hoặc `DB_ERROR` |
| `LOGIN` | S0 | `username`, `password` | `LOGIN_SUCCESS`, sau đó `LEVELS`, `ONLINE_LIST`, `ROOM_LIST` | `LOGIN_FAILED` với `code` = `BAD_LOGIN`, `ALREADY_LOGGED_IN`, `BAD_INPUT` hoặc `DB_ERROR` |
| `LOGOUT` | S1, S2, S3 | `{}` | `LOGOUT_SUCCESS {}`, trạng thái về S0 (kết nối giữ nguyên) | |
| `PING` | mọi trạng thái | `{}` | `PONG {}` | |
| `GET_RANK` | S1, S2 | `kind` (`"ALL"` hoặc `"LEVEL"`), `lv` (chỉ khi `LEVEL`), `limit` | `RANK` | `BAD_INPUT`, `LEVEL_NOT_FOUND`, `DB_ERROR` |
| `CREATE_ROOM` | S1 | `{}` | `ROOM_INFO` | `BAD_STATE` |
| `JOIN_ROOM` | S1 | `room` | `ROOM_INFO` (gửi cho cả 2 người) | `ROOM_NOT_FOUND`, `ROOM_FULL`, `BAD_STATE` (phòng đang PLAYING) |
| `LEAVE_ROOM` | S2, S3 | `{}` | `LEFT_ROOM {}` | `BAD_STATE` |
| `SELECT_LEVEL` | S2 (chủ phòng) | `lv` | `ROOM_INFO` (gửi cả 2 người) | `NOT_HOST`, `LEVEL_NOT_FOUND`, `BAD_STATE` |
| `SWAP` | S2 (chủ phòng) | `{}` | `ROOM_INFO` (gửi cả 2 người) | `NOT_HOST`, `BAD_STATE` |
| `RESTART` | S3 (chủ phòng) | `{}` | `GAME_ABORT` (`reason` = `RESTART`) rồi `GAME_START` cho cả 2 | `NOT_HOST`, `BAD_STATE`, `DB_ERROR` |
| `START_GAME` | S2 (chủ phòng) | `{}` | `GAME_START` (gửi cả 2 người) | `NOT_HOST`, `NEED_2`, `LEVEL_NOT_FOUND`, `DB_ERROR`, `BAD_STATE` |

Quy tắc về trạng thái sai:
- Ở S0 mà gửi lệnh khác `REGISTER`, `LOGIN`, `PING` thì trả `NOT_LOGGED_IN`.
- Ở S1 trở lên mà gửi `REGISTER` hoặc `LOGIN` thì trả `BAD_STATE`.
- Các trường hợp còn lại không nằm trong cột "Trạng thái được phép" thì trả `BAD_STATE`.

Hành vi chi tiết:

- `LOGOUT` và mất kết nối: nếu đang trong phòng thì xử lý như `LEAVE_ROOM` trước. Sau đó gỡ khỏi `Online` và broadcast `ONLINE_LIST`.
- `CREATE_ROOM`: phòng mới có `lv` là id màn nhỏ nhất, người tạo là chủ phòng với `el` = `FIRE`. Người chơi chuyển sang S2.
- `JOIN_ROOM`: người vào chuyển sang S2 và nhận `el` đối lập với người đang ở trong phòng.
- `SWAP`: đổi `el` của mọi thành viên (`FIRE` thành `WATER` và ngược lại). Phòng có 2 người thì 2 người đổi cho nhau. Phòng chỉ có chủ phòng thì chủ phòng đổi `el` của mình. Không đổi trạng thái nào khác.
- `LEAVE_ROOM` khi S2:
  - Nếu còn người khác: người đó thành chủ phòng và giữ nguyên `el` hiện tại, gửi `ROOM_INFO` cho họ.
  - Nếu hết người: xóa phòng.
- `LEAVE_ROOM` khi S3: người còn lại nhận `GAME_ABORT` với `reason` = `PEER_LEFT`, rồi thành chủ phòng của phòng `WAITING` (nhận `ROOM_INFO`).
- `RESTART`: hủy trận hiện tại (gửi `GAME_ABORT` với `reason` = `RESTART` cho cả 2, phòng về `WAITING`) rồi chạy ngay quy trình `START_GAME` với cùng `lv` và cùng `el`. Tính thêm một lượt chơi (`attempts`, `total_plays` tăng). Client nhận `GAME_ABORT` có `reason` = `RESTART` thì ở nguyên màn chơi và chờ `GAME_START`, không quay về phòng.
- `START_GAME`, thứ tự xử lý: kiểm tra điều kiện, ghi DB (tăng `attempts` trong `player_level_stats` và `total_plays` trong `player_results` của cả 2 tài khoản, ngay tại thread `ClientHandler`), tạo `World`, đặt phòng `PLAYING` và 2 người `PLAYING`, gửi `GAME_START` cho cả 2, broadcast `ROOM_LIST` và `ONLINE_LIST`. Sau đó chờ bắt tay UDP (mục 6).
- Sau khi trận kết thúc (thắng, thua, hủy), phòng về `WAITING`, 2 người về `IDLE`, phòng ở trạng thái S2 phía server. Chủ phòng có thể `SELECT_LEVEL` rồi `START_GAME` để chơi tiếp hoặc chơi lại.

Ví dụ:

```json
{"type":"REGISTER","rid":1,"data":{"username":"an","password":"secret1","name":"An"}}
{"type":"LOGIN","rid":2,"data":{"username":"an","password":"secret1"}}
{"type":"CREATE_ROOM","rid":3,"data":{}}
{"type":"JOIN_ROOM","rid":4,"data":{"room":"R1"}}
{"type":"SELECT_LEVEL","rid":5,"data":{"lv":2}}
{"type":"SWAP","rid":5,"data":{}}
{"type":"START_GAME","rid":6,"data":{}}
{"type":"RESTART","rid":6,"data":{}}
{"type":"GET_RANK","rid":7,"data":{"kind":"LEVEL","lv":2,"limit":10}}
{"type":"LEAVE_ROOM","rid":8,"data":{}}
{"type":"LOGOUT","rid":9,"data":{}}
```

## 4. TCP: server gửi xuống client

Phản hồi trực tiếp có `rid` của request. Push không có `rid`.

### 4.1 Phản hồi trực tiếp

| Type | `data` |
|---|---|
| `REGISTER_SUCCESS` | `{}` |
| `REGISTER_FAILED` | `code`, `msg` |
| `LOGIN_SUCCESS` | `acc` (int), `name`, `token` (chuỗi hex 32 ký tự, ngẫu nhiên bằng `SecureRandom`, dùng cho UDP_HELLO) |
| `LOGIN_FAILED` | `code`, `msg` |
| `LOGOUT_SUCCESS` | `{}` |
| `LEFT_ROOM` | `{}` |
| `PONG` | `{}` |
| `RANK` | xem bên dưới |
| `ERR` | `code`, `cmd`, `msg` |

`RANK` khi `kind` = `"ALL"`:

```json
{"type":"RANK","rid":7,"data":{"kind":"ALL","rows":[
  {"pos":1,"name":"An","done":5,"plays":9},
  {"pos":2,"name":"Binh","done":5,"plays":12}]}}
```

`RANK` khi `kind` = `"LEVEL"`:

```json
{"type":"RANK","rid":7,"data":{"kind":"LEVEL","lv":2,"rows":[
  {"pos":1,"name":"An","sec":41.25,"tries":3},
  {"pos":2,"name":"Binh","sec":43.10,"tries":2}]}}
```

Quy tắc sắp xếp xem file đặc tả DB. `sec` là thời gian tốt nhất của tài khoản đó trên màn đó.

### 4.2 Push của lobby (luôn là bản đầy đủ)

Gửi cho mọi client đã đăng nhập (S1, S2, S3) mỗi khi dữ liệu đổi. Client thay thế hoàn toàn dữ liệu cũ bằng dữ liệu mới.

```json
{"type":"LEVELS","data":{"levels":[{"lv":1,"name":"Level 1"},{"lv":2,"name":"Level 2"}]}}
{"type":"ONLINE_LIST","data":{"players":[{"acc":5,"name":"An","status":"IDLE"},{"acc":9,"name":"Binh","status":"PLAYING"}]}}
{"type":"ROOM_LIST","data":{"rooms":[{"room":"R1","host":"An","n":1,"status":"WAITING"}]}}
```

- `status` của người chơi: `IDLE` hoặc `PLAYING`.
- `status` của phòng: `WAITING` hoặc `PLAYING`. `n` là số người trong phòng.
- `LEVELS` chỉ gửi một lần sau `LOGIN_SUCCESS`.
- Thứ tự sau đăng nhập: `LOGIN_SUCCESS`, `LEVELS`, `ONLINE_LIST`, `ROOM_LIST`.
- `ONLINE_LIST` broadcast khi: có người đăng nhập, đăng xuất, mất kết nối, đổi `status`.
- `ROOM_LIST` broadcast khi: tạo phòng, có người vào hoặc rời, phòng đổi `status`, xóa phòng.

`ROOM_INFO` (chỉ gửi cho thành viên phòng, mỗi khi phòng đổi):

```json
{"type":"ROOM_INFO","rid":3,"data":{"room":"R1","host":5,"status":"WAITING","lv":1,
  "members":[{"acc":5,"name":"An","el":"FIRE"},{"acc":9,"name":"Binh","el":"WATER"}]}}
```

`host` là `acc` của chủ phòng. `el` của từng người do phòng lưu (`Room`), chỉ đổi khi `SWAP` hoặc khi người thứ 2 vào phòng (nhận nguyên tố đối lập). Chủ phòng đổi do người cũ rời thì `el` không đổi. Hai người trong phòng luôn có `el` khác nhau.

### 4.3 Bắt đầu trận

```json
{"type":"GAME_START","rid":6,"data":{
  "room":"R1","lv":2,"you":5,"udpPort":5001,
  "players":[{"acc":5,"name":"An","el":"FIRE","x":64.0,"y":640.0},
             {"acc":9,"name":"Binh","el":"WATER","x":96.0,"y":640.0}],
  "level":{ }}}
```

- `level` là toàn bộ JSON của màn (định dạng ở file đặc tả level). Client dựng địa hình và vật thể từ đây, không cần DB.
- `you` là `acc` của người nhận.
- `x`, `y` là vị trí ban đầu lấy từ `spawn` của màn.
- `rid` chỉ có trong gói gửi cho người bấm `START_GAME`. Gói gửi cho người kia không có `rid`.

`GAME_GO` (push, gửi cho cả 2 khi cả 2 đã bắt tay UDP xong, ngay trước khi `Loop` bắt đầu tick):

```json
{"type":"GAME_GO","data":{}}
```

### 4.4 Sự kiện trong trận (push, TCP)

Chỉ dùng để phát hiệu ứng và âm thanh. Không dùng để sửa trạng thái (trạng thái lấy từ SNAP).

| Type | `data` | Khi nào | Âm thanh |
|---|---|---|---|
| `KEY_COLLECTED` | `id`, `acc` (người lấy) | Chìa khóa vừa được lấy | lấy chìa |
| `DOOR_PASSED` | `id`, `acc` | Một người vừa vào cửa | |
| `LEVER_TOGGLED` | `id`, `on` | Cần gạt vừa đổi trạng thái | bấm nút/gạt cần |
| `PLATE_CHANGED` | `id`, `on` | Nút vừa bị nhấn hoặc nhả | bấm nút/gạt cần |
| `MECH_MOVING` | `id`, `moving` | Thang máy hoặc cửa chắn bắt đầu hoặc dừng chuyển động | cơ quan chạy (khi `moving` = true) |
| `PLAYER_DIED` | `acc`, `cause` (`WATER`, `LAVA`, `POISON`) | Nhân vật vừa chết | chết |
| `LEVEL_COMPLETE` | `lv`, `sec` (số thực, 2 chữ số thập phân) | Cả 2 đã qua cửa | kết thúc màn |
| `LEVEL_FAILED` | `cause` (`DEAD`), `acc` (người gây ra) | Màn thua | |
| `GAME_ABORT` | `reason` (`PEER_LEFT`, `UDP_TIMEOUT`, `SERVER_ERROR`, `RESTART`) | Trận bị hủy | |

Thứ tự trong cùng 1 tick: các sự kiện theo thứ tự sinh ra trong `World.step`. Khi chết: `PLAYER_DIED` rồi `LEVEL_FAILED` ngay trong cùng tick.

Sau `LEVEL_COMPLETE`, `LEVEL_FAILED` hoặc `GAME_ABORT`:
1. `Loop` dừng, `World` bị bỏ.
2. Phòng về `WAITING`, 2 người về `IDLE`.
3. Gửi `ROOM_INFO` cho thành viên, broadcast `ROOM_LIST` và `ONLINE_LIST`.
4. Với `LEVEL_COMPLETE`: ghi DB bằng `dbExec` (xem file đặc tả DB). Lỗi ghi DB chỉ ghi log, không ảnh hưởng người chơi.

## 5. Bảng trạng thái phía server (tóm tắt)

| Từ | Sự kiện | Sang |
|---|---|---|
| S0 | `LOGIN` thành công | S1 |
| S1 | `CREATE_ROOM` hoặc `JOIN_ROOM` thành công | S2 |
| S2 | `START_GAME` thành công | S3 |
| S3 | `LEVEL_COMPLETE`, `LEVEL_FAILED`, `GAME_ABORT` | S2 (với `RESTART`: về S2 rồi sang S3 ngay khi `GAME_START` được gửi) |
| S2, S3 | `LEAVE_ROOM` | S1 |
| S1, S2, S3 | `LOGOUT` hoặc mất kết nối | S0 (hoặc đóng) |

Trạng thái của kết nối được suy ra từ `Sess` (`acc`, `room`, và `room.status`), không lưu thêm biến riêng.

## 6. UDP

### 6.1 Tóm tắt

| Type | Hướng | Mục đích |
|---|---|---|
| `UDP_HELLO` | client đến server | Đăng ký địa chỉ UDP |
| `UDP_ACK` | server đến client | Xác nhận bắt tay |
| `INPUT` | client đến server | Trạng thái phím |
| `SNAP` | server đến client | Toàn bộ trạng thái động |
| `PING` | client đến server | Đo độ trễ |
| `PONG` | server đến client | Trả lời PING |

### 6.2 Bắt tay (`UDP_HELLO`, `UDP_ACK`)

Client gửi ngay sau khi nhận `GAME_START`, lặp mỗi `HELLO_MS` cho đến khi nhận `UDP_ACK`:

```json
{"type":"UDP_HELLO","data":{"room":"R1","acc":5,"token":"3f9a0c...32 ký tự hex"}}
```

Server kiểm tra: phòng tồn tại và đang chờ bắt tay, `acc` là thành viên, `token` khớp `Sess.token`. Hợp lệ thì lưu `Sess.udpAddr` (IP và cổng nguồn của gói), trả lời:

```json
{"type":"UDP_ACK","data":{"room":"R1","acc":5}}
```

Không hợp lệ thì bỏ qua và ghi log WARN (nêu lý do). Nhận `UDP_HELLO` lặp từ cùng một người thì vẫn trả `UDP_ACK` (chấp nhận trùng).

Khi cả 2 người đã có `udpAddr`: gửi `GAME_GO` (TCP) cho cả 2, rồi bắt đầu tick. Nếu quá `HELLO_WAIT_MS` kể từ lúc gửi `GAME_START` mà chưa đủ 2 người thì hủy trận với `GAME_ABORT` có `reason` = `UDP_TIMEOUT`.

Client bắt đầu gửi `INPUT` sau khi nhận `GAME_GO`.

### 6.3 `INPUT`

```json
{"type":"INPUT","data":{"room":"R1","acc":5,"seq":812,"l":false,"r":true,"j":false}}
```

| Trường | Ý nghĩa |
|---|---|
| `seq` | Số thứ tự tăng dần mỗi gói, bắt đầu từ 1 mỗi trận |
| `l`, `r` | Đang giữ phím trái, phải |
| `j` | Đang giữ phím nhảy |

Quy tắc client:
- Gửi mỗi `1000/IN_HZ` ms, và gửi ngay khi `l`, `r` hoặc `j` đổi.
- Khi bấm nhảy, giữ `j` = true ít nhất `JUMP_HOLD_MS` (kể cả khi người chơi nhả phím sớm) để mất vài gói không làm mất cú nhảy.
- INPUT cũng là heartbeat, nên vẫn gửi đều khi không có phím nào được giữ.

Quy tắc server khi nhận `INPUT`:
1. Phòng không có hoặc chưa `GAME_GO`: bỏ qua.
2. Địa chỉ nguồn khác `Sess.udpAddr` của `acc` đó: bỏ qua, ghi log WARN.
3. `seq` nhỏ hơn hoặc bằng `seq` đã nhận gần nhất của người đó: bỏ qua (gói cũ hoặc trùng).
4. Ngược lại: cập nhật `Sess.input` và `Sess.lastSeq`, ghi nhận thời điểm nhận (`lastUdpMs`).

Server nhảy khi `j` chuyển từ false sang true (so với tick trước) và nhân vật đang đứng trên mặt đỡ. Giữ phím nhảy không làm nhảy liên tục.

Timeout: nếu một người không có gói UDP hợp lệ nào trong `UDP_TO_MS` thì hủy trận với `GAME_ABORT`, `reason` = `UDP_TIMEOUT`.

### 6.4 `SNAP`

Server gửi cứ mỗi `TICK_HZ/SNAP_HZ` tick (tức mỗi 2 tick) cho cả 2 người, luôn là bản đầy đủ (không delta):

```json
{"type":"SNAP","data":{"tick":1230,"ack":{"5":812,"9":640},
 "players":[{"acc":5,"x":96.0,"y":640.5,"face":1,"air":false,"alive":true,"done":false},
            {"acc":9,"x":128.0,"y":640.5,"face":-1,"air":false,"alive":true,"done":false}],
 "rocks":[{"id":"r1","x":300.0,"y":672.0}],
 "elevs":[{"id":"e1","p":0.0}],
 "gates":[{"id":"g1","p":1.0}],
 "keys":[{"id":"k1","got":false}],
 "doors":[{"id":"d1","open":false,"passed":[]}],
 "levers":[{"id":"v1","on":false}],
 "plates":[{"id":"p1","on":true}]}}
```

| Trường | Ý nghĩa |
|---|---|
| `tick` | Số tick của `World` lúc chụp. Client bỏ snapshot có `tick` nhỏ hơn hoặc bằng snapshot đã nhận |
| `ack` | Map `acc` (dạng chuỗi) sang `seq` INPUT cuối cùng server đã dùng. Chỉ để debug và đo độ trễ |
| `players[].x`, `y` | Vị trí góc trên trái của hitbox, đơn vị pixel, làm tròn 1 chữ số thập phân |
| `players[].face` | -1 (trái) hoặc 1 (phải) |
| `players[].air` | Đang ở trên không |
| `players[].alive` | Còn sống |
| `players[].done` | Đã vào cửa (client không vẽ) |
| `rocks[].x`, `y` | Vị trí đá, làm tròn 1 chữ số thập phân |
| `elevs[].p`, `gates[].p` (lấy từ các `Mech` có `kind` tương ứng) | Tiến trình trên hành trình, từ 0.0 đến 1.0, làm tròn 3 chữ số thập phân. 0 là vị trí ban đầu theo file màn |
| `keys[].got` | Chìa đã bị lấy |
| `doors[].open` | Cửa đã mở (chìa đã lấy) |
| `doors[].passed` | Danh sách `acc` đã vào cửa |
| `levers[].on`, `plates[].on` | Trạng thái |

Quy tắc:
- Tất cả mảng luôn có mặt (rỗng nếu không có vật thể loại đó). Thứ tự phần tử theo thứ tự trong file màn.
- Danh sách vật thể trong SNAP trùng với `id` trong file màn. Client dùng `id` để ghép.
- Kích thước phải nhỏ hơn `MAX_UDP`. Giới hạn thiết kế màn để đảm bảo điều này (file đặc tả level).
- Client không suy ra sự kiện từ khác biệt giữa 2 snapshot. Sự kiện đến từ TCP (mục 4.4).

### 6.5 `PING`, `PONG`

Trong trận, client gửi mỗi 1 giây:

```json
{"type":"PING","data":{"room":"R1","acc":5,"t":1760000000123}}
```

Server trả lại ngay (cùng quy tắc kiểm tra địa chỉ như `INPUT`, không dùng `seq`):

```json
{"type":"PONG","data":{"t":1760000000123}}
```

Client tính `ping = now - t` và hiển thị trên `Dbg`.

### 6.6 Kết thúc

Khi nhận `LEVEL_COMPLETE`, `LEVEL_FAILED` hoặc `GAME_ABORT` (TCP), client ngừng gửi `INPUT` và đóng `DatagramSocket`. Server xóa `Sess.udpAddr`, `Sess.input`, `Sess.lastSeq`. Gói UDP đến sau đó bị bỏ qua vì phòng không còn đang chơi. Mỗi trận mở socket UDP mới nên không bị lẫn gói của trận trước.

## 7. Thứ tự và tính chắc chắn

- TCP đảm bảo thứ tự trong cùng một kết nối. UDP không đảm bảo gì, và TCP với UDP không có thứ tự tương đối với nhau.
- Sự kiện TCP sinh ra ở tick N có thể đến client trước hoặc sau SNAP của tick N. Vì vậy client không được phụ thuộc vào thứ tự đó: trạng thái lấy từ SNAP, hiệu ứng và âm thanh lấy từ sự kiện TCP.
- Mọi push lobby là bản đầy đủ nên nhận lặp hoặc nhận trễ vẫn đúng.
- `GAME_START` luôn đến trước mọi gói UDP của trận đó. `GAME_GO` đến trước SNAP đầu tiên được dùng.

## 8. Ví dụ phiên đầy đủ (rút gọn)

```
C1 > {"type":"LOGIN","rid":1,"data":{"username":"an","password":"secret1"}}
S  > {"type":"LOGIN_SUCCESS","rid":1,"data":{"acc":5,"name":"An","token":"3f9a..."}}
S  > {"type":"LEVELS","data":{...}}
S  > {"type":"ONLINE_LIST","data":{...}}
S  > {"type":"ROOM_LIST","data":{"rooms":[]}}
C1 > {"type":"CREATE_ROOM","rid":2,"data":{}}
S  > {"type":"ROOM_INFO","rid":2,"data":{"room":"R1","host":5,...}}      (đến C1)
S  > {"type":"ROOM_LIST","data":{"rooms":[{"room":"R1","host":"An","n":1,"status":"WAITING"}]}}   (đến mọi người)
C2 > {"type":"JOIN_ROOM","rid":1,"data":{"room":"R1"}}
S  > {"type":"ROOM_INFO","rid":1,"data":{...2 members...}}                (đến C2)
S  > {"type":"ROOM_INFO","data":{...2 members...}}                        (đến C1)
C1 > {"type":"START_GAME","rid":3,"data":{}}
S  > {"type":"GAME_START","rid":3,"data":{...}}                           (đến C1)
S  > {"type":"GAME_START","data":{...}}                                   (đến C2)
C1 = UDP {"type":"UDP_HELLO",...}   S = UDP {"type":"UDP_ACK",...}
C2 = UDP {"type":"UDP_HELLO",...}   S = UDP {"type":"UDP_ACK",...}
S  > {"type":"GAME_GO","data":{}}                                         (đến cả 2)
C1 = UDP INPUT ...  C2 = UDP INPUT ...      S = UDP SNAP (30 lần mỗi giây, đến cả 2)
S  > {"type":"KEY_COLLECTED","data":{"id":"k1","acc":5}}                  (đến cả 2)
S  > {"type":"DOOR_PASSED","data":{"id":"d1","acc":5}}
S  > {"type":"DOOR_PASSED","data":{"id":"d1","acc":9}}
S  > {"type":"LEVEL_COMPLETE","data":{"lv":2,"sec":41.25}}
S  > {"type":"ROOM_INFO","data":{...status WAITING...}}
```

## 9. Cách tự kiểm tra giao thức (không cần client)

Dùng `telnet`, `ncat` hoặc `nc` tới cổng TCP của server, gõ từng dòng JSON.

| Bước | Gửi | Mong đợi |
|---|---|---|
| 1 | `{"type":"PING","rid":1,"data":{}}` | `PONG` có `rid` 1 |
| 2 | `abc` | `ERR BAD_JSON` |
| 3 | `{"type":"XYZ","data":{}}` | `ERR BAD_TYPE` |
| 4 | `{"type":"CREATE_ROOM","rid":2,"data":{}}` khi chưa đăng nhập | `ERR NOT_LOGGED_IN` |
| 5 | `REGISTER` rồi `LOGIN` | `REGISTER_SUCCESS`, `LOGIN_SUCCESS` rồi `LEVELS`, `ONLINE_LIST`, `ROOM_LIST` |
| 6 | `LOGIN` lần 2 từ kết nối khác cùng tài khoản | `LOGIN_FAILED ALREADY_LOGGED_IN` |
| 7 | `START_GAME` khi chưa vào phòng | `ERR BAD_STATE` |
| 8 | Tạo phòng, mở kết nối thứ 2 vào phòng | Cả 2 nhận `ROOM_INFO` 2 thành viên |
| 9 | `START_GAME` bởi người không phải chủ phòng | `ERR NOT_HOST` |
| 10 | Đóng đột ngột 1 kết nối đang ở trong phòng | Người còn lại nhận `ROOM_INFO` mới, `ONLINE_LIST` cập nhật |
| 11 | Không gửi gì quá `TCP_TO_MS` | Server đóng kết nối |
| 12 | `SWAP` bởi chủ phòng có 2 người | Cả 2 nhận `ROOM_INFO` với `el` đã đổi chỗ |
| 13 | `SWAP` bởi người không phải chủ phòng | `ERR NOT_HOST` |
| 14 | `SWAP` khi phòng đang `PLAYING` | `ERR BAD_STATE` |
| 15 | `RESTART` bởi chủ phòng khi đang chơi | `GAME_ABORT RESTART` rồi `GAME_START` mới |

UDP có thể thử bằng `ncat -u` hoặc một công cụ gửi UDP: gửi `UDP_HELLO` hợp lệ và kiểm tra `UDP_ACK`, gửi `INPUT` seq cũ và kiểm tra bị bỏ qua (xem log server).
