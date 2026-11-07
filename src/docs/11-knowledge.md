# 11 - Kiến thức nền cho người mới học

File này giải thích những gì bạn cần hiểu để làm chủ bài tập lớn, từ đầu và bằng lời dễ hiểu. Không cần đọc hết một lượt, hãy đọc theo nhu cầu. Phần cuối có bảng "hiện tượng, nên đọc mục nào".

## 1. Bức tranh tổng thể

Game của bạn gồm 3 chương trình:
- **Server**: một chương trình chạy trên một máy, giữ mọi thứ quan trọng (tài khoản, phòng, trạng thái màn chơi).
- **Client**: chương trình người chơi mở. Mỗi người chơi một client. Client chỉ hiển thị và gửi phím bấm.
- **MySQL**: nơi cất dữ liệu lâu dài (tài khoản, kết quả, xếp hạng, các màn). Chỉ server nói chuyện với MySQL.

Ví dụ cho dễ hình dung: server như trọng tài của một trận bóng. Cầu thủ (client) chỉ cho biết mình muốn chạy hướng nào. Trọng tài mới là người quyết định ai ở đâu, bóng có vào lưới không, rồi báo kết quả cho tất cả mọi người. Nhờ vậy không ai gian lận được, và hai người chơi luôn thấy cùng một trận đấu.

Luồng chính của một ván:
1. Người chơi đăng nhập (client gửi tên và mật khẩu, server hỏi MySQL).
2. Vào phòng với bạn.
3. Bấm Bắt đầu. Server tạo ra "thế giới" của ván đó.
4. Mỗi phím bấm của bạn được gửi lên server. Server tính toán nhân vật đi đâu rồi gửi ảnh chụp thế giới (gọi là snapshot) về cho cả 2 client, khoảng 30 lần mỗi giây. Client vẽ lại.
5. Khi cả 2 qua cửa, server báo thắng, ghi kết quả vào MySQL.

## 2. Mạng máy tính: socket, TCP, UDP

### 2.1 Socket là gì

Socket là "ổ cắm" để hai chương trình nói chuyện qua mạng. Một bên mở cổng (port) chờ, một bên kết nối tới địa chỉ IP và cổng đó. IP giống địa chỉ nhà, port giống số phòng trong nhà. Máy bạn dùng để thử cả server lẫn client thì IP là `127.0.0.1` (nghĩa là "chính máy này").

### 2.2 TCP: như gọi điện thoại

- Phải "bấm số" và bên kia "nhấc máy" trước (kết nối).
- Đảm bảo mọi thứ bạn nói đến nơi đầy đủ, đúng thứ tự. Nếu mất, hệ điều hành tự gửi lại.
- Nhược điểm: nếu một gói bị mất thì mọi thứ phía sau phải chờ gói đó được gửi lại, gây trễ.

Dùng cho: đăng nhập, tạo phòng, các sự kiện chỉ xảy ra một lần (nhặt chìa, thắng). Những thứ này tuyệt đối không được mất.

Ví dụ rất nhỏ (Java):

```java
// SERVER
ServerSocket ss = new ServerSocket(5000);
Socket s = ss.accept();                       // chờ cho đến khi có client nối vào
BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
String line = in.readLine();                  // đọc 1 dòng (đến khi gặp \n)

// CLIENT
Socket s = new Socket("127.0.0.1", 5000);
PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
out.println("xin chào");                       // gửi 1 dòng, kết thúc bằng \n
```

**Vì sao mỗi tin là một dòng?** TCP là một "dòng nước" liên tục, nó không biết tin nhắn bắt đầu và kết thúc ở đâu. Hai tin gửi liền nhau có thể dính vào nhau khi đọc. Quy ước "mỗi tin là một dòng, kết thúc bằng ký tự xuống dòng" giúp tách tin ra. Đó là lý do trong giao thức luôn có `\n`.

### 2.3 UDP: như gửi bưu thiếp

