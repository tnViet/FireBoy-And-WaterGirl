package fw.server.svc;

import fw.common.net.Err;
import fw.common.net.Msg;
import fw.common.net.MsgType;
import fw.common.util.Log;
import fw.server.db.AccountDao;
import fw.server.db.Pass;
import fw.server.net.ClientHandler;
import fw.server.net.Online;
import fw.server.net.Sess;

import com.google.gson.JsonObject;

import java.security.SecureRandom;
import java.sql.SQLIntegrityConstraintViolationException;

public final class AuthSvc {
    private static final SecureRandom random = new SecureRandom();

    private AuthSvc() {}

    /**
     * Đăng ký: kiểm tra định dạng, tên hiển thị không trùng, băm mật khẩu bằng bcrypt rồi tạo tài khoản.
     * Tên đăng nhập trùng được DB báo qua ràng buộc UNIQUE.
     */
    public static void register(ClientHandler h, Msg m) {
        String username = m.str("username");
        String password = m.str("password");
        // " An" và "An" là 1 tên
        String name = m.str("name").trim();

        // Kiểm tra định dạng tên đăng nhập, mật khẩu, tên hiển thị
        if (!isValidUsername(username)) {
            h.err(m, Err.BAD_INPUT, "username must be 3-20 chars, A-Z a-z 0-9 _");
            return;
        }
        if (!isValidPassword(password)) {
            h.err(m, Err.BAD_INPUT, "password must be 6-50 chars");
            return;
        }
        if (!isValidName(name)) {
            h.err(m, Err.BAD_INPUT, "name must be 1-20 chars, no control chars");
            return;
        }

        try {
            // Tên hiển thị dùng ở bảng xếp hạng, danh sách online, phòng chơi -> không cho trùng
            if (AccountDao.nameTaken(name)) {
                h.err(m, Err.NAME_EXISTS, "display name already exists");
                return;
            }
            String hash = Pass.hash(password);
            int acc = AccountDao.create(username, hash, name);

            JsonObject data = new JsonObject();
            h.reply(m, MsgType.REGISTER_SUCCESS, data);

            Log.info("AuthSvc", "register success", "username", username, "acc", acc);

        } catch (SQLIntegrityConstraintViolationException e) {
            h.err(m, Err.USER_EXISTS, "username already exists");
        } catch (Exception e) {
            Log.error("AuthSvc", "register failed", "username", username, "error", e.getMessage());
            h.err(m, Err.DB_ERROR, "database error");
        }
    }

    /**
     * Đăng nhập: so mật khẩu với bản băm, tạo phiên kèm token (dùng để xác thực UDP).
     * Mỗi tài khoản chỉ online 1 nơi. Thành công thì gửi các danh sách ban đầu và báo cho mọi người.
     */
    public static void login(ClientHandler h, Msg m) {
        String username = m.str("username");
        String password = m.str("password");

        try {
            AccountDao.Acc acc = AccountDao.find(username);

            if (acc == null || !Pass.check(password, acc.hash())) {
                h.err(m, Err.BAD_LOGIN, "invalid username or password");
                Log.info("AuthSvc", "login failed", "username", username, "reason", "bad_credentials");
                return;
            }

            String token = generateToken();
            Sess sess = new Sess(acc.id(), acc.name(), token, h);

            if (!Online.add(sess)) {
                h.err(m, Err.ALREADY_LOGGED_IN, "account already logged in");
                Log.info("AuthSvc", "login failed", "username", username, "reason", "already_online");
                return;
            }

            h.sess = sess;

            JsonObject data = new JsonObject();
            data.addProperty("acc", acc.id());
            data.addProperty("name", acc.name());
            data.addProperty("token", token);
            h.reply(m, MsgType.LOGIN_SUCCESS, data);

            // Gửi ngay cho người vừa đăng nhập: danh sách màn, danh sách online, danh sách phòng
            h.send(createLevelsMsg());

            h.send(Online.createOnlineList());

            h.send(Rooms.createRoomList());

            // Báo cho mọi người có thêm 1 người online
            Online.push();

            Log.info("AuthSvc", "login success", "username", username, "acc", acc.id());

        } catch (Exception e) {
            Log.error("AuthSvc", "login failed", "username", username, "error", e.getMessage());
            h.err(m, Err.DB_ERROR, "database error");
        }
    }

    // Đăng xuất: rời phòng nếu có, xóa phiên, báo lại danh sách online
    public static void logout(ClientHandler h, Msg m) {
        Sess sess = h.sess;
        if (sess == null) return;

        // Đang ở trong phòng thì rời phòng trước
        if (sess.room != null) {
            RoomSvc.leave(sess);
        }

        Online.remove(sess);
        h.sess = null;

        JsonObject data = new JsonObject();
        h.reply(m, MsgType.LOGOUT_SUCCESS, data);

        Online.push();

        Log.info("AuthSvc", "logout", "acc", sess.acc);
    }

    private static String generateToken() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean isValidUsername(String s) {
        if (s == null || s.length() < 3 || s.length() > 20) return false;
        return s.matches("[A-Za-z0-9_]+");
    }

    private static boolean isValidPassword(String s) {
        return s != null && s.length() >= 6 && s.length() <= 50;
    }

    private static boolean isValidName(String s) {
        if (s == null || s.length() < 1 || s.length() > 20) return false;
        for (char c : s.toCharArray()) {
            if (Character.isISOControl(c)) return false;
        }
        return true;
    }

    // Danh sách màn (id, tên) để client hiện trong phần chọn màn và bảng xếp hạng
    private static Msg createLevelsMsg() {
        JsonObject data = new JsonObject();
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();

        for (fw.server.db.LevelDao.LevelRow row : Levels.all()) {
            JsonObject lv = new JsonObject();
            lv.addProperty("lv", row.id());
            lv.addProperty("name", row.name());
            arr.add(lv);
        }

        data.add("levels", arr);
        return new Msg(MsgType.LEVELS, null, data);
    }
}
