package appeng.client.gui.me.items;

import net.minecraft.client.gui.GuiGraphics;

/** Draws the narrow recessed scrollbar track used by both pattern upload screens. */
final class PatternUploadScrollbarRenderer {
    private static final int TRACK_X_OFFSET = 3;
    private static final int TRACK_WIDTH = 6;
    private static final int TRACK_EDGE = 0xfff2f2f2;
    private static final int TRACK_TOP_SHADOW = 0xff696d88;
    private static final int TRACK_FILL = 0xff9a9fb4;

    private PatternUploadScrollbarRenderer() {
    }

    static void renderTrack(GuiGraphics guiGraphics, int scrollbarX, int y, int height) {
        var x = scrollbarX + TRACK_X_OFFSET;
        guiGraphics.fill(x, y, x + TRACK_WIDTH, y + height, TRACK_EDGE);
        guiGraphics.fill(x + 1, y + 1, x + TRACK_WIDTH - 1, y + 3, TRACK_TOP_SHADOW);
        guiGraphics.fill(x + 1, y + 3, x + TRACK_WIDTH - 1, y + height - 1, TRACK_FILL);
    }
}
