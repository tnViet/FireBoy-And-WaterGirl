package fw.client.ui;

import fw.client.render.Gfx;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

/**
 * Thanh tiêu đề tự vẽ theo bộ UI Lost Lamb, thay cho thanh tiêu đề của Windows.
 *  - Kéo thanh để di chuyển cửa sổ, bấm đúp để phóng to / thu về.
 *  - Nút: thu nhỏ, phóng to, đóng (hộp thoại chỉ có nút đóng).
 *  - Viền mỏng quanh cửa sổ chính dùng để kéo đổi kích thước.
 * Gọi install(...) trước khi cửa sổ hiện lên (setUndecorated chỉ dùng được lúc đó).
 */
public final class WindowChrome {
    private WindowChrome() {}

    private static final int BAR_H = 36;
    // bề dày viền kéo đổi kích thước
    private static final int EDGE = 5;
    private static final Color BAR      = new Color(0x2D1927);
    private static final Color BAR_LINE = new Color(0x5B4754);
    private static final Color OUTLINE  = new Color(0x090A14);

    /** Cửa sổ chính: đủ 3 nút và đổi được kích thước */
    public static void install(JFrame f) {
        f.setUndecorated(true);
        wrap(f, f, f.getTitle(), true);
    }

    /** Hộp thoại: chỉ nút đóng, kích thước cố định */
    public static void install(JDialog d) {
        d.setUndecorated(true);
        wrap(d, d, d.getTitle(), false);
    }

    /** Bọc nội dung hiện tại của cửa sổ: thanh tiêu đề ở trên, nội dung ở giữa, viền tối bao quanh */
    private static void wrap(Window w, RootPaneContainer rpc, String title, boolean main) {
        Container old = rpc.getContentPane();
        JPanel frame = new JPanel(new BorderLayout());
        frame.setBackground(OUTLINE);
        frame.setBorder(new EmptyBorder(main ? EDGE : 2, main ? EDGE : 2, main ? EDGE : 2, main ? EDGE : 2));
        frame.add(new TitleBar(w, title, main), BorderLayout.NORTH);
        frame.add(old, BorderLayout.CENTER);
        rpc.setContentPane(frame);
        if (main) {
            Resizer r = new Resizer(w, frame);
            frame.addMouseListener(r);
            frame.addMouseMotionListener(r);
        }
    }

    // ── Thanh tiêu đề ───────────────────────────────────────────────────

    private static final class TitleBar extends JPanel {
        private final Window w;
        private final String title;
        // chỗ bấm chuột trên thanh, tính từ góc cửa sổ
        private Point grab;
        // kích thước trước khi phóng to
        private Rectangle normalBounds;
        private final ChromeButton maxBtn;

        // Các nút xếp bên phải; kéo chuột trên thanh để di chuyển cửa sổ, bấm đúp để phóng to
        TitleBar(Window w, String title, boolean main) {
            super(new FlowLayout(FlowLayout.RIGHT, 4, 2));
            this.w = w;
            this.title = title;
            setOpaque(false);
            setPreferredSize(new Dimension(0, BAR_H));

            if (main) {
                ChromeButton min = new ChromeButton(Gfx.uiIcon(7, 4), "Thu nhỏ");
                min.addActionListener(e -> ((Frame) w).setExtendedState(Frame.ICONIFIED));
                add(min);
                maxBtn = new ChromeButton(Gfx.uiIcon(4, 2), "Phóng to");
                maxBtn.addActionListener(e -> toggleMax());
                add(maxBtn);
            } else {
                maxBtn = null;
            }
            ChromeButton close = new ChromeButton(Gfx.uiIcon(0, 0), "Đóng");
            // Gửi sự kiện đóng như nút X của Windows: cửa sổ chính thoát, hộp thoại tự ẩn
            close.addActionListener(e -> w.dispatchEvent(new WindowEvent(w, WindowEvent.WINDOW_CLOSING)));
            add(close);

            MouseAdapter drag = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    grab = SwingUtilities.convertPoint(TitleBar.this, e.getPoint(), w);
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (grab == null) return;
                    Point p = e.getLocationOnScreen();
                    if (normalBounds != null) {
                        // Đang phóng to mà kéo: thu về kích thước cũ, giữ chuột ở cùng tỉ lệ trên thanh
                        float ratio = grab.x / (float) Math.max(1, w.getWidth());
                        Rectangle nb = normalBounds;
                        restore();
                        grab = new Point(Math.round(ratio * nb.width), grab.y);
                    }
                    w.setLocation(p.x - grab.x, p.y - grab.y);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    grab = null;
                }
                @Override public void mouseClicked(MouseEvent e) {
                    if (main && e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) toggleMax();
                }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
        }

        /**
         * Tự phóng to bằng vùng làm việc của màn hình (trừ thanh taskbar) thay vì MAXIMIZED_BOTH:
         * cửa sổ không viền phóng to kiểu đó sẽ che mất taskbar.
         */
        private void toggleMax() {
            if (normalBounds != null) {
                restore();
                return;
            }
            GraphicsConfiguration gc = w.getGraphicsConfiguration();
            Rectangle screen = gc.getBounds();
            Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            normalBounds = w.getBounds();
            w.setBounds(screen.x + in.left, screen.y + in.top,
                        screen.width - in.left - in.right, screen.height - in.top - in.bottom);
            maxBtn.setIcon(Gfx.uiIcon(3, 2), "Thu về");
        }