- Không cần kết nối trước. Ném bưu thiếp đi là xong.
- Có thể mất, có thể đến muộn, có thể đến sai thứ tự. Nhưng không bao giờ phải chờ gói bị mất.
- Mỗi bưu thiếp (datagram) độc lập, nên không cần ký tự xuống dòng để tách.

Dùng cho: vị trí nhân vật, gửi 30 lần mỗi giây. Nếu mất một gói thì gói tiếp theo (33 ms sau) sẽ thay thế, nên không cần gửi lại. Gửi lại gói cũ thậm chí còn có hại vì vị trí cũ không còn đúng nữa.

Ví dụ (Java):

```java
DatagramSocket sock = new DatagramSocket(5001);                 // server mở cổng
byte[] buf = new byte[2048];
DatagramPacket p = new DatagramPacket(buf, buf.length);
sock.receive(p);                                                 // chờ 1 gói
String s = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
// p.getSocketAddress() cho biết ai gửi, dùng để trả lời

byte[] data = "ack".getBytes(StandardCharsets.UTF_8);
sock.send(new DatagramPacket(data, data.length, p.getSocketAddress()));
```

### 2.4 Vì sao dùng cả hai

| | TCP | UDP |
|---|---|---|
| Đảm bảo đến nơi | Có | Không |
| Đúng thứ tự | Có | Không |
| Độ trễ | Có thể bị đọng | Thấp |
| Dùng cho | Việc quan trọng, ít, không lặp lại | Dữ liệu cập nhật liên tục |

Mất một gói chứa "nhặt chìa khóa" thì ván hỏng, nên dùng TCP. Mất một gói vị trí thì chẳng sao, nên dùng UDP.

### 2.5 Bắt tay UDP

UDP không có "kết nối", nên server không biết client nằm ở IP và cổng nào. Vì vậy client phải gửi trước một gói `UDP_HELLO`. Server đọc được địa chỉ người gửi từ gói đó, nhớ lại, và từ giờ gửi snapshot về đúng địa chỉ ấy. Gói `UDP_HELLO` mang theo `token` (một mã bí mật server đã đưa cho client lúc đăng nhập qua TCP) để chứng minh "tôi đúng là người này", tránh người lạ giả mạo.

Client gửi `UDP_HELLO` lặp lại cho đến khi nhận `UDP_ACK` vì chính gói hello cũng có thể bị mất.

## 3. JSON

JSON là cách viết dữ liệu thành chữ để gửi qua mạng, ví dụ:

```json
{"type":"LOGIN","rid":2,"data":{"username":"an","password":"secret1"}}
```

Mọi gói trong game đều có dạng này: `type` là tên lệnh, `data` là nội dung. Thư viện Gson chuyển qua lại giữa JSON và đối tượng Java. Lưu ý: dùng `JsonObject` để đọc từng trường, tránh chuyển vào `Map` vì số nguyên sẽ thành `1.0`.

## 4. Luồng (thread) và chuyện nhiều việc xảy ra cùng lúc

### 4.1 Vì sao cần nhiều luồng

Lệnh `readLine()` và `accept()` **đứng chờ** cho đến khi có dữ liệu. Nếu server chỉ có một luồng, nó đứng chờ client A thì không phục vụ được client B. Giải pháp: mỗi client có một luồng riêng (`ClientHandler`) để đứng chờ.

Tương tự ở client: giao diện Swing chạy trên luồng riêng gọi là EDT. Nếu bạn để EDT đứng chờ mạng thì cửa sổ đơ cứng. Nên luồng mạng riêng, rồi nhờ EDT cập nhật giao diện qua `SwingUtilities.invokeLater`.

### 4.2 Nguy hiểm: hai luồng cùng sửa một dữ liệu

Hai người cùng bấm "Vào phòng" vào đúng một phòng còn 1 chỗ. Hai luồng cùng thấy "còn chỗ", cùng thêm người vào, phòng thành 3 người. Đó là lỗi **race condition** (chạy đua).

