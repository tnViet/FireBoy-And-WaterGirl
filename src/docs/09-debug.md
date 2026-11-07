# 09 - Hỗ trợ debug

Mục tiêu: lỗi nào cũng truy được nguyên nhân từ log và tái hiện được bằng công cụ. Các công cụ ở đây là yêu cầu bắt buộc khi code (không phải tùy chọn).

## 1. Chuẩn log (`Log`)

### 1.1 Định dạng

```
HH:mm:ss.SSS LEVEL [TAG] nội dung key=value key=value
```

Ví dụ:

```
10:21:33.482 INFO  [SRV] started tcp=5000 udp=5001 levels=4
10:21:40.101 DEBUG [TCP<] acc=5 {"type":"LOGIN","rid":2,...}
10:21:40.140 DEBUG [TCP>] acc=5 {"type":"LOGIN_SUCCESS","rid":2,...}
10:22:05.330 INFO  [ROOM] R1 start lv=2 match=1 fire=5 water=9
10:22:05.480 INFO  [UDP] R1 ack acc=5 from=127.0.0.1:53211
10:22:12.900 WARN  [UDP] R1 drop INPUT acc=9 reason=old_seq seq=40 last=44
```

Mức: `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`. Chọn bằng `-Dfw.log=DEBUG` (mặc định `INFO`).

Quy tắc:
- Luôn ghi `acc=` và `room=` (hoặc `R1` đầu nội dung) khi có liên quan, để lọc bằng `grep`.
- Log lỗi luôn kèm exception đầy đủ (stack trace).
- Chuỗi JSON log tối đa 300 ký tự, cắt thêm `...`. Không ghi mật khẩu: với `REGISTER` và `LOGIN` thay giá trị `password` bằng `***` trước khi log.
- Ghi ra console và file `logs/<role>.log` (`server.log`, `client.log`; đặt tên khác bằng `-Dfw.logfile=client2.log` để chạy nhiều client trên một máy).

### 1.2 Thẻ (tag) và mức

| Tag | Dùng cho | Mức |
|---|---|---|
| `SRV` | Khởi động, tắt, cấu hình | INFO |
| `TCP<`, `TCP>` | Mỗi dòng TCP nhận và gửi (cả server và client) | DEBUG |
| `TCP` | Kết nối mở, đóng, lý do đóng, timeout | INFO |
| `UDP` | Bắt tay (`hello`, `ack`), bỏ gói và lý do, timeout | INFO và WARN |
| `UDP<`, `UDP>` | Từng gói `INPUT` và `SNAP` | TRACE (chỉ ghi 1 trong 30 gói) |
| `ROOM` | Tạo, vào, rời, swap, start, end, abort (kèm `match=`) | INFO |
| `LOOP` | Thống kê tick mỗi 5 giây, tick chậm | INFO, WARN |
| `PHYS` | Các `Ev` sinh ra từ `World.step`, và cảnh báo chồng lấn, ra ngoài biên | DEBUG, WARN, ERROR |
| `DB` | Mỗi thao tác DAO kèm thời gian, lỗi SQL kèm `SQLState`, `errorCode` | DEBUG, ERROR |
| `AUTH` | Đăng ký, đăng nhập thành công hoặc thất bại (kèm `username` và mã lỗi) | INFO |
| `NET` (client) | Mất kết nối, đóng socket, nhận `GAME_ABORT` | INFO |
| `UI` (client) | Chuyển màn hình (`from -> to` kèm lý do) | DEBUG |
| `ASSET` (client) | Thiếu file hình hoặc âm thanh | WARN (một lần mỗi file) |

### 1.3 Lý do bỏ gói UDP (`reason=`)

Mọi chỗ bỏ gói UDP phải ghi lý do bằng một trong các giá trị cố định: `bad_json`, `no_room`, `no_sess`, `not_member`, `bad_token`, `bad_addr`, `not_running`, `old_seq`. Nhờ đó thấy ngay vì sao gói không được dùng.

## 2. Dump gói tin

