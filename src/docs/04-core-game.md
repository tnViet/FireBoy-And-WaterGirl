# 04 - Lõi game (module common)

Lõi game là `World` và các lớp liên quan trong `fw.common.game`. Quy tắc bắt buộc:
- Mô phỏng thuần: không mạng, không Swing, không JDBC, không đọc đồng hồ thật, không `Random`. Cùng `Level` và cùng chuỗi input thì kết quả giống hệt nhau từng bit.
- Dùng `float` cho vị trí và vận tốc. `dt` luôn là hằng `1f/60f`.
- Server gọi `World.step` mỗi tick. Client không bao giờ gọi `World.step`.

## 1. Hệ tọa độ và kích thước

- Đơn vị: pixel. Gốc ở góc trên trái, `x` sang phải, `y` xuống dưới.
- `TILE` = 32 pixel. File màn dùng đơn vị tile, `LevelLoader` nhân 32 khi nạp (xem file level).
- Hình chữ nhật (`Rect`): `x, y` là góc trên trái, `w, h` là kích thước.
- Hai `Rect` chồng nhau khi `a.x < b.x+b.w && a.x+a.w > b.x && a.y < b.y+b.h && a.y+a.h > b.y` (so sánh chặt: chạm cạnh không tính là chồng).

## 2. Hằng số (`Phys`)

| Tên | Giá trị | Ý nghĩa |
|---|---|---|
| `DT` | 1f/60f | Bước thời gian |
| `PW`, `PH` | 24, 30 | Kích thước hitbox nhân vật |
| `ROCK` | 32 | Kích thước đá (hình vuông) |
| `KEY_S` | 24 | Kích thước chìa khóa (vuông, đặt giữa ô) |
| `LEVER_W`, `LEVER_H` | 16, 24 | Kích thước cần gạt (giữa ô, sát đáy ô) |
| `GRAV` | 1800 | Gia tốc rơi (px/s²) |
| `MAXFALL` | 900 | Tốc độ rơi tối đa (px/s) |
| `MOVE_V` | 200 | Tốc độ đi ngang (px/s), cả trên không |
| `JUMP_V` | 660 | Vận tốc nhảy ban đầu (px/s, hướng lên) |
| `PUSH_K` | 0.5 | Hệ số chậm khi đẩy đá |
| `PUSH_V` | 100 | Vận tốc ngang đá nhận khi bị đẩy (px/s) |
| `FRIC` | 800 | Ma sát sàn làm đá chậm dần (px/s²) |
| `PROBE` | 1.0 | Khoảng dò mặt đỡ phía dưới (px) |
| `RIDE_EPS` | 0.5 | Sai số khi xét "đứng trên" (px) |
| `HAZ_INSET` | 4 | Thu hẹp hitbox theo chiều ngang khi xét vùng nguy hiểm (mỗi bên, px) |
| `LEVER_CD` | 30 | Thời gian chờ của cần gạt (tick, = 0,5 giây) |
| `EPS` | 0.001 | Sai số so sánh số thực |

Hệ quả (dùng để thiết kế màn, đã tính theo tích phân rời rạc của thuật toán dưới):
- Độ cao nhảy tối đa khoảng 115,5 px (3,6 tile). Quy tắc thiết kế: bục cao 3 tile (96 px) so với chỗ đứng thì nhảy một mình lên được. Bục cao 4 tile (128 px) cần đứng trên đầu nhau hoặc trên đá. Bục cao 5 tile trở lên không lên được.
- Thời gian bay khoảng 0,73 giây, đi ngang khoảng 147 px (4,5 tile). Quy tắc thiết kế: khe hở ngang không quá 3 tile.
- Vùng nguy hiểm (`Zone`) thấp 0,5 tile nằm sát mặt sàn. Quy tắc thiết kế: vùng rộng tối đa 2 tile nếu người chơi phải nhảy qua.

## 3. Mô hình dữ liệu

```
World
  Phase phase            RUN | WON | LOST
  int tick               số lần step đã chạy
  Player[] pl            pl[0] là FIRE, pl[1] là WATER
  List<Rock> rocks; List<Key> keys; List<Door> doors
  List<Lever> levers; List<Plate> plates; List<Mech> mechs
  final Level lv; final int lvId
```

