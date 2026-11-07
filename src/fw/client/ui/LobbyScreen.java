package fw.client.ui;

import fw.client.Model;
import fw.common.net.MsgType;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Sảnh chờ.
 *  ┌──────────┬───────────────────────┬──────────────┐
 *  │ Tạo phòng│  Phòng đang mở (2/3)  │ Lịch sử đấu  │
 *  │ Vào phòng│  (bấm đúp để vào)     │ của tôi      │
 *  │ Làm mới  │                       │              │
 *  │   ...    ├───────────────────────┤              │
 *  │ Xếp hạng │  Chat tổng (1/3)      │              │
 *  │ Đăng xuất│                       │              │
 *  └──────────┴───────────────────────┴──────────────┘
 */
public class LobbyScreen extends Theme.TempleBackground {
    private final DefaultTableModel roomModel = readOnly("Phòng", "Chủ phòng", "Người chơi", "Trạng thái");
    private final DefaultListModel<Model.HistoryRow> historyModel = new DefaultListModel<>();
    private final CardLayout historyCards = new CardLayout();
    private final JPanel historyArea = new JPanel(historyCards);
    private final ChatPanel chat = new ChatPanel(false, MsgType.LOBBY_CHAT);
    private final JTable roomTable = new JTable(roomModel);
    private final JLabel welcome = Theme.label("Xin chào", Theme.display(22), Theme.PARCHMENT);
    private final CardLayout roomCards = new CardLayout();
    private final JPanel roomArea = new JPanel(roomCards);

    /** ClientApp gán: mở bảng xếp hạng */
    public Runnable onRank;

    public LobbyScreen() {
        super(new BorderLayout(16, 0));
        setBorder(new EmptyBorder(18, 18, 18, 18));

        add(sidebar(), BorderLayout.WEST);
        add(centerColumn(), BorderLayout.CENTER);
        add(historyPanel(), BorderLayout.EAST);
    }

    /**
     * Cột trái: lời chào, nhóm nút về phòng (tạo, vào bằng mã, vào phòng đã chọn, làm mới),
     * nhóm nút cá nhân ở đáy (bảng xếp hạng, đăng xuất)
     */
    private JComponent sidebar() {
        Theme.StonePanel side = new Theme.StonePanel(null);
        side.setPreferredSize(new Dimension(240, 0));
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        welcome.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel sub = Theme.label("Rủ bạn cùng vượt đền.", Theme.body(13), Theme.MUTED);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(welcome);
        col.add(Box.createVerticalStrut(4));
        col.add(sub);
        col.add(Box.createVerticalStrut(22));

        // Nhóm trên: việc với phòng
        Theme.StoneButton create = Theme.button("Tạo phòng", Theme.Kind.PRIMARY);
        Theme.StoneButton join = Theme.button("Vào phòng đã chọn", Theme.Kind.STONE);
        Theme.StoneButton refresh = Theme.button("Làm mới danh sách", Theme.Kind.STONE);
        create.addActionListener(e -> ClientApp.tcp.send(MsgType.CREATE_ROOM, new JsonObject()));
        join.addActionListener(e -> joinSelected());
        refresh.addActionListener(e -> ClientApp.tcp.send(MsgType.ROOM_LIST, new JsonObject()));
        stack(col, create, codeRow(), join, refresh);

        col.add(Box.createVerticalGlue());

        // Nhóm dưới: thông tin cá nhân
        Theme.StoneButton rank = Theme.button("Bảng xếp hạng", Theme.Kind.STONE);
        Theme.StoneButton logout = Theme.button("Đăng xuất", Theme.Kind.DANGER);
        rank.addActionListener(e -> { if (onRank != null) onRank.run(); });
        logout.addActionListener(e -> logout());
        stack(col, rank, logout);

        side.content().add(col);
        return side;
    }

    /** Ô nhập mã phòng (ví dụ R3 hoặc chỉ 3) + nút Vào; Enter trong ô cũng là vào phòng */
    private JComponent codeRow() {
        JTextField code = Theme.field();
        code.setToolTipText("Nhập mã phòng, ví dụ R3");
        Theme.StoneButton go = Theme.button("Vào", Theme.Kind.STONE);
        go.setPreferredSize(new Dimension(70, 40));
        Runnable join = () -> {
            String id = code.getText().trim().toUpperCase();
            if (id.isEmpty()) return;
            // gõ "3" cũng hiểu là phòng R3
            if (id.matches("\\d+")) id = "R" + id;
            JsonObject data = new JsonObject();
            data.addProperty("room", id);
            ClientApp.tcp.send(MsgType.JOIN_ROOM, data);
            code.setText("");
        };
        code.addActionListener(e -> join.run());
        go.addActionListener(e -> join.run());

        JPanel box = new JPanel(new BorderLayout(0, 4));
        box.setOpaque(false);
        box.add(Theme.label("Vào bằng mã phòng", Theme.body(13), Theme.MUTED), BorderLayout.NORTH);
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.add(code, BorderLayout.CENTER);
        row.add(go, BorderLayout.EAST);
        box.add(row, BorderLayout.CENTER);
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        box.setAlignmentX(Component.CENTER_ALIGNMENT);
        return box;
    }

