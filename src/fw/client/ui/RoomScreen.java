package fw.client.ui;

import fw.client.Model;
import fw.common.net.MsgType;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Phòng chờ (theo wireframe).
 *  ┌────────────┬──────────────────────────────┬────────────┐
 *  │ Thoát phòng│  [Người chơi 1] [Người chơi 2]│ Chọn màn   │
 *  │ Lịch sử    │  thống kê màn   thống kê màn  │  Màn 1..4  │
 *  │ của tôi    │                               │ Đổi nhân vật│
 *  │            │  ┌─ Trò chuyện ─────────────┐ │            │
 *  │ Xếp hạng   │  └──────────────────────────┘ │ [BẮT ĐẦU]  │
 *  └────────────┴──────────────────────────────┴────────────┘
 */
public class RoomScreen extends Theme.TempleBackground {
    private final JLabel roomTitle = Theme.label("Phòng", Theme.display(24), Theme.PARCHMENT);
    private final JLabel statusLabel = Theme.label(" ", Theme.body(14), Theme.MUTED);
    private final JPanel cards = new JPanel(new GridLayout(1, 2, 16, 0));
    private final DefaultListModel<Model.LevelInfo> levelModel = new DefaultListModel<>();
    private final JList<Model.LevelInfo> levelList = new JList<>(levelModel);
    private final DefaultListModel<Model.HistoryRow> historyModel = new DefaultListModel<>();
    private final Theme.StoneButton swapButton = Theme.button("Đổi nhân vật", Theme.Kind.STONE);
    private final Theme.StoneButton startButton = Theme.button("Bắt đầu", Theme.Kind.PRIMARY);
    private final JLabel startHint = Theme.label(" ", Theme.body(12), Theme.MUTED);
    private final ChatPanel chat = new ChatPanel(false);

    /** ClientApp gán: mở bảng xếp hạng / toàn bộ lịch sử */
    public Runnable onRank;
    public Runnable onHistory;

    // true khi code đang tự cập nhật danh sách màn -> không gửi SELECT_LEVEL ngược lên server
    private boolean updating = false;

    public RoomScreen() {
        super(new BorderLayout(16, 0));
        setBorder(new EmptyBorder(18, 18, 18, 18));
        add(sidebar(), BorderLayout.WEST);
        add(center(), BorderLayout.CENTER);
        add(levelPanel(), BorderLayout.EAST);
    }

    // ── Cột trái: thoát phòng, lịch sử của tôi, xếp hạng ────────────────

    // Cột trái: nút thoát phòng, lịch sử đấu của mình, nút bảng xếp hạng
    private JComponent sidebar() {
        JPanel side = new JPanel(new BorderLayout(0, 12));
        side.setOpaque(false);
        side.setPreferredSize(new Dimension(240, 0));

        Theme.StoneButton leave = Theme.button("Thoát phòng", Theme.Kind.DANGER);
        leave.addActionListener(e -> ClientApp.tcp.send(MsgType.LEAVE_ROOM, new JsonObject()));
        side.add(leave, BorderLayout.NORTH);

        Theme.StonePanel hist = new Theme.StonePanel("Lịch sử của tôi");
        JList<Model.HistoryRow> list = new JList<>(historyModel);
        list.setBackground(Theme.STONE_DK);
        list.setFocusable(false);
        list.setCellRenderer((l, row, i, sel, foc) -> historyCell(row));
        hist.content().add(Theme.scroll(list));
        Theme.StoneButton all = Theme.button("Xem toàn bộ", Theme.Kind.STONE);
        all.setPreferredSize(new Dimension(0, 38));
        all.addActionListener(e -> { if (onHistory != null) onHistory.run(); });
        hist.add(all, BorderLayout.SOUTH);
        side.add(hist, BorderLayout.CENTER);

        Theme.StoneButton rank = Theme.button("Bảng xếp hạng", Theme.Kind.STONE);
        rank.addActionListener(e -> { if (onRank != null) onRank.run(); });
        side.add(rank, BorderLayout.SOUTH);
        return side;
    }