Giải pháp: **khóa**. `synchronized (room) { ... }` đảm bảo tại một thời điểm chỉ một luồng được ở trong khối đó. Luồng kia phải chờ. Quy tắc dự án: mọi thứ động đến một phòng đều nằm trong `synchronized (room)`.

### 4.3 Nguy hiểm: deadlock

Luồng 1 giữ khóa A chờ khóa B, luồng 2 giữ khóa B chờ khóa A. Cả hai đứng chờ mãi. Cách tránh: luôn lấy khóa theo cùng một thứ tự, và không làm việc chậm (DB, mạng chặn) khi đang giữ khóa. Đó là lý do việc gửi tin TCP đi qua hàng đợi (đưa tin vào hàng đợi là tức thì), có một luồng ghi riêng lấy tin ra gửi.

### 4.4 `volatile`

Một biến được nhiều luồng đọc/ghi mà không có khóa thì cần `volatile` để luồng này thấy ngay giá trị luồng kia vừa ghi. Dự án dùng cho `Sess.input` (UdpServer ghi, Loop đọc).

## 5. Vòng lặp game (game loop) và tick

Game không "chạy liên tục", nó chạy theo từng bước rời rạc gọi là **tick**. Mỗi tick, server làm tuần tự: đọc phím, di chuyển mọi thứ một chút xíu, kiểm tra va chạm, kiểm tra sự kiện.

Dự án dùng 60 tick mỗi giây, nghĩa là mỗi tick là 1/60 giây (`dt`). Nhân vật có vận tốc 200 px/giây thì mỗi tick đi 200 × (1/60) ≈ 3,33 px.

Điểm quan trọng: `dt` cố định, không đo thời gian thật. Như vậy cùng chuỗi phím bấm luôn cho ra đúng cùng một kết quả (tính xác định). Nhờ đó:
- Thời gian hoàn thành màn = số tick chia 60, công bằng cho mọi người.
- Lỗi vật lý tái hiện được bằng cách chạy lại chuỗi input (`Rec` và `Sim`).

Server cứ 2 tick thì gửi một snapshot (30 lần mỗi giây). Gửi ít hơn tick để tiết kiệm mạng.

## 6. Mô hình "server là chủ" (authoritative server)

Có hai cách làm:
- **Client tự tính vị trí rồi gửi lên**. Dễ làm, nhưng ai sửa client là gian lận được (ví dụ bay xuyên tường), và hai máy dễ thấy hai cảnh khác nhau.
- **Client chỉ gửi phím, server tính hết** (cách dự án dùng). Server là nguồn sự thật duy nhất. Khó hơn một chút, nhưng đúng yêu cầu đề ("client không tự quyết định kết quả") và hai người luôn thấy cảnh nhất quán.

Hệ quả: khi bạn bấm phím, nhân vật chỉ di chuyển sau khi lệnh đi tới server, được tính, rồi snapshot về. Trong mạng LAN chỉ vài chục mili giây nên không thấy khác.

## 7. Nội suy (interpolation)

Snapshot đến 30 lần mỗi giây, nhưng màn hình vẽ 60 lần mỗi giây. Nếu chỉ vẽ theo snapshot, nhân vật sẽ giật. Client vẽ "lùi lại 100 ms": tìm 2 snapshot bao quanh thời điểm đó và vẽ vị trí ở giữa hai điểm (nội suy tuyến tính). Chuyển động mượt dù gói đến không đều. Cái giá là hình hiển thị trễ 100 ms, đổi lấy sự mượt mà.

## 8. Va chạm trong game 2D (AABB)

AABB là "hình chữ nhật không xoay" (axis-aligned bounding box). Mọi vật (nhân vật, đá, tường) được xem là hình chữ nhật. Hai hình chữ nhật chồng nhau khi cả hai trục x và y cùng chồng.

