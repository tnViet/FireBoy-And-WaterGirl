# Game online phối hợp 2 người qua màn

- Hệ thống gồm một server và nhiều client. Server lưu trữ thông tin người chơi, phòng chơi và trạng thái của các level.

- Để bắt đầu chơi, người chơi đăng nhập vào tài khoản của mình từ một máy client. Sau khi đăng nhập thành công, giao diện hiển thị danh sách những người chơi đang online, gồm: tên người chơi và trạng thái (đang rảnh hoặc đang chơi).

- Người chơi có thể tạo một phòng chơi hoặc tham gia vào một phòng đang có sẵn. Mỗi phòng có tối đa 2 người chơi.

- Khi tạo phòng, người chơi trở thành chủ phòng. Chủ phòng có thể bắt đầu trò chơi khi đã có đủ 2 người chơi trong phòng.

- Khi trò chơi bắt đầu, server gửi thông tin màn chơi và vị trí ban đầu của tất cả người chơi đến các client.

- Giao diện chơi gồm một màn hình 2D đơn giản. Mỗi người chơi điều khiển một nhân vật có thể di chuyển trái, phải và nhảy.

- Trong mỗi màn chơi có một chìa khóa và một cánh cửa. Người chơi phải phối hợp với nhau để lấy chìa khóa và đi đến cửa để hoàn thành màn.

- Khi một người chơi di chuyển hoặc nhảy, client gửi thông tin thao tác đến server. Server xử lý và cập nhật trạng thái nhân vật, sau đó gửi trạng thái mới cho những người chơi khác trong cùng phòng.

- Server là nơi quản lý trạng thái chính của màn chơi, bao gồm: vị trí người chơi, trạng thái chìa khóa, trạng thái cửa và trạng thái hoàn thành màn. Client không tự quyết định kết quả của màn chơi.

- Khi một người chơi chạm vào chìa khóa, server kiểm tra và cập nhật trạng thái chìa khóa thành đã lấy. Sau đó server thông báo cho người chơi còn lại để cập nhật giao diện.

- Khi một người chơi đã lấy được chìa khóa,  2 người chơi có thể đi đến cánh cửa, người cầm chìa vào trước. Khi cả 2 người chơi đã qua cửa, server thông báo cho 2 người chơi rằng màn chơi đã hoàn thành.

- Sau khi hoàn thành một màn, server hiển thị thông báo cho 2 người chơi và cho phép chủ phòng chọn chơi màn tiếp theo hoặc thoát phòng.

- Nếu một người chơi thoát khỏi phòng giữa trận, server thông báo cho người chơi còn lại và cập nhật danh sách thành viên trong phòng.

- Người chơi có thể chủ động thoát khỏi phòng bằng cách click vào nút “Thoát”. Khi đó server cập nhật trạng thái phòng và thông báo cho client còn lại.

- Hệ thống lưu một số màn chơi đơn giản, mỗi màn chỉ thay đổi vị trí của chìa khóa, cửa, chướng ngại vật,...

- Kết quả chơi được lưu trên server, gồm: tên người chơi, số màn đã hoàn thành và tổng số lần chơi.

- Người chơi có thể xem bảng xếp hạng, sắp xếp theo số màn đã hoàn thành. 

- Hai người chơi phối hợp điều khiển hai nhân vật mang nguyên tố đối lập (Lửa và Nước) giải đố vượt địa hình, thu thập chìa khóa và cùng nhau về tới cánh cửa.

- Thiết kế màn chơi buộc một người phải kích hoạt cơ quan mở đường để người kia đi qua, sau đó người thứ hai tìm cách mở tiếp lối đi cho người thứ nhất. 

- Fireboy an toàn khi đi qua hồ dung nham nhưng chết khi chạm nước; Watergirl miễn nhiễm với hồ nước nhưng chết khi chạm dung nham; cả hai đều bị tiêu diệt nếu rơi vào vũng chất độc màu xanh.

- Khối đá chịu tác động của trọng lực và ma sát sàn. Khi nhân vật va chạm theo phương ngang và tiếp tục di chuyển, lực đẩy sẽ làm khối đá dịch chuyển để dùng làm bệ leo trèo.

- Nút cảm biến lực: Hoạt động theo cơ chế vùng kích hoạt. Nút chỉ hạ xuống và kích hoạt cơ quan (thang máy, cửa chắn) khi có tải trọng đè lên (nhân vật hoặc khối đá) và tự động hồi vị khi tải trọng rời đi. 

- Cơ chế công tắc bật/tắt trạng thái kích hoạt các cánh cửa tịnh tiến theo hành trình định sẵn (theo trục x hoặc y). 

- Đỉnh đầu của mỗi nhân vật được xử lý như một bề mặt va chạm phẳng. Người chơi có thể nhảy đè lên đầu nhau để tiếp cận các vị trí vượt quá độ cao nhảy tối đa của một cá nhân. 

- Khung va chạm giữa các nhân vật không xuyên qua nhau.

## Cơ chế xếp hạng 

- Xếp hạng theo số màn chơi hoàn thành

- Xếp hạng theo thời gian hoàn thành 1 màn chơi cụ thể

## Các vật thể sẽ xuất hiện

- 2 nhân vật băng và lửa

- Khối đá

- Vùng nước / dung nham / chất độc

- Chìa khóa

- Cánh cửa

- Cần gạt

- Nút cảm biến

- Thang máy

- Cửa chắn 

## Sound effect

- Khi nhân vật chết (chỉ khi rơi vào nước , dung nham , chất độc)

- Khi bấm nút , gạt cần gạt

- Khi cửa chắn, thang máy hoạt động (do nút bấm, cần gạt )

- Khi lấy được chìa khóa 

- Khi kết thúc màn chơi