    /** Xếp các nút theo cột, cách nhau 10px */
    static void stack(JPanel col, JComponent... items) {
        for (JComponent c : items) {
            col.add(c);
            col.add(Box.createVerticalStrut(10));
        }
    }

    // Bảng phòng đang mở: bấm đúp để vào, chưa có phòng thì hiện lời nhắc thay cho bảng trống
    private JComponent roomsPanel() {
        Theme.StonePanel panel = new Theme.StonePanel("Phòng đang mở");
        roomTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                // bấm đúp = vào phòng
                if (e.getClickCount() == 2) joinSelected();
            }
        });
        roomArea.setOpaque(false);
        roomArea.add(Theme.table(roomTable), "list");
        JLabel empty = Theme.label("<html><center>Chưa có phòng nào.<br>Bấm \"Tạo phòng\" rồi rủ bạn vào cùng.</center></html>",
            Theme.body(15), Theme.MUTED);
        empty.setHorizontalAlignment(SwingConstants.CENTER);
        roomArea.add(empty, "empty");
        panel.content().add(roomArea);
        JLabel hint = Theme.label("Bấm đúp vào một phòng để vào.", Theme.body(12), Theme.MUTED);
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    /** Cột giữa: danh sách phòng chiếm 2 phần, chat tổng 1 phần chiều cao */
    private JComponent centerColumn() {
        JPanel c = new JPanel(new GridBagLayout());
        c.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.fill = GridBagConstraints.BOTH;
        g.weightx = 1;

        g.gridy = 0;
        g.weighty = 2;
        g.insets = new Insets(0, 0, 8, 0);
        JComponent rooms = roomsPanel();
        // chia theo weighty, không theo nội dung
        rooms.setPreferredSize(new Dimension(0, 2));
        c.add(rooms, g);

        g.gridy = 1;
        g.weighty = 1;
        g.insets = new Insets(8, 0, 0, 0);
        Theme.StonePanel chatBox = new Theme.StonePanel("Chat tổng");
        chatBox.setPreferredSize(new Dimension(0, 1));
        chatBox.content().add(chat);
        c.add(chatBox, g);
        return c;
    }

    /** Cột phải: các màn mình đã hoàn thành, mới nhất trước */
    private JComponent historyPanel() {
        Theme.StonePanel panel = new Theme.StonePanel("Lịch sử đấu của tôi");
        panel.setPreferredSize(new Dimension(290, 0));
        JList<Model.HistoryRow> list = new JList<>(historyModel);
        list.setBackground(Theme.STONE_DK);
        list.setFocusable(false);
        list.setCellRenderer((l, row, i, sel, foc) -> RoomScreen.historyCell(row));
        historyArea.setOpaque(false);
        historyArea.add(Theme.scroll(list), "list");
        JLabel empty = Theme.label("<html><center>Bạn chưa hoàn thành màn nào.<br>"
            + "Qua màn đầu tiên là nó sẽ hiện ở đây.</center></html>", Theme.body(14), Theme.MUTED);
        empty.setHorizontalAlignment(SwingConstants.CENTER);
        historyArea.add(empty, "empty");
        historyCards.show(historyArea, "empty");
        panel.content().add(historyArea);
        return panel;
    }

    public void updateHistory() {
        historyModel.clear();
        for (Model.HistoryRow r : Model.history) historyModel.addElement(r);
        historyCards.show(historyArea, Model.history.isEmpty() ? "empty" : "list");
    }

    public ChatPanel chat() {
        return chat;
    }

    private static DefaultTableModel readOnly(String... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    public void updateWelcome(String name) {
        welcome.setText("Chào " + name);
    }

    public void updateRooms() {
        roomModel.setRowCount(0);
        for (Model.RoomInfoRow room : Model.roomList) {
            roomModel.addRow(new Object[]{
                room.room(), room.host(), room.n() + "/2",
                "PLAYING".equals(room.status()) ? "Đang chơi" : (room.n() >= 2 ? "Đủ người" : "Đang chờ")
            });
        }
        roomCards.show(roomArea, Model.roomList.isEmpty() ? "empty" : "list");
    }

    private void joinSelected() {
        int row = roomTable.getSelectedRow();
        if (row < 0 || row >= Model.roomList.size()) {
            PixelDialog.message(this, "Vào phòng", "Chọn một phòng trong danh sách trước.", PixelDialog.Kind.INFO);
            return;
        }
        Model.RoomInfoRow room = Model.roomList.get(row);
        if ("PLAYING".equals(room.status())) {
            PixelDialog.message(this, "Vào phòng", "Phòng này đang chơi, hãy chọn phòng khác.", PixelDialog.Kind.INFO);
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty("room", room.room());
        ClientApp.tcp.send(MsgType.JOIN_ROOM, data);
    }

    private void logout() {
        if (PixelDialog.confirm(this, "Đăng xuất", "Đăng xuất khỏi tài khoản này?")) {
            ClientApp.tcp.send(MsgType.LOGOUT, new JsonObject());
        }
    }
}