| Lớp | Trường |
|---|---|
| `Player` | `acc`, `el`, `x`, `y`, `vx`, `vy`, `face` (-1 hoặc 1), `onGround`, `alive`, `done`, `jHeld` |
| `Rock` | `id`, `x`, `y`, `vx`, `vy`, `pushed` |
| `Key` | `id`, `x`, `y` (cố định), `got`, `by` (acc người lấy, 0 nếu chưa) |
| `Door` | `id`, `x`, `y`, `w`, `h`, `open`, `passed` (danh sách acc đã vào) |
| `Lever` | `id`, `x`, `y`, `on`, `cd` (đếm lùi tick), `touch` (đang chạm ở tick trước), `target` |
| `Plate` | `id`, `x`, `y`, `w`, `h`, `on`, `target` |
| `Mech` | `id`, `kind`, `x0`, `y0` (vị trí khi `p` = 0), `w`, `h`, `axis`, `dist` (px, có dấu), `speed` (px/s), `p` (0..1), `moving` |
| `Zone` | `id`, `kind`, `x`, `y`, `w`, `h` (tĩnh, nằm trong `Level`) |

Vị trí hiện tại của `Mech`: `x = x0 + (axis == 'x' ? p*dist : 0)`, `y = y0 + (axis == 'y' ? p*dist : 0)`.

`Level` (bất biến, tạo bởi `LevelLoader`): `name`, `cols`, `rows`, `boolean[][] solid` (chỉ số `[row][col]`), danh sách định nghĩa (spawn, key, door, rock, zone, lever, plate, mech) đã đổi sang pixel.

API:

```java
World(Level lv, int lvId, int accFire, int accWater)
List<Ev> step(Input[] in)     // in[0] cho FIRE, in[1] cho WATER, không có phần tử null
double sec()                  // Math.round(tick * 100.0 / 60) / 100.0
```

Khi `phase != RUN`, `step` không làm gì và trả danh sách rỗng.

`Ev` (record): `type` (hằng trong `MsgType`), `id`, `acc`, `info`, `on`, `sec`. Bảng chuyển `Ev` thành `Msg` (hàm `Ev.toMsg(int lvId)`):

| `Ev.type` | `data` |
|---|---|
| `KEY_COLLECTED` | `id`, `acc` |
| `DOOR_PASSED` | `id`, `acc` |
| `LEVER_TOGGLED` | `id`, `on` |
| `PLATE_CHANGED` | `id`, `on` |
| `MECH_MOVING` | `id`, `moving` (lấy từ `on`) |
| `PLAYER_DIED` | `acc`, `cause` (lấy từ `info`) |
| `LEVEL_COMPLETE` | `lv` (từ `lvId`), `sec` |
| `LEVEL_FAILED` | `cause` (= `DEAD`), `acc` |

## 4. Thứ tự xử lý trong một tick (`World.step`)

Thứ tự này cố định, không được đổi.

1. `tick++`.
2. Cơ quan (`Mech`): tính kích hoạt từ trạng thái `Lever.on` và `Plate.on` của tick trước, rồi di chuyển (mục 6.5).
3. Nhân vật: lần lượt `pl[0]` rồi `pl[1]` (mục 6.4).
4. Đá: lần lượt theo thứ tự trong màn (mục 6.6).
5. Nút (`Plate`).
6. Cần gạt (`Lever`).
7. Chìa khóa, rồi cập nhật `Door.open`.
8. Vùng nguy hiểm.
9. Cửa.
10. Kiểm tra thắng.

Mỗi bước sinh `Ev` theo thứ tự xảy ra.

## 5. Mô hình va chạm

### 5.1 Vật rắn (solid)

Chặn di chuyển: ô tường (`Level.solid`), mọi `Mech` (ở vị trí hiện tại), `Rock`, `Player` chưa `done`. Không chặn: `Zone`, `Key`, `Door`, `Lever`, `Plate`.

