# 10 - Quy tắc làm việc với AI code

Dán hoặc đính kèm file này (cùng các file đặc tả liên quan) mỗi khi giao việc cho AI.

## 1. Quy tắc cứng cho AI

1. Chỉ làm đúng việc được giao. Không viết thêm tính năng, không viết lại phần không được nhắc tới.
2. Đặc tả là nguồn sự thật. Tên lớp, hàm, trường JSON, mã lỗi, hằng số, thứ tự xử lý lấy đúng theo đặc tả. Thấy mâu thuẫn hoặc thiếu thì dừng và hỏi, không tự đoán rồi làm.
3. Không đổi giao thức (`02-protocol.md`) và không đổi thứ tự xử lý vật lý (`04-core-game.md` mục 4) nếu chưa được duyệt.
4. Không thêm thư viện ngoài danh sách ở `01-overview.md` mục 2.
5. Tên ngắn bằng tiếng Anh theo `01-overview.md` mục 5. Không viết tắt ngoài bảng viết tắt. Không dùng `import x.*`. Mỗi file một lớp cấp cao nhất.
6. Chỉ dùng cú pháp Java 17 trở xuống. `maven.compiler.release` = 17.
7. Chỉ viết chú thích khi cần: một dòng đầu lớp nói trách nhiệm của lớp, và các chỗ logic không hiển nhiên (kèm số mục trong đặc tả). Chú thích bằng tiếng Anh.
8. Log đúng các chỗ yêu cầu ở `09-debug.md` mục 1 (tag, mức, key=value). Mọi `catch` phải log hoặc xử lý, không nuốt lỗi im lặng.
9. Luồng và khóa theo `06-server.md` mục 13 và `08-client.md` mục 2. Không gọi mạng hoặc DB khi giữ khóa phòng. Không thao tác Swing ngoài EDT.
10. `common` không phụ thuộc Swing, JDBC, socket. `World` không dùng `Random`, đồng hồ thật, hay thứ gì không xác định.
11. Module `common` phải kèm test JUnit theo bảng ở `04-core-game.md` mục 9 cho phần được giao. Test chưa qua thì chưa tính là xong.
12. Mỗi lần giao xong, AI phải liệt kê: file đã tạo hoặc sửa, điểm nào trong đặc tả chưa làm, giả định đã dùng, cách chạy để kiểm tra.
13. Không thay đổi file tài liệu. Muốn sửa tài liệu thì đề xuất bằng văn bản, người dùng quyết định.

## 2. Mẫu prompt giao việc

```
Đọc các file đặc tả đính kèm: 01-overview.md, 10-ai-rules.md, <các file liên quan>.

Việc cần làm: <mô tả ngắn, ví dụ "Viết lớp World và các lớp trạng thái trong fw.common.game theo 04-core-game.md mục 3 đến 6">.
Phạm vi file được phép tạo hoặc sửa: <danh sách đường dẫn>.
Không được đụng vào: <danh sách đường dẫn khác>.

Tiêu chí xong:
- <ví dụ: mvn -pl common test chạy qua các test T01 đến T24>
- <ví dụ: Snap.write cho World mới của lv1.json nhỏ hơn 1400 byte>

Nếu đặc tả thiếu hoặc mâu thuẫn, hỏi trước khi viết code. Khi xong, báo cáo theo quy tắc số 12 trong 10-ai-rules.md.
```

## 3. Mẫu báo lỗi cho AI

Khi gặp lỗi, đưa cho AI đủ những thứ sau (thiếu một thứ thì AI thường đoán sai):

```
Hiện tượng: <chuyện gì xảy ra, so với mong đợi>.
Cách tái hiện: <các bước, hoặc lệnh Sim/Bot cụ thể, hoặc file rec>.
Log liên quan: <20 đến 50 dòng log quanh lúc lỗi, của cả server và client, giữ nguyên định dạng>.
Mã lỗi hoặc stack trace: <nếu có>.
Mục đặc tả liên quan: <ví dụ 04-core-game.md mục 5.5>.
Điều tôi đã thử: <nếu có>.
Yêu cầu: tìm nguyên nhân gốc, nêu rõ dòng nào sai và vì sao, sửa tối thiểu, thêm test tái hiện lỗi nếu là lỗi ở common. Không sửa chỗ khác.
```

## 4. Thói quen nên giữ

- Chạy lại toàn bộ test sau mỗi lần AI sửa: `mvn -q test`.
- Khi AI sửa lỗi, yêu cầu viết test tái hiện lỗi trước (ở `common`) rồi mới sửa.
- Commit sau mỗi phần chạy được (git). Lỗi lan rộng thì quay lại bản trước.
- Giữ nguyên các file đặc tả, nếu đổi quyết định thì sửa đặc tả trước rồi mới yêu cầu sửa code.
- Một câu hỏi, một lỗi mỗi lần. Đừng gộp nhiều lỗi vào một lần nhờ AI.
