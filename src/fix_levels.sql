-- Dọn bảng levels sau lần chạy server bằng bản build cũ:
--   1. Xóa dòng thừa 64 "Màn 4: Hai tầng" (bản sao màn 4 cũ), kèm lịch sử nếu có.
--   2. Đổi id màn 5 từ 65 thành 5, chuyển luôn lịch sử (nếu có) sang id mới.
-- Chạy khi server ĐANG TẮT. Sau đó Clean and Build rồi mới chạy ServerMain.
-- Mọi lệnh đều kiểm tra cả id lẫn tên, nên chạy lại lần 2 sẽ không ảnh hưởng gì.

SET NAMES utf8mb4;

START TRANSACTION;

-- 1. Xóa dòng 64
DELETE FROM level_completions  WHERE level_id = 64;
DELETE FROM player_level_stats WHERE level_id = 64;
DELETE FROM levels WHERE level_id = 64 AND level_name = 'Màn 4: Hai tầng';

-- 2. Đổi 65 -> 5
-- level_name là UNIQUE và các bảng lịch sử khóa ngoại tới level_id, nên làm theo thứ tự:
-- đổi tạm tên dòng 65 -> tạo dòng 5 với tên và dữ liệu cũ -> chuyển lịch sử sang 5 -> xóa dòng 65.
UPDATE levels SET level_name = '__tmp_lv5'
WHERE level_id = 65 AND level_name = 'Màn 5: Ba cần gạt';

INSERT INTO levels (level_id, level_name, data)
SELECT 5, 'Màn 5: Ba cần gạt', data FROM levels
WHERE level_id = 65 AND level_name = '__tmp_lv5';

UPDATE level_completions  SET level_id = 5 WHERE level_id = 65;
UPDATE player_level_stats SET level_id = 5 WHERE level_id = 65;

DELETE FROM levels WHERE level_id = 65 AND level_name = '__tmp_lv5';

COMMIT;

-- Đặt lại bộ đếm id (MySQL tự nâng lên id lớn nhất + 1 nếu số này quá nhỏ)
ALTER TABLE levels AUTO_INCREMENT = 6;

-- Kiểm tra: phải còn đúng 5 dòng, id 1 -> 5
SELECT level_id, level_name FROM levels ORDER BY level_id;