Mọi vật rắn dày ít nhất 16 px (đảm bảo không xuyên tường: tốc độ tối đa 15 px/tick nhỏ hơn bề dày và chiều cao vật).

### 5.2 Hàm quét theo trục

```
float sweepX(Rect r, float dx, Set<Body> ex)
  // Trả về độ dịch thực tế theo X, cùng dấu với dx, độ lớn <= |dx|.
  // Xét mọi vật rắn s không thuộc ex mà khoảng y của s chồng chặt với khoảng y của r.
  // dx > 0: nếu s.x >= r.x + r.w - EPS thì giới hạn = s.x - (r.x + r.w). Lấy nhỏ nhất, không nhỏ hơn 0.
  // dx < 0: nếu s.x + s.w <= r.x + EPS thì giới hạn = r.x - (s.x + s.w). Lấy nhỏ nhất, không nhỏ hơn 0.
float sweepY(Rect r, float dy, Set<Body> ex)    // tương tự theo trục Y
```

Vật rắn đã chồng sẵn lên `r` (do sai số) bị bỏ qua, nhưng `World` ghi log WARN `PHYS penetrate` kèm id.

### 5.3 Vật mang theo (rider)

`ridersOf(b)`: danh sách vật `e` (đá, nhân vật chưa `done`) thỏa tất cả:
- `|e.y + e.h - b.y| <= RIDE_EPS` (đáy của `e` chạm đỉnh của `b`),
- khoảng x của `e` chồng chặt với khoảng x của `b`,
- `e.vy >= 0` (không đang bay lên bằng sức nhảy của chính nó),
- `e != b`.

Lấy bao đóng: vật đứng trên vật đứng trên `b` cũng là rider (độ sâu tối đa 3). Sắp xếp từ dưới lên. Nếu `b` là `Mech` hoặc nhân vật/đá thì đều dùng cùng hàm này.

### 5.4 `moveGroup`: di chuyển một vật cùng những vật đứng trên nó

```
Result moveGroup(Body b, float dx, float dy, boolean walk)
  riders = ridersOf(b)            // tính TRƯỚC khi di chuyển
  grp = {b} + riders
  // --- ngang ---
  ax = walk ? walkX(b, dx, grp) : sweepX(b, dx, grp)
  b.x += ax
  for r in riders: r.x += sweepX(r, ax, grp)       // rider bị tường chặn thì dừng, không sao
  // --- dọc ---
  if dy < 0:  ay = max over m in grp of sweepY(m, dy, grp)   // lấy độ dịch NHỎ NHẤT về độ lớn (nhóm bị vật chặn phía trên thì cả nhóm dừng)
  else:       ay = sweepY(b, dy, grp)                         // rider đi theo b, không bị chặn
  for m in grp: m.y += ay
  return (ax, ay)
```

Lưu ý về `ay` khi `dy < 0`: tính `sweepY` cho từng thành viên rồi chọn giá trị có độ lớn nhỏ nhất.

### 5.5 `walkX`: đi ngang và đẩy đá (chỉ cho nhân vật)

```
float walkX(Player p, float dx, Set<Body> grp)
  if dx == 0: return 0
  s = sign(dx)
  a  = sweepX(p, dx, grp + mọi Rock)          // bị chặn bởi vật rắn trừ đá
  // khoảng tới đá đầu tiên trên đường đi (trong phạm vi |a|)
  rock, gap = đá gần nhất phía trước mà khoảng y chồng với p; gap = khoảng cách mép tới mép
  if rock == null or gap >= |a|:  return a
  remaining = |a| - gap
  want  = remaining * PUSH_K
  moved = |moveGroup(rock, s * want, 0, false).ax|      // đá di chuyển (kèm vật đứng trên nó)
  if moved > 0: rock.vx = s * PUSH_V; rock.pushed = true
  return s * (gap + moved)                               // nhân vật đi đúng bằng quãng đá đã dịch
```

Quy tắc: đá bị chặn bởi đá khác thì `moved` = 0, nhân vật đứng lại (không đẩy dây chuyền). Đá bị chặn bởi tường, cơ quan hoặc nhân vật còn lại cũng đứng lại.

## 6. Luật từng thành phần

