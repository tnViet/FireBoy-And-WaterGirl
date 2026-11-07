# 05 - Định dạng level

File màn nằm ở `common/src/main/resources/levels/`, cùng `index.txt` liệt kê tên file (mỗi dòng một file, theo thứ tự). Server nạp theo `index.txt`, kiểm tra bằng `LevelLoader`, đồng bộ vào bảng `levels` (xem file DB). Cả 4 file mẫu `lv1.json` đến `lv4.json` kèm theo bộ tài liệu này.

## 1. Quy ước

- Mọi tọa độ và kích thước tính bằng tile (1 tile = 32 px), có thể là số thực (ví dụ `15.5`). `LevelLoader` nhân 32 khi nạp.
- `x`, `y` là góc trên trái, trừ các vật đặt theo ô (ghi rõ bên dưới). Hàng 0 ở trên cùng.
- `id` của vật là chuỗi duy nhất trong màn, dạng ngắn: `k1`, `d1`, `r1`, `z1`, `v1`, `p1`, `e1`, `g1`.
- Kích thước màn lấy từ `tiles`: `rows` = số phần tử, `cols` = độ dài mỗi chuỗi.

## 2. Các trường

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `name` | string | Tên màn, duy nhất. Cũng là `level_name` trong DB |
| `tiles` | mảng string | Mỗi phần tử là một hàng. `#` là tường (vật rắn), `.` là trống. Mọi hàng cùng độ dài |
| `spawn` | object | `{"FIRE":{"x":2,"y":15},"WATER":{"x":3,"y":15}}`. `x`, `y` là chỉ số ô (số nguyên) nơi nhân vật đứng. Nhân vật đặt giữa ô theo chiều ngang, đáy hitbox sát đáy ô |
| `keys` | mảng | `{id, x, y}`. `x`, `y` là chỉ số ô. Chìa khóa 24x24 px đặt giữa ô. Đúng 1 phần tử |
| `doors` | mảng | `{id, x, y, w, h}`. Hình chữ nhật góc trên trái. Đúng 1 phần tử |
| `rocks` | mảng | `{id, x, y}`. `x`, `y` là chỉ số ô, đá 32x32 chiếm trọn ô |
| `zones` | mảng | `{id, kind, x, y, w, h}`. `kind` là `WATER`, `LAVA` hoặc `POISON`. Hình chữ nhật góc trên trái |
| `levers` | mảng | `{id, x, y, target}`. `x`, `y` là chỉ số ô, cần gạt 16x24 px đặt giữa ô, sát đáy ô. `target` là `id` của một cơ quan |
| `plates` | mảng | `{id, x, y, w, h, target}`. Hình chữ nhật góc trên trái, thường `h` = 0.25. `target` là `id` của một cơ quan |
| `elevators` | mảng | `{id, x, y, w, h, axis, dist, speed}`. Vị trí ban đầu (góc trên trái, khi `p` = 0) |
| `gates` | mảng | Giống `elevators` |

Cơ quan (`elevators`, `gates`):
- `axis`: `"x"` hoặc `"y"`.
- `dist`: quãng đường tính bằng tile, có dấu (âm là sang trái hoặc lên trên).
- `speed`: tile mỗi giây, trong khoảng 0.5 đến 4.

Quy ước vật lý đi kèm (xem file lõi game): thang máy và cửa chắn đều là vật rắn. Cửa chắn thường mở bằng cách trượt vào khoảng trống trong tường.

## 3. Kiểm tra hợp lệ (`LevelLoader`)

Vi phạm một điều thì ném `LevelException` với thông báo nêu rõ tên file, trường và id:

1. JSON hợp lệ, có đủ các trường trên (mảng rỗng được phép trừ `keys`, `doors`).
2. `tiles` không rỗng, mọi hàng cùng độ dài, chỉ chứa `#` và `.`.
3. Viền ngoài (hàng đầu, hàng cuối, cột đầu, cột cuối) toàn `#`.
4. `keys` đúng 1 phần tử, `doors` đúng 1 phần tử.
5. Có đủ `spawn` cho cả `FIRE` và `WATER`. Ô spawn trống, ô ngay dưới là `#`, 2 ô spawn khác nhau.
6. `id` không trùng nhau giữa mọi loại vật trong màn.
7. Mọi `target` của `levers` và `plates` trỏ tới `id` có thật của `elevators` hoặc `gates`.
8. Không vật nào (chìa khóa, cửa, đá, vùng, nút, cần gạt) chồng lên ô `#`. Với cơ quan, kiểm tra cả vị trí đầu, vị trí cuối và toàn bộ vùng quét giữa chúng.
9. `axis` hợp lệ, `speed` trong khoảng cho phép, `dist` khác 0.
10. Giới hạn số lượng để `SNAP` nhỏ hơn `MAX_UDP`: đá tối đa 6, `elevators` tối đa 4, `gates` tối đa 6, `levers` tối đa 6, `plates` tối đa 6.
11. Giá trị `kind` của vùng hợp lệ.

