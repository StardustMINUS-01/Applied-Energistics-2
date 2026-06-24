package appeng.client.gui;

public final class TerminalGuiScale {
    public static final int SYNC_WITH_MINECRAFT = 0;
    public static final int MIN_FIXED_SCALE = 1;
    public static final int MAX_FIXED_SCALE = 5;

    private final int fixedScale;

    private TerminalGuiScale(int fixedScale) {
        this.fixedScale = fixedScale;
    }

    public static TerminalGuiScale sync() {
        return new TerminalGuiScale(SYNC_WITH_MINECRAFT);
    }

    public static TerminalGuiScale fixed(int scale) {
        return new TerminalGuiScale(clampFixed(scale));
    }

    public static TerminalGuiScale of(int scale) {
        return new TerminalGuiScale(clamp(scale));
    }

    public boolean isSynced() {
        return fixedScale == SYNC_WITH_MINECRAFT;
    }

    public int fixedScale() {
        return fixedScale;
    }

    public int getWindowGuiScale(int minecraftGuiScale) {
        return isSynced() ? Math.max(1, minecraftGuiScale) : fixedScale;
    }

    public static int clamp(int scale) {
        if (scale <= SYNC_WITH_MINECRAFT) {
            return SYNC_WITH_MINECRAFT;
        }
        return Math.min(MAX_FIXED_SCALE, Math.max(MIN_FIXED_SCALE, scale));
    }

    private static int clampFixed(int scale) {
        return Math.min(MAX_FIXED_SCALE, Math.max(MIN_FIXED_SCALE, scale));
    }
}
