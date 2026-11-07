package fw.client;

import fw.client.ui.ClientApp;

/**
 * Điểm chạy client. Địa chỉ server đọc từ src/fw/client/resources/client.properties
 * (hoặc ghi đè bằng -Dserver.host=192.168.x.x).
 */
public class ClientMain {
    public static void main(String[] args) {
        ClientApp.main(args);
    }
}
