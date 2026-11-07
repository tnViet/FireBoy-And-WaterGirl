package fw.client.ui;

import fw.common.net.MsgType;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;

/** Màn hình Đăng nhập: username + mật khẩu. Có link sang màn hình Đăng ký. */
public class LoginScreen extends AuthPanel {
    private final JTextField usernameField = textField();
    private final JPasswordField passwordField = passwordField();
    private final JButton loginButton = button("Đăng nhập", Theme.Kind.PRIMARY);
    private final JButton toRegister = link("Chưa có tài khoản? Đăng ký");

    /** ClientApp gán: chuyển sang màn hình Đăng ký */
    public Runnable onGoRegister;

    public LoginScreen() {
        super("Đăng nhập");

        addField("Tên đăng nhập:", usernameField);
        addField("Mật khẩu:", passwordField);
        addWide(loginButton, new Insets(14, 8, 4, 8));
        addWide(toRegister, new Insets(4, 8, 0, 8));
        addStatus();

        loginButton.addActionListener(e -> login());
        // Enter ở ô mật khẩu = đăng nhập
        passwordField.addActionListener(e -> login());
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow());
        toRegister.addActionListener(e -> { if (onGoRegister != null) onGoRegister.run(); });
    }

    // Gửi LOGIN; nếu đang mất kết nối thì thử kết nối lại trước. Khóa form tới khi có kết quả.
    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            showStatus("Nhập tên đăng nhập và mật khẩu", false);
            return;
        }
        if (ClientApp.tcp.isClosed() && !ClientApp.reconnect()) return;

        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        data.addProperty("password", password);
        ClientApp.tcp.send(MsgType.LOGIN, data);
        setEnabled(false);
        showStatus("Đang đăng nhập...", true);
    }

    /** Sau khi đăng ký xong: điền sẵn tên đăng nhập, xóa mật khẩu cũ */
    public void prefill(String username) {
        usernameField.setText(username);
        passwordField.setText("");
        passwordField.requestFocusInWindow();
    }

    /** Khi đăng xuất / mất kết nối: xóa mật khẩu */
    public void clearPassword() {
        passwordField.setText("");
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
        toRegister.setEnabled(enabled);
    }
}