**Di chuyển theo từng trục**: di chuyển theo trục x trước và xử lý va chạm, rồi mới di chuyển theo trục y. Làm riêng từng trục thì dễ xác định "va vào từ phía nào":
- Đi sang phải mà va thì dừng sát mép trái của vật cản.
- Rơi xuống mà va thì đáp lên đỉnh vật cản (đó là lúc nhân vật "đứng trên mặt đất").

**Trọng lực**: mỗi tick, vận tốc rơi tăng thêm `GRAV × dt`. Nhảy là đặt vận tốc hướng lên một lần (`-JUMP_V`), rồi trọng lực kéo dần thành rơi xuống.

**Đứng trên đầu nhau và thang máy**: đỉnh đầu nhân vật là một mặt phẳng như sàn. Vật đứng trên nó gọi là "rider" (người cưỡi). Khi vật bên dưới dịch chuyển (đi, lên thang máy), mọi rider dịch chuyển theo cùng một lượng. Đó chính là thuật toán `moveGroup`.

**Đẩy đá**: khi nhân vật đi vào đá, đá bị đẩy đi một đoạn bằng đúng đoạn nhân vật di chuyển thêm, nhưng nhân vật chậm đi một nửa (`PUSH_K`). Đá bị tường chặn thì cả hai đứng lại.

## 9. Cơ sở dữ liệu và JDBC

MySQL lưu dữ liệu thành các bảng. JDBC là cách Java nói chuyện với MySQL:

```java
try (Connection con = DriverManager.getConnection(url, user, pass);
     PreparedStatement ps = con.prepareStatement("SELECT display_name FROM accounts WHERE username = ?")) {
    ps.setString(1, username);                       // điền giá trị vào dấu ?
    try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) { String name = rs.getString("display_name"); }
    }
}
```

**Vì sao dùng `?`**: tránh SQL injection (kẻ xấu nhập `' OR 1=1 --` vào ô tên đăng nhập để qua mặt). Không bao giờ nối chuỗi người dùng nhập vào câu SQL.

**Transaction**: nhiều câu lệnh phải "cùng thành công hoặc cùng thất bại" thì nằm trong một transaction. Ví dụ ghi kết quả thắng cho 2 người: hoặc cả hai được ghi, hoặc không ai. `setAutoCommit(false)`, `commit()` khi xong, `rollback()` khi có lỗi.

**Băm mật khẩu**: không bao giờ lưu mật khẩu thật. Lưu "dấu vân tay" của nó (hash) bằng BCrypt. BCrypt chậm một cách cố ý và tự thêm muối (salt) ngẫu nhiên, nên cùng một mật khẩu cho ra hash khác nhau mỗi lần. Vì vậy kiểm tra mật khẩu bằng hàm `verify`, không băm lại rồi so chuỗi.

## 10. Maven

Maven là công cụ build. File `pom.xml` mô tả: project tên gì, cần thư viện nào (Gson, MySQL connector, BCrypt), biên dịch bằng Java phiên bản nào. Bạn không phải tải thư viện bằng tay.

Dự án có 3 module:
- `common`: code dùng chung (giao thức và lõi game).
- `server`, `client`: phụ thuộc `common`.

Lệnh hay dùng: `mvn -q test` (chạy test), `mvn -q -DskipTests install` (build tất cả, cài `common` để `server` và `client` thấy nó).

Vì sao tách `common`: lõi game (`World`) cần chạy trên server. Nếu nó nằm lẫn trong client, có Swing, server sẽ không dùng được. Tách ra thành module sạch (không Swing, không DB, không socket) giúp kiểm thử dễ dàng.

## 11. Swing cơ bản

- `JFrame` là cửa sổ. `JPanel` là mảnh ghép bên trong. `CardLayout` cho phép xếp nhiều màn hình chồng lên nhau và chỉ hiện một cái (đăng nhập, sảnh, phòng, game).
- Vẽ game: ghi đè `paintComponent(Graphics g)` của một `JPanel`. Gọi `repaint()` để yêu cầu vẽ lại. Một `javax.swing.Timer` 16 ms gọi `repaint()` đều đặn cho ra khoảng 60 khung hình mỗi giây.
- **EDT**: mọi thao tác với thành phần Swing phải chạy trên luồng EDT. Từ luồng khác dùng `SwingUtilities.invokeLater(() -> ...)`.