        private void restore() {
            if (normalBounds == null) return;
            w.setBounds(normalBounds);
            normalBounds = null;
            maxBtn.setIcon(Gfx.uiIcon(4, 2), "Phóng to");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w0 = getWidth(), h = getHeight();
            g2.setColor(BAR);
            g2.fillRect(0, 0, w0, h);
            g2.setColor(BAR_LINE);
            g2.fillRect(0, h - 3, w0, 1);
            g2.setColor(OUTLINE);
            g2.fillRect(0, h - 2, w0, 2);

            // Chìa khóa nhỏ làm biểu tượng game, rồi tới tên cửa sổ
            BufferedImage key = Gfx.img("key.png");
            int tx = 12;
            if (key != null) {
                Gfx.draw(g2, key, tx, (h - 2 - 20) / 2, 6, 20);
                tx += 16;
            }
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(Theme.display(15));
            FontMetrics fm = g2.getFontMetrics();
            int ty = (h - 2 - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(new Color(0, 0, 0, 140));
            g2.drawString(title, tx + 1, ty + 1);
            g2.setColor(Theme.GILT);
            g2.drawString(title, tx, ty);
            g2.dispose();
        }
    }

    /** Nút trên thanh tiêu đề: icon 16px của UI.png phóng 2 lần, sáng lên khi rê chuột */
    private static final class ChromeButton extends JButton {
        private BufferedImage icon;

        ChromeButton(BufferedImage icon, String tip) {
            this.icon = icon;
            setToolTipText(tip);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            // không giành bàn phím của màn chơi
            setFocusable(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(32, 32));
        }

        void setIcon(BufferedImage icon, String tip) {
            this.icon = icon;
            setToolTipText(tip);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int off = getModel().isPressed() ? 1 : 0;
            Gfx.draw(g2, icon, off, off, 32, 32);
            if (getModel().isRollover()) {
                g2.setColor(new Color(255, 255, 255, 40));
                g2.fillRect(4 + off, 4 + off, 24, 24);
            }
            g2.dispose();
        }
    }

    // ── Kéo viền để đổi kích thước ──────────────────────────────────────

    private static final class Resizer extends MouseAdapter {
        private final Window w;
        private final JComponent edgeArea;
        // tổ hợp bit: 1 trái, 2 phải, 4 trên, 8 dưới
        private int edge;
        private Point start;
        private Rectangle startBounds;

        Resizer(Window w, JComponent edgeArea) {
            this.w = w;
            this.edgeArea = edgeArea;
        }

        private int edgeAt(Point p) {
            // góc dễ bắt hơn một chút
            int e = 0, m = EDGE + 3;
            if (p.x < m) e |= 1;
            if (p.x >= edgeArea.getWidth() - m) e |= 2;
            if (p.y < m) e |= 4;
            if (p.y >= edgeArea.getHeight() - m) e |= 8;
            return e;
        }

        private static int cursorFor(int e) {
            return switch (e) {
                case 1 -> Cursor.W_RESIZE_CURSOR;
                case 2 -> Cursor.E_RESIZE_CURSOR;
                case 4 -> Cursor.N_RESIZE_CURSOR;
                case 8 -> Cursor.S_RESIZE_CURSOR;
                case 5 -> Cursor.NW_RESIZE_CURSOR;
                case 6 -> Cursor.NE_RESIZE_CURSOR;
                case 9 -> Cursor.SW_RESIZE_CURSOR;
                case 10 -> Cursor.SE_RESIZE_CURSOR;
                default -> Cursor.DEFAULT_CURSOR;
            };
        }

        @Override public void mouseMoved(MouseEvent e) {
            edgeArea.setCursor(Cursor.getPredefinedCursor(cursorFor(edgeAt(e.getPoint()))));
        }

        @Override public void mouseExited(MouseEvent e) {
            if (start == null) edgeArea.setCursor(Cursor.getDefaultCursor());
        }

        @Override public void mousePressed(MouseEvent e) {
            edge = edgeAt(e.getPoint());
            start = e.getLocationOnScreen();
            startBounds = w.getBounds();
        }

        @Override public void mouseReleased(MouseEvent e) {
            start = null;
        }

        @Override public void mouseDragged(MouseEvent e) {
            if (start == null || edge == 0) return;
            Point p = e.getLocationOnScreen();
            int dx = p.x - start.x, dy = p.y - start.y;
            Dimension min = w.getMinimumSize();
            Rectangle r = new Rectangle(startBounds);
            if ((edge & 2) != 0) r.width = Math.max(min.width, startBounds.width + dx);
            if ((edge & 8) != 0) r.height = Math.max(min.height, startBounds.height + dy);
            if ((edge & 1) != 0) {
                r.width = Math.max(min.width, startBounds.width - dx);
                r.x = startBounds.x + startBounds.width - r.width;
            }
            if ((edge & 4) != 0) {
                r.height = Math.max(min.height, startBounds.height - dy);
                r.y = startBounds.y + startBounds.height - r.height;
            }
            w.setBounds(r);
            w.validate();
        }
    }
}
