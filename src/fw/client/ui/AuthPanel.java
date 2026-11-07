package fw.client.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Phần giao diện dùng chung cho màn hình Đăng nhập và Đăng ký:
 * ảnh nền núi, khung giấy da viền đinh gỗ ở giữa, ô nhập, nút và dòng trạng thái.
 */
abstract class AuthPanel extends JPanel {
    private static final Color TEXT_COLOR    = Theme.INK;

    protected final JPanel form;
    protected final JLabel statusLabel;
    private final GridBagConstraints gbc = new GridBagConstraints();
    private int row = 0;

    /**
     * Form đặt giữa màn hình trên khung giấy da: tiêu đề game, tên màn hình, đường kẻ;
     * các lớp con thêm ô nhập và nút bằng addField / addWide.
     */
    protected AuthPanel(String subtitle) {
        setLayout(new GridBagLayout());
        setOpaque(false);

        form = new Theme.ParchmentPanel(new GridBagLayout());
        // chừa viền đinh gỗ 16px
        form.setBorder(BorderFactory.createEmptyBorder(36, 48, 34, 48));
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Font Arial không có emoji -> tô màu chữ Fire/Water bằng HTML thay cho biểu tượng
        JLabel title = new JLabel("<html><span style='color:#cf573c'>Fireboy</span> &amp; "
                + "<span style='color:#2f7fc0'>Watergirl</span></html>", SwingConstants.CENTER);
        title.setFont(Theme.display(30));
        title.setForeground(Theme.INK);
        addWide(title, new Insets(4, 8, 2, 8));

        JLabel sub = new JLabel(subtitle, SwingConstants.CENTER);
        sub.setFont(Theme.display(17));
        sub.setForeground(Theme.INK_SOFT);
        addWide(sub, new Insets(2, 8, 10, 8));

        addWide(divider(), new Insets(0, 0, 12, 0));

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setForeground(Theme.INK_ERR);
        statusLabel.setFont(Theme.body(14));

        add(form);
    }

    /** Thêm 1 dòng "nhãn: ô nhập" vào form */
    protected void addField(String label, JComponent field) {
        gbc.gridwidth = 1;
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0; gbc.gridy = row;
        JLabel l = new JLabel(label);
        l.setForeground(TEXT_COLOR);
        l.setFont(Theme.bodyBold(14));
        // Swing đo thiếu bề rộng chữ tiếng Việt có dấu vài pixel -> cho cột nhãn bề rộng cố định
        l.setPreferredSize(new Dimension(150, l.getPreferredSize().height));
        form.add(l, gbc);
        gbc.gridx = 1;
        form.add(field, gbc);
        row++;
    }

    /** Thêm 1 thành phần chiếm cả bề ngang form */
    protected void addWide(JComponent c, Insets insets) {
        gbc.gridx = 0; gbc.gridy = row++;
        gbc.gridwidth = 2;
        gbc.insets = insets;
        gbc.anchor = GridBagConstraints.CENTER;
        form.add(c, gbc);
    }

    /** Thêm dòng trạng thái (gọi sau cùng khi dựng form) */
    protected void addStatus() {
        addWide(statusLabel, new Insets(8, 8, 0, 8));
    }

    protected static JTextField textField() {
        return new Theme.PixelField(18);
    }

    protected static JPasswordField passwordField() {
        return new Theme.PixelPassword(18);
    }

    /** Đường kẻ ngang màu mực, có điểm nhấn hình thoi ở giữa */
    private static JComponent divider() {
        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                int w = getWidth(), cy = getHeight() / 2, cx = w / 2;
                g.setColor(Theme.alpha(Theme.INK_SOFT, 150));
                g.fillRect(0, cy - 1, cx - 10, 2);
                g.fillRect(cx + 10, cy - 1, w - cx - 10, 2);
                g.setColor(Theme.RUST);
                g.fillPolygon(new int[]{cx - 6, cx, cx + 6, cx}, new int[]{cy, cy - 6, cy, cy + 6}, 4);
            }
        };
        c.setPreferredSize(new Dimension(10, 14));
        return c;
    }

    /** Nút chính: dùng nút đá của Theme để đồng bộ với các màn hình khác */
    protected static JButton button(String text, Theme.Kind kind) {
        Theme.StoneButton b = Theme.button(text, kind);
        b.setPreferredSize(new Dimension(240, 48));
        return b;
    }

    /** Nút dạng đường link để chuyển qua lại giữa 2 màn hình */
    protected static JButton link(String text) {
        JButton b = new JButton("<html><u>" + text + "</u></html>");
        b.setForeground(Theme.INK);
        b.setFont(Theme.bodyBold(14));
        b.setRolloverEnabled(true);
        b.addChangeListener(e -> b.setForeground(b.getModel().isRollover() ? Theme.RUST : Theme.INK));
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public void showStatus(String msg) {
        statusLabel.setText(wrap(msg));
    }

    /** Hiện thông báo lỗi màu cam / thành công màu xanh */
    public void showStatus(String msg, boolean ok) {
        statusLabel.setForeground(ok ? Theme.INK_OK : Theme.INK_ERR);
        statusLabel.setText(wrap(msg));
    }

    /** Bọc HTML có bề rộng cố định để câu dài tự xuống dòng thay vì bị cắt */
    private static String wrap(String msg) {
        if (msg == null || msg.isBlank()) return " ";
        String safe = msg.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><div style='width:300px;text-align:center'>" + safe + "</div></html>";
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Nền núi nhiều lớp của Lost Lamb, phủ tối nhẹ cho form nổi rõ
        Graphics2D g2 = (Graphics2D) g;
        g2.drawImage(fw.client.render.Gfx.background(fw.client.render.Gfx.MOUNTAINS, getWidth(), getHeight()), 0, 0, null);
        g2.setColor(new Color(26, 16, 24, 90));
        g2.fillRect(0, 0, getWidth(), getHeight());
    }
}