`-Dfw.dump=true` ghi mọi gói vào `logs/pkt-<role>.log` (không cắt 300 ký tự, không lọc 1/30), mỗi dòng `ms_từ_khi_chạy hướng kênh nội_dung` (ví dụ `12033 < UDP {"type":"SNAP",...}`). Dùng để so sánh với file log của đầu bên kia.

## 3. Mô phỏng mạng xấu (`NetSim`)

Áp dụng cho UDP (cả gửi và nhận phía client, gửi phía server):

| Thuộc tính | Ý nghĩa |
|---|---|
| `-Dfw.loss=0.1` | Xác suất mất gói (0 đến 1) |
| `-Dfw.lag=100` | Trễ cộng thêm (ms) |
| `-Dfw.jit=30` | Dao động trễ ngẫu nhiên ± (ms) |

`NetSim.send(Runnable r)`: nếu `Math.random() < loss` thì bỏ, ngược lại lên lịch `r` sau `lag ± jit` ms bằng một `ScheduledExecutorService` riêng. Khi tắt (giá trị mặc định 0) thì gọi `r.run()` ngay. Dùng `Random` có hạt giống cố định qua `-Dfw.seed=N` để tái hiện được.

Kiểm tra: `loss=0.2 lag=150 jit=50` thì game vẫn chơi được (hơi giật), không mất đồng bộ trạng thái rời rạc (chìa, cửa, cần gạt trong SNAP đúng).

## 4. Ghi và phát lại vật lý (`Rec`, `Sim`)

- Server ghi input từng tick khi bật `-Dfw.rec=true`: `logs/rec-<room>-<matchId>.txt` (định dạng ở `04-core-game.md` mục 8).
- Tái hiện lỗi vật lý: `java -cp common.jar fw.common.game.Sim levels/lv3.json logs/rec-R1-2.txt` in trạng thái từng tick. Có thể thêm `--from 600 --to 700` để chỉ in đoạn quan tâm, `--watch r1,p1` để chỉ in vài đối tượng.
- Kịch bản tay: `Sim levels/lv1.json --script "r*40,j,r*60"`.
- Quy trình khi báo lỗi vật lý: mở file `rec` của trận, chạy `Sim` và đối chiếu tick lỗi, viết thành test JUnit rồi sửa.

## 5. Chạy nhiều tiến trình trên một máy

### 5.1 Lệnh Maven

```
mvn -q -DskipTests install
mvn -q -pl server exec:java -Dexec.mainClass=fw.server.ServerMain
mvn -q -pl client exec:java -Dexec.mainClass=fw.client.ClientMain -Dfw.logfile=client1.log
mvn -q -pl client exec:java -Dexec.mainClass=fw.client.ClientMain -Dfw.logfile=client2.log
```

(Cần cấu hình plugin `exec-maven-plugin` trong `pom.xml` của `server` và `client`.)

### 5.2 VS Code: `.vscode/launch.json`

```json
{
  "version": "0.2.0",
  "configurations": [
    { "type": "java", "name": "Server", "request": "launch",
      "mainClass": "fw.server.ServerMain", "projectName": "server",
      "vmArgs": "-Dfw.log=DEBUG -Dfw.rec=true" },
    { "type": "java", "name": "Client 1", "request": "launch",
      "mainClass": "fw.client.ClientMain", "projectName": "client",
      "vmArgs": "-Dfw.log=DEBUG -Dfw.logfile=client1.log" },
    { "type": "java", "name": "Client 2", "request": "launch",
      "mainClass": "fw.client.ClientMain", "projectName": "client",
      "vmArgs": "-Dfw.log=DEBUG -Dfw.logfile=client2.log" }
  ],
  "compounds": [
    { "name": "Server + 2 Clients",
      "configurations": ["Server", "Client 1", "Client 2"],
      "stopAll": true }
  ]
}
```

Hai client cùng máy dùng cổng UDP ngẫu nhiên khác nhau nên không đụng nhau.

## 6. `Bot` (client không GUI)

Chạy: `fw.client.Bot --user bot1 --pass secret1 [--room R1 | --create] [--script "r*60,j"] [--host 127.0.0.1] [--lv 1]`.

