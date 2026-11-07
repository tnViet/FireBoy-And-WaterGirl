package fw.client.ui;

import fw.client.Model;
import fw.common.net.MsgType;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Lịch sử chơi của chính mình: chỉ các màn đã hoàn thành (bảng level_completions), mới nhất trước.
 * Gửi GET_HISTORY, server trả HISTORY -> ClientApp lưu vào Model.history rồi gọi refresh().
 */
public class HistoryDialog extends JDialog {
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Màn", "Chơi cùng", "Thời gian", "Lúc"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JLabel summary = Theme.label(" ", Theme.body(14), Theme.MUTED);
    private final CardLayout cards = new CardLayout();
    private final JPanel area = new JPanel(cards);

    // Hộp thoại lịch sử: bảng các lần hoàn thành và dòng tóm tắt; chưa có dữ liệu thì hiện lời nhắc
    public HistoryDialog(Frame owner) {
        super(owner, "Lịch sử chơi", false);
        setSize(700, 480);
        setLocationRelativeTo(owner);

        Theme.TempleBackground bg = new Theme.TempleBackground(new BorderLayout(0, 12));
        bg.setBorder(new EmptyBorder(18, 18, 18, 18));
        bg.add(Theme.label("Lịch sử chơi của bạn", Theme.display(22), Theme.GILT), BorderLayout.NORTH);

        Theme.StonePanel panel = new Theme.StonePanel(null);
        area.setOpaque(false);
        JTable table = new JTable(model);
        table.getColumnModel().getColumn(0).setPreferredWidth(240);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        area.add(Theme.table(table), "list");
        JLabel empty = Theme.label("<html><center>Bạn chưa hoàn thành màn nào.<br>"
            + "Qua được màn đầu tiên là nó sẽ hiện ở đây.</center></html>", Theme.body(15), Theme.MUTED);
        empty.setHorizontalAlignment(SwingConstants.CENTER);
        area.add(empty, "empty");
        panel.content().add(area);
        panel.add(summary, BorderLayout.SOUTH);
        bg.add(panel, BorderLayout.CENTER);

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

    /** Mở hộp thoại và xin dữ liệu mới nhất từ server */
    public void open() {
        refresh();
        setVisible(true);
        request();
    }

    public static void request() {
        JsonObject data = new JsonObject();
        data.addProperty("limit", 100);
        ClientApp.tcp.send(MsgType.GET_HISTORY, data);
    }

    /** Đổ Model.history vào bảng (gọi trên EDT) */
    public void refresh() {
        model.setRowCount(0);
        double best = Double.MAX_VALUE;
        for (Model.HistoryRow r : Model.history) {
            model.addRow(new Object[]{r.level(), r.partnerText(), String.format("%.2f giây", r.sec()), r.when()});
            best = Math.min(best, r.sec());
        }
        cards.show(area, Model.history.isEmpty() ? "empty" : "list");
        summary.setText(Model.history.isEmpty() ? " "
            : "Đã hoàn thành " + Model.history.size() + " lần. Nhanh nhất " + String.format("%.2f giây", best) + ".");
    }
}
