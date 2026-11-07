package fw.client.ui;

import fw.common.net.MsgType;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;

/**
 * Màn hình Đăng ký: tên đăng nhập, tên hiển thị, mật khẩu, nhập lại mật khẩu.
 * Kiểm tra sơ bộ ở client cho nhanh (cùng luật với server), server vẫn kiểm tra lại lần nữa.
 */
public class RegisterScreen extends AuthPanel {
    private final JTextField usernameField = textField();
    private final JTextField nameField = textField();
    private final JPasswordField passwordField = passwordField();
    private final JPasswordField confirmField = passwordField();
    private final JButton registerButton = button("Tạo tài khoản", Theme.Kind.PRIMARY);
    private final JButton toLogin = link("Đã có tài khoản? Đăng nhập");

    /** ClientApp gán: quay lại màn hình Đăng nhập */
    public Runnable onGoLogin;

    public RegisterScreen() {
        super("Tạo tài khoản mới");

        addField("Tên đăng nhập:", usernameField);
        addField("Tên hiển thị:", nameField);
        addField("Mật khẩu:", passwordField);
        addField("Nhập lại mật khẩu:", confirmField);

        // Ngắt dòng sẵn: Consolas rộng hơn font thường, câu dài sẽ bị cắt
        JLabel hint = new JLabel("<html><center>"
                + "Tên đăng nhập 3-20 ký tự (chữ, số, _)<br>"
                + "Mật khẩu 6-50 ký tự<br>"
                + "Tên hiển thị là tên người khác nhìn thấy,<br>không được trùng</center></html>",
                SwingConstants.CENTER);
        hint.setForeground(Theme.INK_SOFT);
        hint.setFont(Theme.body(12));
        addWide(hint, new Insets(4, 8, 4, 8));

        addWide(registerButton, new Insets(10, 8, 4, 8));
        addWide(toLogin, new Insets(4, 8, 0, 8));
        addStatus();

        registerButton.addActionListener(e -> register());
        confirmField.addActionListener(e -> register());
        toLogin.addActionListener(e -> { if (onGoLogin != null) onGoLogin.run(); });
    }

    // Kiểm tra sơ bộ ở client (đủ thông tin, 2 lần mật khẩu khớp) rồi gửi REGISTER; server kiểm tra lại đầy đủ
    private void register() {
        String username = usernameField.getText().trim();
        String name = nameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirm = new String(confirmField.getPassword());

        String error = validate(username, name, password, confirm);
        if (error != null) {
            showStatus(error, false);
            return;
        }
        if (ClientApp.tcp.isClosed() && !ClientApp.reconnect()) return;

        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        data.addProperty("password", password);
        data.addProperty("name", name);
        ClientApp.tcp.send(MsgType.REGISTER, data);
        setEnabled(false);
        showStatus("Đang đăng ký...", true);
    }

    /** Cùng luật với AuthSvc ở server; trả về lỗi hoặc null nếu hợp lệ */
    private static String validate(String username, String name, String password, String confirm) {
        if (!username.matches("[A-Za-z0-9_]{3,20}")) return "Tên đăng nhập phải 3-20 ký tự, chỉ gồm chữ, số, _";
        if (name.isEmpty() || name.length() > 20)   return "Tên hiển thị phải 1-20 ký tự";
        if (password.length() < 6 || password.length() > 50) return "Mật khẩu phải 6-50 ký tự";
        if (!password.equals(confirm))             return "Mật khẩu nhập lại không khớp";
        return null;
    }

    public String username() {
        return usernameField.getText().trim();
    }

    /** Đăng ký xong thì xóa form để lần sau vào lại không còn dữ liệu cũ */
    public void clear() {
        usernameField.setText("");
        nameField.setText("");
        passwordField.setText("");
        confirmField.setText("");
        showStatus(" ");
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        usernameField.setEnabled(enabled);
        nameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        confirmField.setEnabled(enabled);
        registerButton.setEnabled(enabled);
        toLogin.setEnabled(enabled);
    }
}
