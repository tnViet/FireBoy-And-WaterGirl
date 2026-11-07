# 07 - Cơ sở dữ liệu

MySQL 8, truy cập bằng JDBC thuần (`PreparedStatement`). Dựa trên tài liệu thiết kế DB của bạn, đã sửa các điểm mâu thuẫn (mục 8).

## 1. Chuẩn bị

```sql
CREATE DATABASE fw CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'fw'@'localhost' IDENTIFIED BY 'fw';
GRANT ALL PRIVILEGES ON fw.* TO 'fw'@'localhost';
```

Server chạy `schema.sql` (trong `server/src/main/resources`) khi khởi động, bằng `CREATE TABLE IF NOT EXISTS`, nên chạy lại nhiều lần không sao. `Db.init` tách file theo dấu `;` cuối dòng và chạy từng câu.

## 2. `schema.sql`

```sql
CREATE TABLE IF NOT EXISTS accounts (
  account_id    INT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name  VARCHAR(50)  NOT NULL,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS levels (
  level_id   INT AUTO_INCREMENT PRIMARY KEY,
  level_name VARCHAR(50) NOT NULL UNIQUE,
  data       JSON NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS level_completions (
  completion_id   INT AUTO_INCREMENT PRIMARY KEY,
  account_id      INT NOT NULL,
  level_id        INT NOT NULL,
  completion_time DECIMAL(6,2) NOT NULL,
  completed_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (account_id) REFERENCES accounts(account_id),
  FOREIGN KEY (level_id)   REFERENCES levels(level_id),
  INDEX idx_lv_time (level_id, completion_time),
  INDEX idx_acc_lv (account_id, level_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS player_results (
  account_id        INT PRIMARY KEY,
  levels_completed  INT NOT NULL DEFAULT 0,
  total_plays       INT NOT NULL DEFAULT 0,
  last_completed_at DATETIME NULL,
  FOREIGN KEY (account_id) REFERENCES accounts(account_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS player_level_stats (
  account_id INT NOT NULL,
  level_id   INT NOT NULL,
  attempts   INT NOT NULL DEFAULT 0,
  PRIMARY KEY (account_id, level_id),
  FOREIGN KEY (account_id) REFERENCES accounts(account_id),
  FOREIGN KEY (level_id)   REFERENCES levels(level_id)
) ENGINE=InnoDB;
```

Khác bản thiết kế: `levels.level_name` thêm `UNIQUE` (để đồng bộ file màn theo tên), `completion_time` `NOT NULL`, thêm 2 chỉ mục.

## 3. `Db`

```java
final class Db {
  static void init(String url, String user, String pass)   // lưu cấu hình, chạy schema.sql
  static Connection get() throws SQLException              // DriverManager.getConnection mỗi lần gọi
}
```

Mỗi thao tác mở một kết nối mới và đóng bằng `try-with-resources`. Với quy mô bài tập đây là đủ, không cần pool.

Quy tắc chung cho DAO:
- Luôn dùng `PreparedStatement` với tham số `?`. Không nối chuỗi vào SQL.
- Thao tác nhiều câu lệnh dùng transaction: `con.setAutoCommit(false)`, `commit()` khi xong, `rollback()` trong khối bắt lỗi.
- Lỗi `SQLException` ném lên cho dịch vụ gọi (dịch vụ chuyển thành mã lỗi trong giao thức).
- Log DEBUG cho mỗi thao tác DAO kèm thời gian chạy (ms). Log ERROR kèm `SQLState` và `errorCode` khi lỗi.

## 4. `Pass`

Dùng thư viện `at.favre.lib:bcrypt`:

```java
static String hash(String pw)   { return BCrypt.withDefaults().hashToString(10, pw.toCharArray()); }
static boolean check(String pw, String hash) { return BCrypt.verifyer().verify(pw.toCharArray(), hash).verified; }
```

Salt nằm sẵn trong chuỗi hash, nên kiểm tra mật khẩu bằng `check`, không băm lại rồi so sánh chuỗi.

## 5. `AccountDao`

| Hàm | Việc |
|---|---|
| `int create(String username, String hash, String name)` | Một transaction: `INSERT INTO accounts(username, password_hash, display_name) VALUES(?,?,?)` lấy khóa sinh tự động `account_id`, rồi `INSERT INTO player_results(account_id) VALUES(?)`. Trùng `username` ném `SQLIntegrityConstraintViolationException` (dịch vụ chuyển thành `USER_EXISTS`) |
| `Acc find(String username)` | `SELECT account_id, password_hash, display_name FROM accounts WHERE username = ?`. Trả `record Acc(int id, String hash, String name)` hoặc `null` |

## 6. `LevelDao` và `Levels`

`LevelDao.sync()`:
1. Đọc `levels/index.txt` từ classpath, mỗi dòng là tên file.
2. Mỗi file: đọc nội dung, `LevelLoader.load(json)` (kiểm tra hợp lệ, lỗi thì dừng server với thông báo rõ), lấy `name`.
3. Ghi vào DB:
   ```sql
   INSERT INTO levels(level_name, data) VALUES(?, ?)
   ON DUPLICATE KEY UPDATE data = VALUES(data)
   ```
4. Màn trong DB không còn trong `index.txt` thì để nguyên (không xóa).