### 6.1 Nhân vật: input

```
dir = (in.r ? 1 : 0) - (in.l ? 1 : 0)
p.vx = dir * MOVE_V
if dir != 0: p.face = dir
```

### 6.2 Nhân vật: mặt đỡ và nhảy

```
p.onGround = sweepY(p, +PROBE, {p}) < PROBE - EPS       // có vật rắn ngay dưới chân (kể cả đầu người kia, đá, thang máy)
if in.j && !p.jHeld && p.onGround: p.vy = -JUMP_V         // nhảy khi phím chuyển từ nhả sang giữ
p.jHeld = in.j
```

### 6.3 Nhân vật: trọng lực

```
p.vy = min(p.vy + GRAV*DT, MAXFALL)         // luôn áp dụng, kể cả đang đứng (va chạm sẽ đặt lại vy = 0)
```

### 6.4 Nhân vật: di chuyển

Bỏ qua nhân vật `!alive` hoặc `done`.

```
res = moveGroup(p, p.vx*DT, p.vy*DT, true)
dyWant = p.vy*DT
if dyWant > 0 and res.ay < dyWant - EPS: p.vy = 0        // chạm đất
if dyWant < 0 and |res.ay| < |dyWant| - EPS: p.vy = 0    // đụng đầu
```

Hệ quả cần có:
- Đỉnh đầu nhân vật là mặt phẳng: người kia đáp xuống đầu thì đứng được (vật rắn).
- Người dưới đi thì người trên đi cùng (rider). Người dưới đi ra khỏi vùng x chồng thì người trên hết là rider, `onGround` = false, rơi xuống.
- Người dưới nhảy thì mang người trên lên theo, nếu người trên bị trần chặn thì cả nhóm dừng.
- Thang máy chạy thì người dưới và người trên đi cùng (bao đóng rider).

### 6.5 Cơ quan (`Mech`)

```
active  = tồn tại Lever.on hoặc Plate.on có target == m.id      // dùng trạng thái cuối tick trước
target  = active ? 1 : 0
if m.p == target: m.moving = false; continue
step = m.speed * DT / |m.dist|
np   = m.p tiến về target thêm tối đa step (không vượt target)
d    = (np - m.p) * m.dist                         // độ dịch theo trục axis, đơn vị px
riders = ridersOf(m); grp = {m} + riders
// thử trước, không di chuyển nếu bị chặn:
ok = sweep của m theo (axis, d) với ex = grp trả về đủ |d|   (không đụng ô tường, cơ quan khác, nhân vật/đá không phải rider)
     và mọi rider cũng sweep được đủ d theo axis với ex = grp
if !ok: m.moving = false; continue
dịch m và mọi rider theo d; m.p = np; m.moving = true
```

Phát `Ev MECH_MOVING` mỗi khi `moving` đổi giá trị so với tick trước. `p = 0` là vị trí ban đầu theo file màn (thang máy ở dưới, cửa chắn đang đóng), `p = 1` là vị trí cuối hành trình. Cơ quan không chặn tới mức nghiền người: gặp vật cản thì đứng chờ.

### 6.6 Đá

```
if !r.pushed:                                  // ma sát: đá trượt chậm dần sau khi hết bị đẩy
    r.vx -= sign(r.vx) * min(|r.vx|, FRIC*DT)
    dxs = r.vx * DT
else:
    dxs = 0; r.pushed = false                   // tick này đã bị đẩy, không trượt thêm
r.vy = min(r.vy + GRAV*DT, MAXFALL)
res = moveGroup(r, dxs, r.vy*DT, false)
if dxs != 0 and |res.ax| < |dxs| - EPS: r.vx = 0
if r.vy*DT > 0 and res.ay < r.vy*DT - EPS: r.vy = 0
```

Đá chịu trọng lực (rơi khi mất chỗ đỡ), bị chặn bởi mọi vật rắn, mang theo vật đứng trên nó. Đá không bị vùng nguy hiểm tác động. Đá nhấn được `Plate`.

### 6.7 Nút (`Plate`)

`on = có nhân vật (alive, chưa done) hoặc đá chồng chặt với Rect của nút`. Phát `Ev PLATE_CHANGED` khi `on` đổi.

