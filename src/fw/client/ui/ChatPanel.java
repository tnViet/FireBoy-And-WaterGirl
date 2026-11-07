package fw.client.ui;

import fw.client.Model;
import fw.common.net.MsgType;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;

/**
 * Khung chat trong phòng. compact = true là bản nhỏ đặt ở góc trái trong trận.
 * Tên người gửi tô màu theo nhân vật (Lửa cam, Nước xanh).
 */
public class ChatPanel extends JPanel {
    private final JTextPane log = new JTextPane();
    private final JTextField input = Theme.field();
    private final boolean compact;
    /** Loại tin gửi đi: CHAT (trong phòng) hoặc LOBBY_CHAT (chat tổng ở sảnh) */
    private final String sendType;

    /** Trong trận: gửi xong / bấm Esc thì trả bàn phím về cho màn chơi */
    public Runnable onDone;

    public ChatPanel(boolean compact) {
        this(compact, MsgType.CHAT);
    }

    /**
     * Khung chat: ô hiện tin ở trên, ô nhập ở dưới (bản thường có thêm nút Gửi).
     * Trong trận, Enter gửi tin và Esc trả bàn phím về cho màn chơi.
     */
    public ChatPanel(boolean compact, String sendType) {
        super(new BorderLayout(0, 6));
        this.compact = compact;
        this.sendType = sendType;
        setOpaque(false);

        log.setEditable(false);
        log.setFont(Theme.body(compact ? 12 : 14));
        log.setFocusable(false);
        log.setBackground(Theme.STONE_DK);
        log.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane sp = Theme.scroll(log);
        add(sp, BorderLayout.CENTER);

        input.setFont(Theme.body(compact ? 13 : 14));
        input.addActionListener(e -> send());
        input.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "done");
        input.getActionMap().put("done", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { finish(); }
        });

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.add(input, BorderLayout.CENTER);
        if (!compact) {
            Theme.StoneButton sendBtn = Theme.button("Gửi", Theme.Kind.STONE);
            sendBtn.setPreferredSize(new Dimension(80, 38));
            sendBtn.addActionListener(e -> send());
            row.add(sendBtn, BorderLayout.EAST);
        }
        add(row, BorderLayout.SOUTH);
        setPlaceholder();
    }

    private void setPlaceholder() {
        input.setToolTipText(compact ? "Enter để chat, Esc để quay lại chơi" : "Nhập tin nhắn rồi bấm Enter");
    }

    /** Đưa con trỏ vào ô nhập (trong trận: bấm Enter để chat) */
    public void focusInput() {
        input.requestFocusInWindow();
    }

    private void send() {
        String text = input.getText().strip();
        if (!text.isEmpty() && ClientApp.tcp != null) {
            JsonObject data = new JsonObject();
            data.addProperty("text", text);
            ClientApp.tcp.send(sendType, data);
        }
        input.setText("");
        if (compact) finish();
    }

    private void finish() {
        input.setText("");
        if (onDone != null) onDone.run();
    }

    /** Thêm 1 dòng tin nhắn (gọi trên EDT) */
    public void append(Model.ChatLine line) {
        StyledDocument doc = log.getStyledDocument();
        SimpleAttributeSet who = new SimpleAttributeSet();
        Color nameColor;
        if (MsgType.LOBBY_CHAT.equals(sendType)) {
            // Chat tổng: chưa ai có nhân vật, tô tên mình màu nhấn để dễ nhận ra
            nameColor = line.acc() == Model.acc ? Theme.GILT : Theme.MUTED;
        } else {
            String el = Model.elementOf(line.acc());
            nameColor = el == null ? Theme.MUTED : Theme.elementColor(el);
        }
        StyleConstants.setForeground(who, nameColor);
        StyleConstants.setBold(who, true);
        StyleConstants.setFontFamily(who, Theme.FAMILY);
        StyleConstants.setFontSize(who, compact ? 12 : 14);
        SimpleAttributeSet msg = new SimpleAttributeSet(who);
        StyleConstants.setForeground(msg, Theme.PARCHMENT);
        StyleConstants.setBold(msg, false);
        try {
            if (doc.getLength() > 0) doc.insertString(doc.getLength(), "\n", msg);
            doc.insertString(doc.getLength(), line.name() + ": ", who);
            doc.insertString(doc.getLength(), line.text(), msg);
            // Chỉ giữ khoảng 200 dòng gần nhất
            Element root = doc.getDefaultRootElement();
            if (root.getElementCount() > 200) {
                doc.remove(0, root.getElement(root.getElementCount() - 201).getEndOffset());
            }
        } catch (BadLocationException ignored) {}
        log.setCaretPosition(doc.getLength());
    }

    /** Dòng thông báo hệ thống (ví dụ: ai đó vào/rời phòng) */
    public void system(String text) {
        StyledDocument doc = log.getStyledDocument();
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, Theme.MUTED);
        StyleConstants.setItalic(a, true);
        StyleConstants.setFontSize(a, compact ? 12 : 13);
        try {
            if (doc.getLength() > 0) doc.insertString(doc.getLength(), "\n", a);
            doc.insertString(doc.getLength(), text, a);
        } catch (BadLocationException ignored) {}
        log.setCaretPosition(doc.getLength());
    }

    public void clear() {
        log.setText("");
    }
}
