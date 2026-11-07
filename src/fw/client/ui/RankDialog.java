package fw.client.ui;

import fw.client.Model;
import fw.common.net.Msg;
import fw.common.net.MsgType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Bảng xếp hạng: "Tổng" (theo số màn hoàn thành) và "Theo màn" (theo thời gian nhanh nhất).
 * Gửi GET_RANK qua TCP, server trả RANK -> ClientApp gọi onRank() để đổ dữ liệu vào bảng.
 */
public class RankDialog extends JDialog {
    private final DefaultTableModel allModel = readOnly("Hạng", "Người chơi", "Màn đã qua", "Lượt chơi");
    private final DefaultTableModel lvModel = readOnly("Hạng", "Người chơi", "Thời gian", "Số lần thử");
    private final JComboBox<String> levelCombo = new JComboBox<>();
    private final Theme.StoneButton allTab = Theme.button("Tổng", Theme.Kind.PRIMARY);
    private final Theme.StoneButton lvTab = Theme.button("Theo màn", Theme.Kind.STONE);
    private final CardLayout cards = new CardLayout();
    private final JPanel area = new JPanel(cards);
    private boolean showingAll = true;

    // Hộp thoại xếp hạng: 2 nút đổi tab (Tổng / Theo màn), mỗi tab 1 bảng; tab theo màn có ô chọn màn
    public RankDialog(Frame owner) {
        super(owner, "Bảng xếp hạng", false);
        setSize(640, 520);
        setLocationRelativeTo(owner);

        Theme.TempleBackground bg = new Theme.TempleBackground(new BorderLayout(0, 12));
        bg.setBorder(new EmptyBorder(18, 18, 18, 18));

        // Hai "tab" là 2 nút: nút đang chọn màu vàng
        JPanel head = new JPanel(new BorderLayout(12, 0));
        head.setOpaque(false);
        head.add(Theme.label("Bảng xếp hạng", Theme.display(22), Theme.GILT), BorderLayout.WEST);
        JPanel tabs = new JPanel(new GridLayout(1, 2, 8, 0));
        tabs.setOpaque(false);
        for (Theme.StoneButton b : new Theme.StoneButton[]{allTab, lvTab}) {
            b.setPreferredSize(new Dimension(130, 40));
            tabs.add(b);
        }
        allTab.addActionListener(e -> selectTab(true));
        lvTab.addActionListener(e -> selectTab(false));
        head.add(tabs, BorderLayout.EAST);
        bg.add(head, BorderLayout.NORTH);

        area.setOpaque(false);
        Theme.StonePanel allPanel = new Theme.StonePanel("Xếp theo số màn đã hoàn thành");
        allPanel.content().add(Theme.table(new JTable(allModel)));
        area.add(allPanel, "all");

        Theme.StonePanel lvPanel = new Theme.StonePanel("Xếp theo thời gian nhanh nhất");
        Theme.combo(levelCombo);
        levelCombo.addActionListener(e -> requestLevel());
        lvPanel.add(levelCombo, BorderLayout.SOUTH);
        lvPanel.content().add(Theme.table(new JTable(lvModel)));
        area.add(lvPanel, "level");
        bg.add(area, BorderLayout.CENTER);

        Theme.StoneButton close = Theme.button("Đóng", Theme.Kind.STONE);
        close.setPreferredSize(new Dimension(140, 42));
        close.addActionListener(e -> setVisible(false));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.setOpaque(false);
        south.add(close);
        bg.add(south, BorderLayout.SOUTH);
        setContentPane(bg);
        WindowChrome.install(this);
    }

    private static DefaultTableModel readOnly(String... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    /** Mở dialog và tải dữ liệu tab hiện tại */
    public void open() {
        if (levelCombo.getItemCount() != Model.levels.size()) {
            levelCombo.removeAllItems();
            for (Model.LevelInfo lv : Model.levels) levelCombo.addItem(lv.name());
        }
        setVisible(true);
        selectTab(showingAll);
    }

    private void selectTab(boolean all) {
        showingAll = all;
        cards.show(area, all ? "all" : "level");
        // Tab đang chọn màu vàng, tab còn lại màu đá
        allTab.setKind(all ? Theme.Kind.PRIMARY : Theme.Kind.STONE);
        lvTab.setKind(all ? Theme.Kind.STONE : Theme.Kind.PRIMARY);
        if (all) {
            JsonObject data = new JsonObject();
            data.addProperty("kind", "ALL");
            data.addProperty("limit", 20);
            ClientApp.tcp.send(MsgType.GET_RANK, data);
        } else {
            requestLevel();
        }
    }

    private void requestLevel() {
        int idx = levelCombo.getSelectedIndex();
        if (idx < 0 || idx >= Model.levels.size() || !isVisible()) return;
        JsonObject data = new JsonObject();
        data.addProperty("kind", "LEVEL");
        data.addProperty("lv", Model.levels.get(idx).lv());
        data.addProperty("limit", 20);
        ClientApp.tcp.send(MsgType.GET_RANK, data);
    }

    /** Gọi trên EDT khi nhận RANK từ server */
    public void onRank(Msg msg) {
        boolean all = "ALL".equals(msg.str("kind"));
        DefaultTableModel model = all ? allModel : lvModel;
        model.setRowCount(0);
        for (JsonElement el : msg.data.getAsJsonArray("rows")) {
            JsonObject r = el.getAsJsonObject();
            if (all) {
                model.addRow(new Object[]{ r.get("pos").getAsInt(), r.get("name").getAsString(),
                                           r.get("done").getAsInt(), r.get("plays").getAsInt() });
            } else {
                model.addRow(new Object[]{ r.get("pos").getAsInt(), r.get("name").getAsString(),
                                           String.format("%.2f giây", r.get("sec").getAsDouble()),
                                           r.get("tries").getAsInt() });
            }
        }
    }
}