### 6.8 Cần gạt (`Lever`)

```
if cd > 0: cd--
touching = có nhân vật (alive, chưa done) chồng chặt với Rect của cần gạt
if touching && !touch && cd == 0: on = !on; cd = LEVER_CD; phát Ev LEVER_TOGGLED
touch = touching
```

### 6.9 Chìa khóa và cửa

```
// chìa khóa
for k in keys where !k.got:
    for p in pl (alive, chưa done): nếu hitbox p chồng chặt Rect của k: k.got = true; k.by = p.acc; phát KEY_COLLECTED; break
for d in doors: d.open = tất cả keys có got
```

Cửa (bước 9):

```
for d in doors where d.open:
    for p in pl (alive, chưa done):
        center = (p.x + PW/2, p.y + PH/2)
        if center nằm trong Rect của d:
            holder = keys[0].by
            if p.acc == holder  hoặc  holder đã nằm trong d.passed:
                p.done = true; d.passed.add(p.acc); phát DOOR_PASSED
```

Người `done` bị loại khỏi vật rắn, không bị vùng nguy hiểm, không mang ai, `SNAP` có `done` = true.

### 6.10 Vùng nguy hiểm (bước 8)

```
for p in pl (alive, chưa done):
    hb = Rect(p.x + HAZ_INSET, p.y, PW - 2*HAZ_INSET, PH)
    for z in zones: nếu hb chồng chặt Rect của z và kills(z.kind, p.el):
        p.alive = false; phát PLAYER_DIED {acc, cause = z.kind}
        nếu phase == RUN: phase = LOST; phát LEVEL_FAILED {cause = "DEAD", acc}
        (chỉ sinh LEVEL_FAILED một lần mỗi trận; vẫn phát PLAYER_DIED cho mọi người chết trong tick)
kills(kind, el): POISON giết cả 2; WATER giết FIRE; LAVA giết WATER
```

### 6.11 Thắng (bước 10)

Nếu `phase == RUN` và cả 2 nhân vật `done`: `phase = WON`, phát `LEVEL_COMPLETE {lv = lvId, sec = sec()}`.

### 6.12 Ranh giới

Màn có viền tường kín (bộ nạp màn kiểm tra), nên nhân vật và đá không ra khỏi bản đồ. Phòng hờ: sau mỗi tick, nếu vị trí nằm ngoài bản đồ thì `World` ghi log ERROR `PHYS out of bounds` và kéo đối tượng về trong biên.

## 7. Snapshot (`Snap`)

- `Snap.write(World w, int[] ack)` trả `String` JSON đúng định dạng ở `02-protocol.md` mục 6.4. Làm tròn: `x`, `y` 1 chữ số thập phân; `p` 3 chữ số.
- `elevs` gồm các `Mech` có `kind` = `ELEV`, `gates` gồm `GATE`, thứ tự theo file màn.
- Kiểm tra độ dài `<= MAX_UDP`, vượt thì ghi log ERROR và trả `null`.
- `Snap.read(String)` phía client trả `SnapData` (các mảng đã parse). Hai hàm phải đối xứng, có test.

## 8. `Rec` và `Sim` (công cụ tái hiện lỗi)

- `Rec`: ghi mỗi tick một dòng `tick,l0,r0,j0,l1,r1,j1` (0 hoặc 1), dòng đầu là `level=<tên>` và `lvId=<số>`. Server ghi khi bật `-Dfw.rec=true` vào `logs/rec-<room>-<matchId>.txt`.
- `Sim`: `java -cp ... fw.common.game.Sim <level.json> [rec.txt]`. Chạy `World` offline, in mỗi tick: `tick`, vị trí 2 nhân vật, `onGround`, `vy`, các sự kiện. Không có file `rec` thì đọc kịch bản từ tham số `--script "r*60,j,r*30"` (chuỗi lệnh `l`, `r`, `j`, `-` nghỉ, kèm `*n` lặp n tick).
- `World` là xác định, nên bất kỳ lỗi vật lý nào xảy ra trên server đều tái hiện được bằng file `Rec` của trận đó.