## 12. Từ điển nhỏ

| Từ | Nghĩa |
|---|---|
| Tick | Một bước mô phỏng (1/60 giây) |
| Snapshot (SNAP) | Ảnh chụp toàn bộ trạng thái động của thế giới, server gửi cho client |
| Authoritative | Server giữ quyền quyết định cuối cùng |
| Input | Trạng thái phím (trái, phải, nhảy) |
| Handshake (bắt tay) | Bước trao đổi ban đầu để hai bên biết nhau (UDP_HELLO, UDP_ACK) |
| Token | Mã bí mật xác nhận danh tính trong phiên |
| Rider | Vật đứng trên một vật khác và đi theo nó |
| Hitbox | Hình chữ nhật dùng để tính va chạm |
| EDT | Luồng xử lý giao diện của Swing |
| Race condition | Lỗi do hai luồng chạy đua cùng sửa một dữ liệu |
| Deadlock | Hai luồng chờ nhau mãi |
| rid | Mã yêu cầu để ghép phản hồi với yêu cầu |
| Payload | Phần nội dung của một gói tin (`data`) |
| DAO | Lớp chuyên truy vấn một bảng trong DB |

## 13. Cách đọc log để tìm lỗi

Log là cuốn nhật ký của chương trình. Hãy đọc nó như đọc câu chuyện:
1. Mở log server. Tìm dòng của người chơi (`acc=5`) hoặc phòng (`R1`).
2. Xem trình tự: `TCP<` (nhận lệnh), `TCP>` (gửi phản hồi), `ROOM`, `UDP`.
3. Tìm dòng `WARN` hoặc `ERROR` đầu tiên trước khi lỗi xảy ra, vì lỗi đầu tiên thường là nguyên nhân, các lỗi sau chỉ là hệ quả.
4. So sánh với log client cùng thời điểm (giờ ghi ở đầu dòng).

## 14. Hiện tượng và nên đọc ở đâu

| Hiện tượng | Đọc |
|---|---|
| Lệnh gửi đi mà không có phản hồi | Mục 2.2 (dấu xuống dòng), `09-debug.md` mục 8 |
| Nhân vật đứng im dù bấm phím | Mục 2.5, 5, 6; `09-debug.md` mục 7 |
| Nhân vật giật | Mục 7 |
| Nhân vật xuyên tường | Mục 5, 8; `04-core-game.md` mục 5 |
| Đứng trên đầu nhau bị rơi | Mục 8 (rider); `04-core-game.md` mục 5.3 |
| Hai người chơi thấy hai cảnh khác nhau | Mục 6 |
| Phòng bị kẹt, không thoát được | Mục 4; `03-states-flows.md` |
| Cửa sổ client đơ | Mục 4.1, 11 (EDT) |
| Mật khẩu hay SQL có vấn đề | Mục 9 |
| Không biết bắt đầu code từ đâu | `10-ai-rules.md`, bắt đầu từ `common` (có test), làm sau cùng GUI |

## 15. Thứ tự đọc bộ tài liệu

1. `11-knowledge.md` (file này): hiểu bức tranh lớn.
2. `01-overview.md`: công nghệ, cấu trúc, quyết định kiến trúc.
3. `02-protocol.md` và `03-states-flows.md`: ai nói gì với ai, và khi nào.
4. `04-core-game.md` và `05-level.md`: luật chơi và vật lý.
5. `06-server.md`, `07-database.md`, `08-client.md`: chi tiết từng phần.
6. `09-debug.md`: khi gặp lỗi.
7. `10-ai-rules.md`: khi giao việc cho AI.
