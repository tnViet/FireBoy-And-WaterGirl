-- Đổi tên màn 3 và màn 4 nhưng giữ nguyên level_id, để lịch sử chơi cũ vẫn gắn đúng màn.
-- Chạy MỘT LẦN, TRƯỚC khi khởi động server với các file lv3.json / lv4.json mới.
-- Tên mới phải giống hệt "name" trong file JSON.
--
-- Chỉ đổi khi tên cũ còn đúng (chạy lại lần 2 sẽ không ảnh hưởng gì).

SET NAMES utf8mb4;

START TRANSACTION;

UPDATE levels SET level_name = 'Màn 3: Bốn tầng'
WHERE level_id = 3 AND level_name = 'Màn 3: Đá đè nút';

UPDATE levels SET level_name = 'Màn 4: Phòng khóa'
WHERE level_id = 4 AND level_name = 'Màn 4: Hai tầng';

COMMIT;

-- Kiểm tra lại kết quả
SELECT level_id, level_name FROM levels ORDER BY level_id;