## 9. Test bắt buộc (JUnit, không cần mạng)

Dùng `Level` dựng bằng mã (bản đồ nhỏ) cho từng test. Giá trị mong đợi là tiêu chí nghiệm thu.

| Mã | Tình huống | Mong đợi |
|---|---|---|
| T01 | Đặt nhân vật trên sàn, chạy 60 tick không input | `y` không đổi quá 0,01, `onGround` = true |
| T02 | Giữ `r` 60 tick trên sàn trống | `x` tăng 200 ± 1 |
| T03 | Nhấn `j` 1 tick rồi nhả | Độ cao đỉnh nhảy trong [114, 117] px |
| T04 | Giữ `j` liên tục | Chỉ nhảy 1 lần cho đến khi nhả rồi nhấn lại |
| T05 | Đi vào tường 60 tick | Dừng sát tường, không xuyên |
| T06 | Đẩy đá 60 tick | Đá dịch khoảng 100 ± 5 px, nhân vật dịch bằng đá, không hở khe |
| T07 | Hết đẩy đá | Đá trượt thêm 5 đến 7 px rồi dừng |
| T08 | Đẩy đá vào tường | Đá và nhân vật dừng, không xuyên |
| T09 | Đẩy đá vào đá | Không đẩy dây chuyền, nhân vật dừng |
| T10 | Đá đặt ở mép sàn bị đẩy ra ngoài | Đá rơi, dừng trên sàn thấp hơn |
| T11 | B nhảy lên đầu A | Đáy B bằng đỉnh A, B đứng yên được |
| T12 | A đi trái 30 tick, B đứng yên trên đầu A, không input | B đi cùng A khoảng 100 px |
| T13 | A đi trái cho đến khi khoảng x không còn chồng với B | B rơi xuống sàn |
| T14 | B đi trái/phải trên đầu A | B tự di chuyển được và vẫn đứng trên A khi còn chồng x |
| T15 | Nút kích hoạt thang máy, A và B chồng lên nhau trên thang máy | Thang máy lên, cả A và B lên cùng nhau, khoảng cách giữa họ không đổi |
| T16 | Thang máy đi lên gặp trần khi có rider | Dừng, `moving` = false, không ai bị kẹt trong tường |
| T17 | Cửa chắn đang đóng xuống có đá ngay dưới | Dừng chờ, không nghiền đá |
| T18 | Đứng lên nút rồi bước ra | Cơ quan mở rồi đóng lại theo `speed` |
| T19 | Cần gạt: chạm, ra, vào lại trong 10 tick | Chỉ bật 1 lần (thời gian chờ). Sau 30 tick chạm lại thì tắt |
| T20 | Bảng nguy hiểm: FIRE/WATER/LAVA/POISON | FIRE chết ở WATER và POISON, sống ở LAVA. WATER chết ở LAVA và POISON, sống ở WATER |
| T21 | Chết trong tick | `PLAYER_DIED` rồi `LEVEL_FAILED`, `phase` = LOST, `step` sau đó trả rỗng |
| T22 | Người không giữ chìa vào vùng cửa trước | Không `done` |
| T23 | Người giữ chìa vào cửa rồi người kia vào | Cả 2 `done`, `phase` = WON, `LEVEL_COMPLETE.sec` đúng với số tick |
| T24 | Chạy cùng chuỗi input 2 lần | `Snap.write` giống hệt nhau ở mọi tick |
| T25 | `Snap.write` rồi `Snap.read` | Giá trị khớp (sai số làm tròn) |
| T26 | Mỗi file `levels/*.json` | `LevelLoader` nạp không lỗi, `Snap.write` của `World` mới nhỏ hơn `MAX_UDP` |

## 10. Giới hạn đã biết (không sửa, chỉ ghi nhận)

- Không đẩy đá dây chuyền.
- Không có khử chồng lấn (depenetration). Chồng lấn do sai số chỉ được ghi log.
- Đá không thể kéo, chỉ đẩy (dễ bế tắc, đã có `RESTART`).
- Nhân vật nhảy mang theo người đứng trên đầu.