Bot thực hiện: đăng ký nếu chưa có tài khoản, đăng nhập, tạo phòng (`--create`) hoặc vào phòng (`--room`), chủ phòng chọn màn và `START_GAME` khi đủ người, bắt tay UDP, chạy kịch bản input (cú pháp như `Sim`), in mọi `Msg` TCP và tóm tắt SNAP (mỗi 30 snapshot một dòng) ra console. Kết thúc khi nhận `LEVEL_COMPLETE`, `LEVEL_FAILED`, `GAME_ABORT` và in `RESULT=...`. Mã thoát 0 nếu nhận `LEVEL_COMPLETE`.

Dùng để kiểm tra server mà không cần chơi tay: 2 bot trên một máy, một bot `--create`, một bot `--room R1`.

## 7. Thứ tự kiểm tra khi có lỗi mạng

1. Server có chạy không? `SRV started` trong log. Cổng bị chiếm thì lỗi `Address already in use`.
2. TCP: `telnet 127.0.0.1 5000` rồi gửi `PING` (xem `02-protocol.md` mục 9).
3. Log server có dòng `TCP<` của lệnh vừa gửi không? Không có thì tin chưa tới: kiểm tra thiếu ký tự xuống dòng, thiếu `flush`, sai cổng.
4. Có `TCP>` phản hồi không? Có `ERR` thì đọc `code` và `msg`.
5. UDP: có `UDP ack` trong log server không? Không thì xem dòng `drop` với `reason=`.
6. Client có nhận `SNAP` không? Bật `F3`: `snap/s` bằng 0 nghĩa là không nhận. Kiểm tra tường lửa, `127.0.0.1` so với `localhost`, client có gọi `connect` đúng cổng `udpPort` không.
7. Nếu có `SNAP` nhưng hình sai: so `tick` và `ack` trên `Dbg`, dump gói để so sánh dữ liệu thô.
8. Nếu lỗi nằm ở hành vi vật lý: dùng `Rec` và `Sim` (mục 4).

## 8. Bảng lỗi thường gặp