`LevelDao.all()`: `SELECT level_id, level_name, data FROM levels ORDER BY level_id`, trả `List<LevelRow>`. `Levels.load()` gọi hàm này, parse từng `data` bằng `LevelLoader`, lưu vào bản đồ. `Levels.get(lv)` trả `LevelRow` hoặc `null`.

## 7. `ResultDao`

### 7.1 `onStart(int acc1, int acc2, int lv)`

Một transaction. Với mỗi `acc` trong 2 tài khoản:

```sql
INSERT INTO player_level_stats(account_id, level_id, attempts) VALUES(?, ?, 1)
ON DUPLICATE KEY UPDATE attempts = attempts + 1;

UPDATE player_results SET total_plays = total_plays + 1 WHERE account_id = ?;
```

Gọi khi bắt đầu một trận (kể cả `RESTART`), trước khi tạo `World`. Một "lượt chơi" tính ngay khi bắt đầu, dù sau đó thua hay bỏ dở.

### 7.2 `onWin(int acc1, int acc2, int lv, double sec)`

Một transaction. Với mỗi `acc`:

```sql
SELECT 1 FROM level_completions WHERE account_id = ? AND level_id = ? LIMIT 1;     -- first = (không có dòng)
INSERT INTO level_completions(account_id, level_id, completion_time) VALUES(?, ?, ?);
-- nếu first:
UPDATE player_results
SET levels_completed = levels_completed + 1, last_completed_at = NOW()
WHERE account_id = ?;
```

Việc `SELECT` phải chạy trước `INSERT` trong cùng transaction. `total_plays` không tăng ở đây (đã tăng trong `onStart`).

### 7.3 `rankAll(int limit)`

```sql
SELECT a.display_name, r.levels_completed, r.total_plays
FROM player_results r JOIN accounts a ON a.account_id = r.account_id
WHERE r.levels_completed > 0
ORDER BY r.levels_completed DESC, r.total_plays ASC, r.last_completed_at ASC, r.account_id ASC
LIMIT ?
```

Trả `List<RowAll(String name, int done, int plays)>`. `pos` là thứ tự dòng, bắt đầu từ 1.

### 7.4 `rankLevel(int lv, int limit)`

Mỗi tài khoản chỉ xuất hiện một lần, với thời gian tốt nhất:

```sql
WITH ranked AS (
  SELECT c.account_id, c.completion_time, c.completed_at,
         ROW_NUMBER() OVER (PARTITION BY c.account_id
                            ORDER BY c.completion_time ASC, c.completed_at ASC) AS rn
  FROM level_completions c
  WHERE c.level_id = ?
)
SELECT a.display_name, r.completion_time, COALESCE(s.attempts, 0) AS tries
FROM ranked r
JOIN accounts a ON a.account_id = r.account_id
LEFT JOIN player_level_stats s ON s.account_id = r.account_id AND s.level_id = ?
WHERE r.rn = 1
ORDER BY r.completion_time ASC, tries ASC, r.completed_at ASC, r.account_id ASC
LIMIT ?
```

Trả `List<RowLv(String name, double sec, int tries)>`.

Quy tắc sắp xếp (khớp tài liệu thiết kế):
- Xếp hạng tổng: nhiều màn hoàn thành hơn thì cao hơn, bằng nhau thì ít lượt chơi hơn cao hơn, bằng nữa thì ai đạt mốc sớm hơn cao hơn.
- Xếp hạng theo màn: thời gian ngắn hơn cao hơn, bằng nhau thì ít lượt thử màn đó hơn cao hơn, bằng nữa thì hoàn thành sớm hơn cao hơn.

## 8. Điểm đã sửa so với tài liệu thiết kế DB

1. `total_plays` tăng khi bắt đầu một lượt chơi (cùng lúc `attempts`), không tăng khi hoàn thành. Nhờ vậy khớp với "tổng số lần chơi" của đề và tiêu chí phụ "ít lượt chơi hơn xếp trên" có nghĩa.
2. Xếp hạng theo màn lấy thời gian tốt nhất của mỗi tài khoản, tránh một người xuất hiện nhiều dòng.
3. `player_results` được tạo cùng transaction với `accounts` lúc đăng ký (quan hệ 1-1 luôn đầy đủ).
4. Ghi kết quả thắng nằm trong một transaction.
5. `levels` có `UNIQUE(level_name)` phục vụ đồng bộ từ file; file màn là nguồn gốc dữ liệu màn.
6. Cấu trúc JSON của `levels.data` đổi theo `05-level.md` (thêm `tiles` cho địa hình; `buttons` đổi tên thành `plates`).

## 9. Test DAO (kiểm tra thủ công hoặc JUnit có DB thật)

| Bước | Mong đợi |
|---|---|
| `create("an","h","An")` hai lần | Lần hai ném lỗi trùng |
| Sau `create`, `SELECT * FROM player_results` | Có 1 dòng với 0, 0, NULL |
| `onStart(1,2,1)` 3 lần | `attempts` = 3 và `total_plays` = 3 cho cả hai |
| `onWin(1,2,1,41.25)` | `levels_completed` = 1 cho cả hai, `level_completions` thêm 2 dòng |
| `onWin(1,2,1,40.00)` lần nữa | `levels_completed` vẫn 1, `level_completions` thêm 2 dòng |
| `rankLevel(1, 10)` | Mỗi tài khoản 1 dòng với `sec` = 40.00 |
| `rankAll(10)` | Thứ tự theo quy tắc ở mục 7.4 (kiểm tra bằng dữ liệu giả có hòa) |
