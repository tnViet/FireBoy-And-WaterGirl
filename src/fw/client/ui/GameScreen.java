package fw.client.ui;

import fw.client.Model;
import fw.client.render.GamePanel;
import fw.common.game.Input;
import fw.common.net.Msg;
import fw.common.net.MsgType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Màn chơi: khung game ở giữa, thanh dưới có chat nhỏ (góc trái) và các nút (bên phải).
 * Bàn phím: ← → / A D di chuyển, ↑ / W / Space nhảy, Enter mở ô chat.
 */
public class GameScreen extends Theme.TempleBackground {
    private final GamePanel gamePanel = new GamePanel();
    private final EndOverlay endOverlay = new EndOverlay();
    private final ChatPanel chat = new ChatPanel(true);
    private final Theme.StoneButton restartButton = Theme.button("Chơi lại", Theme.Kind.STONE);
    private Timer renderTimer;

    private volatile boolean leftPressed = false;
    private volatile boolean rightPressed = false;
    private volatile boolean jumpPressed = false;

    /**
     * Màn chơi: khung game ở giữa (bảng kết quả nằm đè lên), thanh dưới có chat nhỏ và các nút.
     * Bàn phím đặt cờ trái/phải/nhảy; mất focus thì nhả hết phím để nhân vật không tự chạy.
     */
    public GameScreen() {
        super(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // Bảng kết quả nằm đè lên giữa khung chơi
        gamePanel.setLayout(new GridBagLayout());
        endOverlay.setVisible(false);
        gamePanel.add(endOverlay);
        gamePanel.setFocusable(false);
        add(gamePanel, BorderLayout.CENTER);
        add(bottomBar(), BorderLayout.SOUTH);

        // Bàn phím điều khiển nhân vật
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                // Enter = mở ô chat
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    leftPressed = rightPressed = jumpPressed = false;
                    chat.focusInput();
                    return;
                }
                handleKey(e.getKeyCode(), true);
            }
            @Override public void keyReleased(KeyEvent e) {
                handleKey(e.getKeyCode(), false);
            }
        });
        // Chuyển sang cửa sổ khác (Alt+Tab) hoặc gõ chat thì không nhận được keyReleased -> tự nhả hết phím
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                leftPressed = rightPressed = jumpPressed = false;
            }
        });
        // gửi xong / Esc -> quay lại điều khiển nhân vật
        chat.onDone = this::requestFocusInWindow;
    }

    // Thanh dưới: chat nhỏ bên trái, nút chơi lại (chỉ chủ phòng) và thoát phòng bên phải
    private JComponent bottomBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);

        chat.setPreferredSize(new Dimension(420, 118));
        bar.add(chat, BorderLayout.WEST);

        JLabel keys = Theme.label("<html>← → hoặc A D để đi, ↑ W Space để nhảy<br>Enter để chat, Esc để quay lại chơi</html>",
            Theme.body(13), Theme.MUTED);
        bar.add(keys, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 8));
        buttons.setOpaque(false);
        Theme.StoneButton leave = Theme.button("Thoát phòng", Theme.Kind.DANGER);
        for (Theme.StoneButton b : new Theme.StoneButton[]{restartButton, leave}) {
            b.setPreferredSize(new Dimension(170, 44));
            // nút không được giữ bàn phím, nếu không nhân vật sẽ đứng im
            b.setFocusable(false);
            buttons.add(b);
        }
        restartButton.addActionListener(e -> restart());
        leave.addActionListener(e -> leave());
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    private void handleKey(int code, boolean pressed) {
        switch (code) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> leftPressed = pressed;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> rightPressed = pressed;
            case KeyEvent.VK_UP, KeyEvent.VK_W, KeyEvent.VK_SPACE -> jumpPressed = pressed;
        }
    }

    public Input getInput() {
        return new Input(leftPressed, rightPressed, jumpPressed);
    }

    public ChatPanel chat() {
        return chat;
    }

    public void startRendering() {
        if (renderTimer != null) renderTimer.stop();
        // Xóa trạng thái phím cũ để nhân vật không tự chạy khi bắt đầu màn mới
        leftPressed = rightPressed = jumpPressed = false;
        renderTimer = new Timer(16, e -> gamePanel.repaint());
        renderTimer.start();
        requestFocusInWindow();
    }

    public void stopRendering() {
        if (renderTimer != null) {
            renderTimer.stop();
            renderTimer = null;
        }
    }

    /** Chỉ chủ phòng được chơi lại màn khi đang chơi */
    public void updateControls() {
        restartButton.setVisible(Model.isHost());
    }

    private void leave() {
        if (PixelDialog.confirm(this, "Thoát phòng", "Thoát khỏi phòng? Trận đấu sẽ kết thúc.")) {
            ClientApp.tcp.send(MsgType.LEAVE_ROOM, new JsonObject());
        }
        requestFocusInWindow();
    }

    private void restart() {
        // Server hủy trận hiện tại (GAME_ABORT reason=RESTART) rồi gửi GAME_START mới
        if (Model.isHost()) ClientApp.tcp.send(MsgType.RESTART, new JsonObject());
        requestFocusInWindow();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) requestFocusInWindow();
    }

    // ── Bảng kết quả sau khi kết thúc màn ───────────────────────────────

    /** Hiện bảng kết quả, giữ nguyên khung cảnh lúc kết thúc phía sau */
    public void showEnd(boolean won, String text, boolean hasNext) {
        leftPressed = rightPressed = jumpPressed = false;
        // sau khi kết thúc thì chơi lại bằng bỏ phiếu
        restartButton.setVisible(false);
        endOverlay.open(won, text, hasNext);
    }

    public void hideEnd() {
        endOverlay.setVisible(false);
    }

    public boolean isEndShown() {
        return endOverlay.isVisible();
    }

    /** Nhận VOTE_STATE từ server: cập nhật lựa chọn của mình và của người kia */
    public void onVotes(Msg msg) {
        String mine = null, other = null;
        for (JsonElement el : msg.data.getAsJsonArray("votes")) {
            JsonObject v = el.getAsJsonObject();
            if (v.get("acc").getAsInt() == Model.acc) mine = v.get("choice").getAsString();
            else other = v.get("choice").getAsString();
        }
        endOverlay.showVotes(mine, other);
    }

    private static String label(String choice) {
        return "NEXT".equals(choice) ? "Màn tiếp theo" : "Chơi lại";
    }

    /** Tấm bia kết quả: tiêu đề, lời nhắn, 3 nút bỏ phiếu, dòng trạng thái phiếu */
    private class EndOverlay extends Theme.StonePanel {
        private final JLabel title = new JLabel(" ", SwingConstants.CENTER);
        private final JLabel message = new JLabel(" ", SwingConstants.CENTER);
        private final JLabel status = new JLabel(" ", SwingConstants.CENTER);
        private final Theme.StoneButton replayBtn = Theme.button("Chơi lại", Theme.Kind.PRIMARY);
        private final Theme.StoneButton nextBtn = Theme.button("Màn tiếp theo", Theme.Kind.PRIMARY);
        private final Theme.StoneButton leaveBtn = Theme.button("Thoát phòng", Theme.Kind.DANGER);

        // Xếp theo cột: tiêu đề, lời nhắn, 3 nút (chơi lại, màn tiếp, thoát), dòng trạng thái bỏ phiếu
        EndOverlay() {
            super(null);
            setBorder(new EmptyBorder(24, 36, 24, 36));
            JPanel col = new JPanel();
            col.setOpaque(false);
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

            title.setFont(Theme.display(34));
            message.setFont(Theme.body(16));
            message.setForeground(Theme.PARCHMENT);
            status.setFont(Theme.body(14));
            status.setForeground(Theme.GILT);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
            buttons.setOpaque(false);
            for (Theme.StoneButton b : new Theme.StoneButton[]{replayBtn, nextBtn, leaveBtn}) {
                b.setFocusable(false);
                b.setPreferredSize(new Dimension(170, 50));
                buttons.add(b);
            }
            replayBtn.addActionListener(e -> vote("REPLAY"));
            nextBtn.addActionListener(e -> vote("NEXT"));
            // Thoát: server cho người này rời phòng, người còn lại tự quay về màn hình phòng
            leaveBtn.addActionListener(e -> ClientApp.tcp.send(MsgType.LEAVE_ROOM, new JsonObject()));

            for (JComponent c : new JComponent[]{title, message, buttons, status}) {
                c.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
            // Nhãn giãn hết bề ngang của bảng (BoxLayout), chữ dài không bị cắt
            for (JLabel l : new JLabel[]{title, message, status}) {
                l.setMaximumSize(new Dimension(Integer.MAX_VALUE, l.getPreferredSize().height + 8));
            }
            col.add(title);
            col.add(Box.createVerticalStrut(8));
            col.add(message);
            col.add(Box.createVerticalStrut(18));
            col.add(buttons);
            col.add(Box.createVerticalStrut(14));
            col.add(status);
            content().add(col);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 620), d.height);
        }

        void open(boolean won, String text, boolean hasNext) {
            title.setText(won ? "Hoàn thành màn" : "Thất bại");
            title.setForeground(won ? Theme.GILT : Theme.RUST.brighter());
            message.setText(text);
            nextBtn.setVisible(won && hasNext);
            showVotes(null, null);
            setVisible(true);
            revalidate();
        }

        private void vote(String choice) {
            JsonObject data = new JsonObject();
            data.addProperty("choice", choice);
            ClientApp.tcp.send(MsgType.VOTE, data);
        }

        void showVotes(String mine, String other) {
            replayBtn.setText("REPLAY".equals(mine) ? "✓ Chơi lại" : "Chơi lại");
            nextBtn.setText("NEXT".equals(mine) ? "✓ Màn tiếp theo" : "Màn tiếp theo");
            String s;
            if (mine == null && other == null) s = "Cả hai cùng chọn thì trận mới bắt đầu.";
            else if (mine == null)             s = "Người kia muốn " + label(other).toLowerCase() + ". Chọn giống để bắt đầu.";
            else if (other == null)            s = "Bạn chọn " + label(mine).toLowerCase() + ". Đang chờ người kia...";
            else                               s = "Hai người đang chọn khác nhau: bạn " + label(mine).toLowerCase()
                                                   + ", người kia " + label(other).toLowerCase() + ".";
            status.setText(s);
            revalidate();
            repaint();
        }
    }
}