## 4. Quy tắc thiết kế màn (để màn chơi được)

| Quy tắc | Giá trị |
|---|---|
| Bục nhảy một mình | Cao tối đa 3 tile so với chỗ đứng |
| Bục cần hợp tác (đầu nhau hoặc đá) | Cao đúng 4 tile |
| Khe hở ngang | Tối đa 3 tile |
| Vùng nguy hiểm phải nhảy qua | Rộng tối đa 2 tile |
| Hành lang đi bộ | Cao ít nhất 2 tile (hitbox cao 30 px) |
| Khe giữa vật rắn và vật rắn kề nhau (ví dụ thang máy và bục) | 0 (chạm sát) hoặc ít nhất 2 tile. Khe 1 tile làm nhân vật rơi vào |
| Cơ quan | Đường quét không chạm ô `#`. Cửa chắn nên mở vào hốc rỗng trong tường |
| Đá | Chỉ đẩy được, không kéo được. Không để đá có thể bị đẩy vào góc gây bế tắc mà không có đá dự phòng |

## 5. Ví dụ đầy đủ: `lv1.json`

```json
{
  "name": "Level 1",
  "tiles": [
    "################################",
    "#..............................#",
    "#..............................#",
    "#..............................#",
    "#..............................#",
    "#..............................#",
    "#..............................#",
    "#..............................#",
    "#..............................#",
    "#..............................#",
    "#..................###########.#",
    "#..............................#",
    "#...........#####..............#",
    "#..............................#",
    "#.....####.....................#",
    "#..............................#",
    "################################",
    "################################"
  ],
  "spawn": {"FIRE": {"x": 2, "y": 15}, "WATER": {"x": 3, "y": 15}},
  "keys": [
    {"id": "k1", "x": 14, "y": 11}
  ],
  "doors": [
    {"id": "d1", "x": 27, "y": 8, "w": 1, "h": 2}
  ],
  "rocks": [],
  "zones": [
    {"id": "z1", "kind": "WATER", "x": 10, "y": 15.5, "w": 2, "h": 0.5},
    {"id": "z2", "kind": "LAVA", "x": 17, "y": 15.5, "w": 2, "h": 0.5}
  ],
  "levers": [],
  "plates": [],
  "elevators": [],
  "gates": []
}
```

## 6. Bốn màn mẫu

| File | Ý tưởng | Lời giải ngắn |
|---|---|---|
| `lv1.json` | Màn cơ bản: các bục bậc thang 2 tile, 2 hồ dưới các khe | Cả 2 leo lần lượt qua 3 bục. Ai lấy chìa phải vào cửa trước. Rơi nhầm hồ thì chết (hồ nước giết Lửa, hồ dung nham giết Nước) |
| `lv2.json` | Nút và cần gạt: tường kín trần có cửa chắn, tháp 4 tile cần xếp chồng | Một người đứng lên nút giữ cửa mở. Người kia đi qua, gạt cần để cửa mở hẳn. Cả 2 xếp chồng lên tháp lấy chìa, người giữ chìa vào cửa trước |
| `lv3.json` | Hai thang máy: thang 1 bằng nút, thang 2 bằng cần gạt, hồ độc dưới bục | A giữ nút, B lên thang 1 đến bục. A chạy sang thang 2 đứng chờ. B gạt cần đưa A lên. B lấy chìa, vào cửa trước |
| `lv4.json` | Đá làm bậc: 2 đá, bục cao 4 tile, hồ nước trên đường đẩy đá, hồ độc trên bục | Chỉ nhân vật Nước đẩy đá qua hồ nước (Lửa sẽ chết). Đá đặt sát chân bục, mỗi người nhảy lên đá rồi lên bục. Nhảy qua hồ độc, giữ chìa vào cửa trước |

Lưu ý: 4 màn này được kiểm tra cấu trúc (kích thước, vị trí vật thể, mục tiêu cơ quan) bằng script nhưng chưa được chơi thử. Khi có `World` chạy được, viết test bot hoặc chơi thử từng màn. Nếu màn không giải được thì sửa file màn, không sửa vật lý để chiều theo màn.
