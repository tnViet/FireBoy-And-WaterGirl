package fw.client.render;

import java.awt.image.BufferedImage;

/**
 * Quản lý sprite sheet - chia ảnh lớn thành các frame nhỏ cho animation
 */
public class SpriteSheet {
    private final BufferedImage sheet;
    private final int frameWidth;
    private final int frameHeight;
    private final int cols;
    private final int rows;

    public SpriteSheet(BufferedImage sheet, int frameWidth, int frameHeight) {
        this.sheet = sheet;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.cols = sheet.getWidth() / frameWidth;
        this.rows = sheet.getHeight() / frameHeight;
    }

    /**
     * Lấy frame tại vị trí (col, row)
     */
    public BufferedImage getFrame(int col, int row) {
        if (col < 0 || col >= cols || row < 0 || row >= rows) {
            return null;
        }
        return sheet.getSubimage(col * frameWidth, row * frameHeight, frameWidth, frameHeight);
    }

    /**
     * Lấy frame theo index (đọc từ trái sang phải, trên xuống dưới)
     */
    public BufferedImage getFrame(int index) {
        int row = index / cols;
        int col = index % cols;
        return getFrame(col, row);
    }

    public int getTotalFrames() {
        return cols * rows;
    }

    public int getCols() {
        return cols;
    }

    public int getFrameWidth() {
        return frameWidth;
    }

    public int getFrameHeight() {
        return frameHeight;
    }
}
