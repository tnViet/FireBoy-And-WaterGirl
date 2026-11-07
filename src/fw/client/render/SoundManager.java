package fw.client.render;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * SoundManager - quản lý sound effects cho game
 *
 * Mỗi âm thanh có tên: death, key, door, lever, win, mech.
 * Nếu có file src/fw/client/resources/sounds/<tên>.wav thì dùng file đó,
 * không thì dùng âm thanh tổng hợp (sine wave) làm placeholder.
 */
public class SoundManager {
    private static final String SOUND_DIR = "/fw/client/resources/sounds/";
    private static final float SAMPLE_RATE = 22050;
    private static final AudioFormat PCM_FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    /** Dữ liệu âm thanh đã giải mã + định dạng của nó */
    private record Sound(byte[] data, AudioFormat format) {}

    private final Map<String, Sound> sounds = new HashMap<>();
    private boolean enabled = true;

    public SoundManager() {
        generatePlaceholderSounds();
        for (String name : sounds.keySet().toArray(new String[0])) {
            loadSound(name, SOUND_DIR + name + ".wav");
        }
    }

    /**
     * Tạo placeholder sounds đơn giản (sine wave)
     */
    private void generatePlaceholderSounds() {
        // trầm dần xuống
        sounds.put("death", new Sound(generateSweep(400, 120, 0.35, 0.5), PCM_FORMAT));
        // "ting" vút lên
        sounds.put("key",   new Sound(generateSweep(800, 1400, 0.15, 0.3), PCM_FORMAT));
        sounds.put("door",  new Sound(generateSweep(300, 600, 0.25, 0.4), PCM_FORMAT));
        // click ngắn
        sounds.put("lever", new Sound(generateSweep(600, 600, 0.08, 0.3), PCM_FORMAT));
        sounds.put("win",   new Sound(generateSweep(500, 1000, 0.5, 0.5), PCM_FORMAT));
        // tiếng máy chạy ù ù
        sounds.put("mech",  new Sound(generateSweep(150, 220, 0.3, 0.35), PCM_FORMAT));
    }

    /**
     * Sinh sóng sine có tần số trượt từ f0 tới f1, âm lượng giảm dần (fade out)
     */
    private byte[] generateSweep(double f0, double f1, double duration, double volume) {
        int numSamples = (int) (SAMPLE_RATE * duration);
        // 16-bit = 2 bytes / sample
        byte[] buffer = new byte[numSamples * 2];
        double phase = 0;

        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / numSamples;
            double freq = f0 + (f1 - f0) * t;
            // cộng dồn pha để không bị "vỡ" tiếng khi đổi tần số
            phase += 2.0 * Math.PI * freq / SAMPLE_RATE;
            double sample = Math.sin(phase) * volume * (1.0 - t);

            // Đổi sang PCM 16-bit little-endian
            short pcm = (short) (sample * 32767);
            buffer[i * 2]     = (byte) (pcm & 0xFF);
            buffer[i * 2 + 1] = (byte) ((pcm >> 8) & 0xFF);
        }
        return buffer;
    }

    /**
     * Load file WAV từ classpath, thay thế placeholder cùng tên. Không có file thì bỏ qua.
     */
    public void loadSound(String name, String resourcePath) {
        InputStream raw = getClass().getResourceAsStream(resourcePath);
        if (raw == null) return;
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
            sounds.put(name, new Sound(ais.readAllBytes(), ais.getFormat()));
            System.out.println("[SOUND] Loaded " + resourcePath);
        } catch (Exception e) {
            System.err.println("[SOUND] Failed to load " + resourcePath + ": " + e.getMessage());
        }
    }

    /**
     * Phát âm thanh theo tên (chạy trên thread riêng để không chặn vòng lặp vẽ)
     */
    public void play(String soundName) {
        if (!enabled) return;

        Sound sound = sounds.get(soundName);
        if (sound == null) {
            System.err.println("[SOUND] Unknown sound: " + soundName);
            return;
        }

        Thread t = new Thread(() -> {
            try {
                playClip(sound);
            } catch (Exception e) {
                System.err.println("[SOUND] Error playing " + soundName + ": " + e.getMessage());
            }
        }, "sound-" + soundName);
        // không giữ JVM sống khi đóng game
        t.setDaemon(true);
        t.start();
    }

    private void playClip(Sound sound) throws Exception {
        DataLine.Info info = new DataLine.Info(Clip.class, sound.format());
        // máy không có thiết bị âm thanh
        if (!AudioSystem.isLineSupported(info)) return;

        Clip clip = (Clip) AudioSystem.getLine(info);
        clip.addLineListener(event -> {
            // tự giải phóng khi phát xong
            if (event.getType() == LineEvent.Type.STOP) clip.close();
        });
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(sound.data()), sound.format(),
                sound.data().length / sound.format().getFrameSize())) {
            clip.open(stream);
        }
        clip.start();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
