package fw.client.ui;

import fw.client.render.Gfx;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

/**
 * Hộp thông báo / hỏi xác nhận tự vẽ, thay cho JOptionPane của Windows.
 * Khung giấy da của UI.png, biểu tượng lấy từ lưới icon, nút theo Theme, thanh tiêu đề WindowChrome.
 * Hộp là modal: hàm chỉ trả về khi người dùng đã bấm nút (giống JOptionPane).
 */
public final class PixelDialog {
    private PixelDialog() {}

    public enum Kind { INFO, WARNING, ERROR, QUESTION }

    /** Thông báo có 1 nút "Đã hiểu" */
    public static void message(Component parent, String title, String text, Kind kind) {
        show(parent, title, text, kind, "Đã hiểu", null);
    }

    /** Hỏi Có / Không; true nếu người dùng bấm Có */
    public static boolean confirm(Component parent, String title, String text) {
        return show(parent, title, text, Kind.QUESTION, "Có", "Không");
    }

    // Dựng hộp thoại: biểu tượng bên trái, lời nhắn ở giữa, các nút ở dưới; chờ người dùng bấm rồi trả kết quả
    private static boolean show(Component parent, String title, String text, Kind kind, String yes, String no) {
        Window owner = parent == null ? null
            : parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        boolean[] result = {false};

        Theme.ParchmentPanel body = new Theme.ParchmentPanel(new BorderLayout(18, 18));
        // chừa viền đinh gỗ 16px
        body.setBorder(new EmptyBorder(30, 30, 28, 30));

        JLabel icon = new JLabel(new ImageIcon(scaled(iconOf(kind))));
        icon.setVerticalAlignment(SwingConstants.TOP);
        body.add(icon, BorderLayout.WEST);

        String safe = text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                                              .replace("\n", "<br>");
        JLabel msg = new JLabel("<html><div style='width:280px'>" + safe + "</div></html>");
        msg.setFont(Theme.body(15));
        msg.setForeground(Theme.INK);
        body.add(msg, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        Theme.StoneButton yesBtn = Theme.button(yes, Theme.Kind.PRIMARY);
        yesBtn.setFont(Theme.bodyBold(15));
        yesBtn.setPreferredSize(new Dimension(130, 44));
        yesBtn.addActionListener(e -> { result[0] = true; d.dispose(); });
        if (no != null) {
            Theme.StoneButton noBtn = Theme.button(no, Theme.Kind.STONE);
            noBtn.setPreferredSize(new Dimension(130, 44));
            noBtn.addActionListener(e -> d.dispose());
            buttons.add(noBtn);
        }
        buttons.add(yesBtn);
        body.add(buttons, BorderLayout.SOUTH);

        d.setContentPane(body);
        WindowChrome.install(d);
        // Enter = nút chính
        d.getRootPane().setDefaultButton(yesBtn);
        // Esc = đóng như bấm "Không" (hoặc đóng thông báo)
        d.getRootPane().registerKeyboardAction((ActionEvent e) -> d.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.pack();
        d.setLocationRelativeTo(owner);
        // modal: chờ tới khi đóng
        d.setVisible(true);
        return result[0];
    }

    private static BufferedImage iconOf(Kind kind) {
        return switch (kind) {
            // chữ i
            case INFO     -> Gfx.uiIcon(4, 4);
            // dấu chấm than
            case WARNING  -> Gfx.uiIcon(5, 4);
            // dấu X đỏ
            case ERROR    -> Gfx.uiIcon(0, 0);
            // dấu hỏi
            case QUESTION -> Gfx.uiIcon(9, 5);
        };
    }

    /** Icon 16px phóng 3 lần, giữ nét pixel */
    private static Image scaled(BufferedImage src) {
        int s = 48;
        BufferedImage out = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        if (src != null) {
            Graphics2D g = out.createGraphics();
            Gfx.draw(g, src, 0, 0, s, s);
            g.dispose();
        }
        return out;
    }
}