| Triệu chứng | Nguyên nhân thường gặp | Cách kiểm tra và sửa |
|---|---|---|
| Server không nhận lệnh, client chờ mãi | Thiếu `\n` hoặc thiếu `flush()` khi gửi | Dump gói; luôn gửi qua `sendLine` thêm `\n` và `flush` trong luồng ghi |
| `ERR BAD_JSON` ngay khi gửi | Gửi JSON có xuống dòng bên trong (pretty print), hoặc gửi nhiều gói dính nhau không có `\n` | Dùng Gson mặc định (không `setPrettyPrinting`) |
| Số nguyên thành `1.0` | Parse JSON vào `Map<String,Object>` (Gson cho `Double`) | Dùng `JsonObject` và `getAsInt()`, không dùng `Map` |
| Không bao giờ có `UDP ack` | Sai địa chỉ (IPv6 và IPv4), sai cổng, tường lửa chặn UDP, `token` sai | Log `drop reason=bad_token`/`no_sess`. Dùng `127.0.0.1`, mở cổng UDP, kiểm tra client đã nhận `token` từ `LOGIN_SUCCESS` |
| Có `ack` nhưng không có SNAP | `Loop` chưa chạy (chưa đủ 2 người bắt tay), hoặc `Loop` đã chết do ngoại lệ | Tìm `GAME_GO` trong log. Tìm `LOOP` ERROR. `Loop.run` phải bắt `Throwable` |
| Game đang chơi đột nhiên dừng, phòng kẹt `PLAYING` | `scheduleAtFixedRate` bị hủy vì ngoại lệ không được bắt | Bắt `Throwable` trong `Loop.run`, gọi `endMatch(ABORT, SERVER_ERROR)` |
| Phòng kẹt `PLAYING` sau khi một người mất mạng | `ClientHandler.close()` không gọi `RoomSvc.leave` | Kiểm tra `finally` của vòng đọc; log `TCP closed acc=` |
| Đăng nhập lại báo `ALREADY_LOGGED_IN` sau khi client sập | `Online.remove` không chạy khi đóng kết nối | Dọn trong `close()`; thêm `TCP_TO_MS` để phát hiện kết nối chết |
| Nhân vật xuyên tường hoặc sàn | `dt` không cố định, bước di chuyển lớn hơn bề dày vật, lỗi chỉ số ô khi đổi pixel sang ô | Dùng `Sim` với file `rec`. Kiểm tra `PHYS penetrate` trong log. Xem 04 mục 5.1 |
| Nhân vật không nhảy | `j` không tới server (thiếu `JUMP_HOLD_MS`), nhảy tính theo phím giữ thay vì cạnh lên, `onGround` sai | Bật `Dbg`, quan sát `onGround`. Chạy test T03 và T04 |
| Nhân vật giật hoặc nhảy cóc | Nội suy sai: dùng thời điểm nhận sai, bỏ snapshot mới, so `tick` ngược | Kiểm tra `Interp.at` dùng `nanoTime`; bỏ snapshot `tick` cũ; `snap/s` phải gần 30 |
| Nhân vật phía đối thủ trễ rất nhiều | `INTERP_MS` quá lớn, hoặc `IN_HZ` và `SNAP_HZ` sai, hoặc `NetSim` đang bật | Kiểm tra thuộc tính `fw.lag` và `Const` |
| Người trên đầu không đi theo người dưới | `ridersOf` sai dung sai (`RIDE_EPS`), xử lý theo thứ tự sai, rider bị loại vì `vy < 0` | Chạy T11 đến T15. Log rider danh sách ở mức TRACE |
| Thang máy kẹt không chạy | `target` sai `id`, thang bị chặn (log `PHYS mech blocked id=`), logic `active` đọc nhầm trạng thái | Kiểm tra `LevelLoader`; chạy T16 và T18 |
| Cửa chắn nghiền hoặc đè lên đá | Bước thử trước (`ok`) bị thiếu | Chạy T17 |
| Cả 2 vào cửa nhưng không thắng | Luật người giữ chìa chưa thỏa, `done` không đặt, kiểm tra thắng chạy trước cửa | Xem `PHYS` ghi `DOOR_PASSED`; chạy T22 và T23 |
| `LEVEL_COMPLETE` nhưng DB không có dòng | Lỗi trong tác vụ `dbExec` bị nuốt | Tìm `DB` ERROR; `dbExec` phải log mọi ngoại lệ |
| Tiếng Việt bị lỗi dấu | Thiếu UTF-8 ở đâu đó | Dùng `StandardCharsets.UTF_8` cho mọi `Reader/Writer`; `-Dfile.encoding=UTF-8`; URL JDBC có `characterEncoding=utf8` |
| Lỗi kết nối MySQL | Thiếu `allowPublicKeyRetrieval=true`, sai `serverTimezone`, sai user hoặc quyền | Đọc `SQLState`/`errorCode` trong log `DB` |
| Giao diện treo | Gọi mạng hoặc `Thread.sleep` trên EDT | Thread dump (`jstack`) thấy EDT bị chặn ở `socket` |
| Phím kẹt khi đổi cửa sổ | Không xóa phím khi mất tiêu điểm | Xóa cờ trong `focusLost` |
| Không có tiếng | File không phải `.wav` PCM 16-bit, thiếu file, thiết bị âm thanh bận | Xem log `ASSET`; thử file mẫu |
| Server nặng khi có nhiều phòng | `Loop` chậm, DB chạy trong `Loop`, log TRACE bật | Xem log `LOOP slow tick`; tắt TRACE; kiểm tra không có DB trong `Loop` |
| Deadlock (mọi thứ đứng im) | Gửi chặn khi giữ khóa phòng, lấy khóa sai thứ tự | `jstack <pid>` tìm `deadlock`. Xem quy tắc ở `06-server.md` mục 13 |

## 9. Cách lấy thread dump

- `jps` để thấy mã tiến trình, `jstack <pid> > dump.txt`. Tìm "Found one Java-level deadlock".
- VS Code: khi đang debug bấm Pause, xem Call Stack của từng luồng.

## 10. Kiểm tra bất biến

`-Dfw.assert=true` bật `Rooms.check()` (xem `03-states-flows.md` mục 5). Khi vi phạm bất biến thì log ERROR và nội dung phòng, không làm sập server.
