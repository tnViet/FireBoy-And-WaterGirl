package fw.client.render;

import java.awt.image.BufferedImage;

/**
 * Animation - quản lý các frame và thời gian chuyển đổi
 */
public class Animation {
    private final BufferedImage[] frames;
    // số tick giữa mỗi frame
    private final int frameDelay;
    private int currentFrame;
    private int tickCounter;
    private boolean loop;

    public Animation(BufferedImage[] frames, int frameDelay, boolean loop) {
        this.frames = frames;
        this.frameDelay = frameDelay;
        this.loop = loop;
        this.currentFrame = 0;
        this.tickCounter = 0;
    }

    /**
     * Update animation - gọi mỗi frame
     */
    public void update() {
        tickCounter++;
        if (tickCounter >= frameDelay) {
            tickCounter = 0;
            currentFrame++;
            if (currentFrame >= frames.length) {
                if (loop) {
                    currentFrame = 0;
                } else {
                    currentFrame = frames.length - 1;
                }
            }
        }
    }

    /**
     * Lấy frame hiện tại
     */
    public BufferedImage getCurrentFrame() {
        if (frames == null || frames.length == 0) return null;
        return frames[currentFrame];
    }

    /**
     * Reset animation về frame đầu
     */
    public void reset() {
        currentFrame = 0;
        tickCounter = 0;
    }

    /**
     * Kiểm tra animation đã chạy xong chưa (nếu không loop)
     */
    public boolean isFinished() {
        return !loop && currentFrame == frames.length - 1;
    }
}
