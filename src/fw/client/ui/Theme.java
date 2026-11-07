package fw.client.ui;

import fw.client.render.Gfx;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;

/**
 * Bộ giao diện chung "đền đá cổ": bảng màu, font và các thành phần đã tạo kiểu sẵn.
 * Mọi màn hình chỉ lấy từ đây, nên đổi màu/font ở 1 chỗ là cả game đổi theo.
 */
public final class Theme {
    private Theme() {}

    // ── Bảng màu ────────────────────────────────────────────────────────
    // Lấy theo bảng màu bộ UI Lost Lamb (tím than, xám tím, kem, cam)
    // nền tối
    public static final Color NIGHT     = new Color(0x1A1018);
    // mặt khung/nút
    public static final Color STONE     = new Color(0x2D1927);
    // viền sáng của khung
    public static final Color STONE_LIT = new Color(0x5B4754);
    // nền ô nhập, bảng
    public static final Color STONE_DK  = new Color(0x1E1019);
    // điểm nhấn, tiêu đề
    public static final Color GILT      = new Color(0xF0B866);
    // chữ chính
    public static final Color PARCHMENT = new Color(0xE7D5B3);
    // chữ phụ
    public static final Color MUTED     = new Color(0xA8939E);
    // chỉ dùng cho nhân vật Lửa
    public static final Color FIRE      = new Color(0xF26B2A);
    // chỉ dùng cho nhân vật Nước
    public static final Color WATER     = new Color(0x3AA6F0);
    // thông báo thành công
    public static final Color MOSS      = new Color(0x8DB255);
    public static final Color RUST      = new Color(0xCF573C);
    // Chữ trên nền giấy da (khung sáng)
    // chữ chính
    public static final Color INK       = new Color(0x2D1927);
    // chữ phụ
    public static final Color INK_SOFT  = new Color(0x6B4A52);
    // báo thành công
    public static final Color INK_OK    = new Color(0x3F7A2A);
    // báo lỗi // nút thoát / lỗi
    public static final Color INK_ERR   = new Color(0xB03A22);

    // ── Font ────────────────────────────────────────────────────────────
    /**
     * Một họ font cho cả game: Consolas (chữ đều nét, hợp với đồ họa pixel). Lấy font đầu tiên
     * trên máy hiển thị đủ tiếng Việt có dấu, lần lượt Consolas, Cascadia Mono, Courier New.
     */
    public static final String FAMILY = pickFamily(
        new String[]{"Consolas", "Cascadia Mono", "Courier New", "Segoe UI"}, "Mở cửa Thất bại Hoàn thành Người chơi ữ ặ ợ");

    /** Chữ tiêu đề: cùng họ font, in đậm */
    public static Font display(float size) {
        return new Font(FAMILY, Font.BOLD, Math.round(size));
    }

    private static String pickFamily(String[] families, String sample) {
        for (String f : families) {
            Font font = new Font(f, Font.BOLD, 16);
            if (font.getFamily().equalsIgnoreCase(f) && font.canDisplayUpTo(sample) == -1) return f;
        }
        return "Monospaced";
    }

    /**
     * Đặt font mặc định của Swing sang họ font của game (cho các nhãn, ô, tooltip chưa đặt font riêng)
     * và tô tooltip theo bảng màu. Gọi 1 lần trước khi tạo giao diện.
     */
    public static void installDefaults() {
        javax.swing.plaf.FontUIResource f = new javax.swing.plaf.FontUIResource(body(14));
        for (Object k : java.util.Collections.list(UIManager.getDefaults().keys())) {
            if (k instanceof String s && s.endsWith(".font")) UIManager.put(k, f);
        }
        UIManager.put("ToolTip.background", STONE);
        UIManager.put("ToolTip.foreground", PARCHMENT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(STONE_LIT));
    }

    /** Chữ nội dung */
    public static Font body(float size) {
        return new Font(FAMILY, Font.PLAIN, Math.round(size));
    }

    public static Font bodyBold(float size) {
        return new Font(FAMILY, Font.BOLD, Math.round(size));
    }

    public static Color elementColor(String el) {
        return "WATER".equals(el) ? WATER : FIRE;
    }

    // ── Nền hang đá ─────────────────────────────────────────────────────

