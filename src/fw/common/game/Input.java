package fw.common.game;

public record Input(boolean l, boolean r, boolean j) {
    public static final Input NONE = new Input(false, false, false);
}