    /** 1 dòng lịch sử: tên màn (vàng), thời gian + ngày và người chơi cùng (mờ) */
    static JComponent historyCell(Model.HistoryRow row) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.STONE_DK);
        p.setBorder(new EmptyBorder(6, 8, 6, 8));
        p.add(Theme.label(shortName(row.level()), Theme.bodyBold(13), Theme.GILT), BorderLayout.NORTH);
        p.add(Theme.label("Chơi cùng " + row.partnerText(), Theme.body(12), Theme.PARCHMENT), BorderLayout.CENTER);
        p.add(Theme.label(seconds(row.sec()) + ", " + row.when(), Theme.body(12), Theme.MUTED), BorderLayout.SOUTH);
        return p;
    }

    // ── Giữa: thẻ 2 người chơi + chat ───────────────────────────────────

    // Cột giữa: tên phòng và trạng thái, 2 thẻ người chơi, khung chat của phòng
    private JComponent center() {
        JPanel c = new JPanel(new BorderLayout(0, 14));
        c.setOpaque(false);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(roomTitle, BorderLayout.WEST);
        head.add(statusLabel, BorderLayout.SOUTH);
        c.add(head, BorderLayout.NORTH);

        cards.setOpaque(false);
        c.add(cards, BorderLayout.CENTER);

        Theme.StonePanel chatBox = new Theme.StonePanel("Trò chuyện");
        chatBox.setPreferredSize(new Dimension(0, 220));
        chatBox.content().add(chat);
        c.add(chatBox, BorderLayout.SOUTH);
        return c;
    }

    /** Thẻ người chơi: tên, vai, chủ phòng, rồi thống kê từng màn */
    private JComponent playerCard(Model.RoomData.Member mem, boolean isHost) {
        boolean fire = "FIRE".equals(mem.el());
        Color tint = fire ? Theme.FIRE : Theme.WATER;
        Theme.StonePanel card = new Theme.StonePanel(null, tint);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        String you = mem.acc() == Model.acc ? " (bạn)" : "";
        top.add(Theme.label(mem.name() + you, Theme.display(22), Theme.PARCHMENT));
        top.add(Box.createVerticalStrut(2));
        top.add(Theme.label(fire ? "Lửa, lội được dung nham" : "Nước, lội được hồ nước",
            Theme.bodyBold(13), tint));
        if (isHost) top.add(Theme.label("Chủ phòng", Theme.bodyBold(13), Theme.GILT));
        top.add(Box.createVerticalStrut(10));
        JSeparator sep = new JSeparator();
        sep.setForeground(Theme.alpha(tint, 120));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        top.add(sep);
        card.content().add(top, BorderLayout.NORTH);

        // Thống kê: mỗi màn 2 dòng (tên màn, kết quả)
        JPanel stats = new JPanel();
        stats.setOpaque(false);
        stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));
        stats.setBorder(new EmptyBorder(10, 0, 0, 0));
        List<Model.StatRow> rows = Model.stats.get(mem.acc());
        if (rows == null) {
            stats.add(Theme.label("Đang tải thống kê...", Theme.body(13), Theme.MUTED));
        } else {
            for (Model.StatRow r : rows) {
                stats.add(Theme.label(r.level(), Theme.bodyBold(13), Theme.PARCHMENT));
                stats.add(statText(r));
                stats.add(Box.createVerticalStrut(8));
            }
        }
        card.content().add(stats, BorderLayout.CENTER);
        return card;
    }

    private static JLabel statText(Model.StatRow r) {
        String text = r.done() == 0
            ? (r.tries() == 0 ? "Chưa chơi" : "Chưa qua, đã thử " + r.tries() + " lần")
            : "Qua " + r.done() + " lần, nhanh nhất " + seconds(r.best());
        return Theme.label(text, Theme.body(13), r.done() > 0 ? Theme.MOSS : Theme.MUTED);
    }

    private static JComponent emptyCard() {
        Theme.StonePanel card = new Theme.StonePanel(null);
        JLabel l = Theme.label("<html><center>Đang chờ người chơi thứ hai...<br><br>"
            + "Rủ bạn vào phòng này từ sảnh chờ.</center></html>", Theme.body(15), Theme.MUTED);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        card.content().add(l);
        return card;
    }

    // ── Cột phải: chọn màn + bắt đầu ────────────────────────────────────

    // Cột phải: danh sách màn (chỉ chủ phòng được chọn), nút đổi nhân vật và nút bắt đầu
    private JComponent levelPanel() {
        JPanel right = new JPanel(new BorderLayout(0, 12));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(270, 0));

        Theme.StonePanel lv = new Theme.StonePanel("Chọn màn");
        levelList.setBackground(Theme.STONE_DK);
        levelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        levelList.setCellRenderer((l, info, i, sel, foc) -> levelCell(info, sel, l.isEnabled()));
        levelList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) selectLevel(); });
        lv.content().add(Theme.scroll(levelList));
        swapButton.addActionListener(e -> ClientApp.tcp.send(MsgType.SWAP, new JsonObject()));
        lv.add(swapButton, BorderLayout.SOUTH);
        right.add(lv, BorderLayout.CENTER);

        JPanel start = new JPanel(new BorderLayout(0, 6));
        start.setOpaque(false);
        startButton.setPreferredSize(new Dimension(0, 72));
        startButton.setFont(Theme.display(24));
        startButton.addActionListener(e -> ClientApp.tcp.send(MsgType.START_GAME, new JsonObject()));
        startHint.setHorizontalAlignment(SwingConstants.CENTER);
        start.add(startHint, BorderLayout.NORTH);
        start.add(startButton, BorderLayout.CENTER);
        right.add(start, BorderLayout.SOUTH);
        return right;
    }

    /** 1 màn trong danh sách: "Màn 1" đậm, tên màn bên dưới */
    private static JComponent levelCell(Model.LevelInfo info, boolean sel, boolean enabled) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(sel ? Theme.alpha(Theme.GILT, 70) : Theme.STONE_DK);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, sel ? Theme.GILT : Theme.STONE_DK),
            new EmptyBorder(8, 10, 8, 10)));
        String name = info.name();
        int colon = name.indexOf(':');
        String head = colon > 0 ? name.substring(0, colon) : name;
        String tail = colon > 0 ? name.substring(colon + 1).trim() : "";
        p.add(Theme.label(head, Theme.bodyBold(15), sel ? Theme.GILT : Theme.PARCHMENT), BorderLayout.NORTH);
        if (!tail.isEmpty()) p.add(Theme.label(tail, Theme.body(13), Theme.MUTED), BorderLayout.SOUTH);
        return p;
    }

    // ── Cập nhật dữ liệu ────────────────────────────────────────────────

    /**
     * Vẽ lại phòng theo Model.room: thẻ người chơi, màn đang chọn, bật/tắt nút theo vai trò.
     * Cờ updating chặn sự kiện chọn màn giả khi chương trình tự đặt lại lựa chọn.
     */
    public void updateRoom() {
        Model.RoomData room = Model.room;
        if (room == null) return;
        roomTitle.setText("Phòng " + room.room);

        cards.removeAll();
        for (Model.RoomData.Member mem : room.members) cards.add(playerCard(mem, mem.acc() == room.host));
        if (room.members.size() < 2) cards.add(emptyCard());
        cards.revalidate();
        cards.repaint();

        updating = true;
        try {
            if (levelModel.size() != Model.levels.size()) {
                levelModel.clear();
                for (Model.LevelInfo lv : Model.levels) levelModel.addElement(lv);
            }
            for (int i = 0; i < levelModel.size(); i++) {
                if (levelModel.get(i).lv() == room.lv) levelList.setSelectedIndex(i);
            }
        } finally {
            updating = false;
        }

        boolean isHost = room.host == Model.acc;
        boolean full = room.members.size() == 2;
        levelList.setEnabled(isHost);
        swapButton.setEnabled(isHost && full);
        startButton.setEnabled(isHost && full && room.lv > 0);
        startHint.setText(!full ? "Cần đủ 2 người để bắt đầu"
            : isHost ? "Hai bạn đã sẵn sàng" : "Chờ chủ phòng bấm bắt đầu");
        if (full) showStatus(" ");
        else showStatus("Đang chờ người chơi thứ 2...");
    }

    public void updateHistory() {
        historyModel.clear();
        for (Model.HistoryRow r : Model.history) historyModel.addElement(r);
    }

    public void showStatus(String msg) {
        statusLabel.setText(msg);
    }

    public ChatPanel chat() {
        return chat;
    }

    private void selectLevel() {
        if (updating || !Model.isHost()) return;
        Model.LevelInfo info = levelList.getSelectedValue();
        if (info == null || Model.room == null || info.lv() == Model.room.lv) return;
        JsonObject data = new JsonObject();
        data.addProperty("lv", info.lv());
        ClientApp.tcp.send(MsgType.SELECT_LEVEL, data);
    }

    /** "Màn 1: Mở cửa cho nhau" -> "Màn 1: Mở cửa cho nhau" (rút gọn nếu quá dài) */
    static String shortName(String level) {
        return level.length() > 26 ? level.substring(0, 25) + "…" : level;
    }

    static String seconds(Double sec) {
        return sec == null ? "-" : String.format("%.2f giây", sec);
    }
}