    /** Panel nền: cảnh rừng nhiều lớp của Lost Lamb, phủ tối nhẹ và tối dần ở mép (vignette) */
    public static class TempleBackground extends JPanel {
        public TempleBackground(LayoutManager layout) {
            super(layout);
            setOpaque(true);
            setBackground(NIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.drawImage(Gfx.background(Gfx.FOREST, getWidth(), getHeight()), 0, 0, null);
            // phủ tối để chữ và khung nổi rõ
            g2.setColor(new Color(26, 16, 24, 120));
            g2.fillRect(0, 0, getWidth(), getHeight());
            float r = Math.max(getWidth(), getHeight()) * 0.75f;
            g2.setPaint(new RadialGradientPaint(getWidth() / 2f, getHeight() / 2f, Math.max(1f, r),
                new float[]{0.4f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)}));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    // ── Khung đá ────────────────────────────────────────────────────────

    /**
     * Khung đá vát cạnh, có tiêu đề (tùy chọn). Thêm nội dung vào {@link #content()}.
     */
    public static class StonePanel extends JPanel {
        private final JPanel content = new JPanel(new BorderLayout());
        private final Color tint;

        public StonePanel(String title) {
            this(title, null);
        }

        /** tint: ánh màu riêng cho khung (dùng cho thẻ người chơi Lửa/Nước), null = đá thường */
        public StonePanel(String title, Color tint) {
            super(new BorderLayout(0, 8));
            this.tint = tint;
            setOpaque(false);
            setBorder(new EmptyBorder(12, 14, 14, 14));
            if (title != null) {
                JLabel t = new JLabel(title);
                t.setFont(display(17));
                t.setForeground(tint != null ? tint : GILT);
                add(t, BorderLayout.NORTH);
            }
            content.setOpaque(false);
            add(content, BorderLayout.CENTER);
        }

        public JPanel content() {
            return content;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            // Khung tối 9 mảnh của UI.png, phóng 2 lần cho đúng chất pixel art
            Gfx.nine(g2, Gfx.uiPanel(), 5, 2, 0, 0, w, h);
            // thẻ người chơi: viền trong theo màu nhân vật
            if (tint != null) {
                g2.setColor(alpha(tint, 170));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(7, 7, w - 15, h - 15);
            }
            g2.dispose();
        }
    }

    // ── Khung giấy da ───────────────────────────────────────────────────

    /**
     * Khung giấy da viền đinh gỗ của UI.png (phóng 2 lần, viền lặp lại).
     * Kích thước ưu tiên làm tròn lên bội số 16px để hàng đinh khép kín ở góc.
     */
    public static class ParchmentPanel extends JPanel {
        public ParchmentPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension((d.width + 15) / 16 * 16, (d.height + 15) / 16 * 16);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Gfx.nineTiled(g2, Gfx.uiParchment(), 12, 2, 0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    // ── Nút đá ──────────────────────────────────────────────────────────

    public enum Kind { PRIMARY, STONE, DANGER, FIRE, WATER }

    /** Nút đá vát cạnh. PRIMARY = tấm bia vàng (hành động chính của màn hình). */
    public static class StoneButton extends JButton {
        private Kind kind;

        public StoneButton(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setFont(bodyBold(kind == Kind.PRIMARY ? 17 : 15));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(190, kind == Kind.PRIMARY ? 56 : 44));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
            setAlignmentX(Component.CENTER_ALIGNMENT);
        }

        /** Đổi kiểu nút (ví dụ tab đang chọn chuyển sang màu vàng) */
        public void setKind(Kind kind) {
            this.kind = kind;
            repaint();
        }

        private Color face() {
            return switch (kind) {
                case PRIMARY -> GILT;
                case DANGER  -> RUST;
                case FIRE    -> FIRE.darker();
                case WATER   -> WATER.darker();
                default      -> STONE_LIT;
            };
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            ButtonModel m = getModel();
            int w = getWidth(), h = getHeight();
            int press = m.isPressed() ? 2 : 0;

            // Nút cam của UI.png cho hành động chính / thoát, nút tối cho phần còn lại
            boolean orange = kind == Kind.PRIMARY || kind == Kind.DANGER;
            Gfx.nine(g2, orange ? Gfx.uiButtonMain() : Gfx.uiButton(), 4, 2, 0, press, w, h - press);
            Color overlay = switch (kind) {
                case FIRE   -> alpha(FIRE, 110);
                case WATER  -> alpha(WATER, 110);
                case DANGER -> alpha(new Color(0x7A1E1E), 110);
                default     -> null;
            };
            if (overlay != null) { g2.setColor(overlay); g2.fillRect(6, press + 6, w - 12, h - press - 14); }
            if (!isEnabled()) { g2.setColor(alpha(NIGHT, 150)); g2.fillRect(0, press, w, h - press); }
            else if (m.isRollover()) { g2.setColor(alpha(Color.WHITE, 28)); g2.fillRect(4, press + 4, w - 8, h - press - 10); }

            // Chữ: căn giữa phần mặt nút (trừ gờ bóng 4px ở đáy)
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String text = getText();
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = press + (h - press - 4 - fm.getHeight()) / 2 + fm.getAscent();
            Color fg = orange ? new Color(0xFFF1DA) : PARCHMENT;
            g2.setColor(new Color(0, 0, 0, 120));
            g2.drawString(text, tx + 1, ty + 1);
            g2.setColor(isEnabled() ? fg : alpha(fg, 110));
            g2.drawString(text, tx, ty);
            g2.dispose();
        }
    }

    public static StoneButton button(String text, Kind kind) {
        return new StoneButton(text, kind);
    }

    // ── Nhãn, ô nhập ────────────────────────────────────────────────────

    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        // Swing đo thiếu bề rộng chữ tiếng Việt có dấu vài pixel -> chừa thêm để chữ cuối không bị cắt
        l.setBorder(new EmptyBorder(0, 0, 0, 8));
        return l;
    }

    public static JTextField field() {
        return new PixelField();
    }

    /** Ô nhập: nền là khung tối lõm của UI.png (vẽ 9 mảnh, phóng 2 lần), chữ kem */
    public static class PixelField extends JTextField {
        public PixelField() { this(0); }
        public PixelField(int cols) {
            super(cols);
            styleField(this);
        }
        @Override protected void paintComponent(Graphics g) {
            Gfx.nine((Graphics2D) g, Gfx.uiInset(), 5, 2, 0, 0, getWidth(), getHeight());
            super.paintComponent(g);
        }
    }

    /** Ô mật khẩu cùng kiểu với PixelField */
    public static class PixelPassword extends JPasswordField {
        public PixelPassword(int cols) {
            super(cols);
            styleField(this);
        }
        @Override protected void paintComponent(Graphics g) {
            Gfx.nine((Graphics2D) g, Gfx.uiInset(), 5, 2, 0, 0, getWidth(), getHeight());
            super.paintComponent(g);
        }
    }

    private static void styleField(JTextField f) {
        // nền do paintComponent vẽ
        f.setOpaque(false);
        f.setForeground(PARCHMENT);
        f.setCaretColor(GILT);
        f.setSelectionColor(alpha(RUST, 160));
        f.setSelectedTextColor(Color.WHITE);
        f.setDisabledTextColor(MUTED);
        f.setFont(body(15));
        f.setBorder(new EmptyBorder(9, 12, 9, 12));
    }

    // ── Ô chọn (combo box) ──────────────────────────────────────────────

    /**
     * Tạo kiểu cho ô chọn: mặt nút tối của UI.png, mũi tên xuống lấy từ lưới icon,
     * danh sách thả xuống nền tối, mục đang chọn ánh cam. Trả về chính ô chọn đó.
     */
    public static <T> JComboBox<T> combo(JComboBox<T> c) {
        c.setFont(body(14));
        c.setForeground(PARCHMENT);
        c.setBackground(STONE_DK);
        c.setOpaque(false);
        c.setFocusable(false);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // chừa gờ bóng 4px ở đáy mặt nút
        c.setBorder(new EmptyBorder(0, 6, 4, 4));
        c.setPreferredSize(new Dimension(c.getPreferredSize().width, 42));
        c.setUI(new BasicComboBoxUI() {
            @Override public void paint(Graphics g, JComponent comp) {
                Gfx.nine((Graphics2D) g, Gfx.uiButton(), 4, 2, 0, 0, comp.getWidth(), comp.getHeight());
                super.paint(g, comp);
            }

            @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                // trong suốt: để lộ mặt nút đã vẽ ở paint()
            }

            @Override protected JButton createArrowButton() {
                JButton b = new JButton() {
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        // icon 16px phóng 2 lần
                        int s = 32;
                        Gfx.draw(g2, Gfx.uiIcon(2, 1), (getWidth() - s) / 2, (getHeight() - s) / 2, s, s);
                        g2.dispose();
                    }
                };
                b.setBorder(null);
                b.setContentAreaFilled(false);
                b.setFocusPainted(false);
                b.setOpaque(false);
                b.setPreferredSize(new Dimension(36, 36));
                return b;
            }

            @Override protected ComboPopup createPopup() {
                BasicComboPopup p = new BasicComboPopup(comboBox) {
                    @Override protected JScrollPane createScroller() {
                        JScrollPane sp = scroll(list);
                        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                        return sp;
                    }
                };
                p.setBorder(BorderFactory.createLineBorder(STONE_LIT, 2));
                p.getList().setBackground(STONE_DK);
                return p;
            }
        });
        c.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, false);
                setFont(body(14));
                setBorder(new EmptyBorder(6, 10, 6, 10));
                setForeground(PARCHMENT);
                // index -1 là giá trị đang hiện trên mặt ô chọn: không tô nền
                setOpaque(index >= 0);
                setBackground(sel ? mix(STONE, RUST, 0.45f) : STONE_DK);
                return this;
            }
        });
        return c;
    }

    // ── Bảng và thanh cuộn ──────────────────────────────────────────────

    /** Tạo kiểu cho bảng: hàng cao, không kẻ ô, tiêu đề chữ vàng; trả về khung cuộn đã tạo kiểu */
    public static JScrollPane table(JTable t) {
        t.setFont(body(14));
        t.setForeground(PARCHMENT);
        t.setBackground(STONE_DK);
        t.setRowHeight(34);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setSelectionBackground(alpha(GILT, 90));
        t.setSelectionForeground(Color.WHITE);
        t.setFillsViewportHeight(true);
        t.setFocusable(false);

        DefaultTableCellRenderer cells = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean sel,
                                                           boolean focus, int row, int col) {
                super.getTableCellRendererComponent(table, value, sel, false, row, col);
                setBorder(new EmptyBorder(0, 12, 0, 12));
                if (!sel) setBackground(row % 2 == 0 ? STONE_DK : mix(STONE_DK, STONE, 0.35f));
                return this;
            }
        };
        t.setDefaultRenderer(Object.class, cells);
        t.setDefaultRenderer(Integer.class, cells);

        JTableHeader head = t.getTableHeader();
        head.setReorderingAllowed(false);
        head.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean sel,
                                                           boolean focus, int row, int col) {
                super.getTableCellRendererComponent(table, value, false, false, row, col);
                setFont(bodyBold(13));
                setForeground(GILT);
                setBackground(NIGHT);
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, alpha(GILT, 120)),
                    new EmptyBorder(8, 12, 8, 12)));
                return this;
            }
        });
        return scroll(t);
    }

    /** Khung cuộn tối, thanh cuộn mảnh màu đá */
    public static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createLineBorder(STONE_DK, 1));
        sp.getViewport().setBackground(STONE_DK);
        sp.setOpaque(false);
        for (JScrollBar bar : new JScrollBar[]{sp.getVerticalScrollBar(), sp.getHorizontalScrollBar()}) {
            bar.setPreferredSize(new Dimension(10, 10));
            bar.setUI(new BasicScrollBarUI() {
                @Override protected void configureScrollBarColors() {
                    thumbColor = STONE_LIT;
                    trackColor = STONE_DK;
                }
                @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
                @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }
            });
        }
        return sp;
    }

    private static JButton zeroButton() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        return b;
    }

    // ── Màu tiện ích ────────────────────────────────────────────────────

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    /** Trộn 2 màu: t = 0 -> a, t = 1 -> b */
    public static Color mix(Color a, Color b, float t) {
        return new Color(
            Math.round(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            Math.round(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t),
            a.getAlpha());
    }
}
