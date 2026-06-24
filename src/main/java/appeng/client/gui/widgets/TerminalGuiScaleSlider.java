package appeng.client.gui.widgets;

import java.util.function.IntConsumer;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.TerminalGuiScale;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;

public class TerminalGuiScaleSlider implements ICompositeWidget {
    private static final int HANDLE_WIDTH = 8;
    private static final int TRACK_HEIGHT = 6;
    private static final int LABEL_WIDTH = 96;
    private static final int TEXT_TRACK_GAP = 8;
    private static final ResourceLocation HANDLE = AppEng.makeId("big_scroller");

    private final IntConsumer changeListener;
    private int x;
    private int y;
    private int width;
    private int height = 20;
    private int selectedScale;
    private boolean dragging;

    public TerminalGuiScaleSlider(int selectedScale, IntConsumer changeListener) {
        this.changeListener = changeListener;
        this.selectedScale = TerminalGuiScale.clamp(selectedScale);
    }

    public int getSelectedScale() {
        return selectedScale;
    }

    public void setSelectedScale(int selectedScale) {
        this.selectedScale = TerminalGuiScale.clamp(selectedScale);
    }

    @Override
    public Rect2i getBounds() {
        return new Rect2i(x, y, width, height);
    }

    @Override
    public void setPosition(Point position) {
        this.x = position.getX();
        this.y = position.getY();
    }

    @Override
    public void setSize(int width, int height) {
        if (width != 0) {
            this.width = width;
        }
        if (height != 0) {
            this.height = height;
        }
    }

    @Override
    public void drawForegroundLayer(GuiGraphics guiGraphics, Rect2i bounds, Point mouse) {
        var minecraft = Minecraft.getInstance();
        var font = minecraft.font;
        var message = getMessage();

        int labelWidth = getLabelWidth(width);
        int textY = y + (height - font.lineHeight) / 2 + 1;
        guiGraphics.drawString(font, message, x, textY, 0x413f54, false);

        int trackMinX = x + labelWidth + TEXT_TRACK_GAP;
        int trackMaxX = x + width - HANDLE_WIDTH;
        if (trackMaxX <= trackMinX) {
            return;
        }

        int trackY = y + (height - TRACK_HEIGHT) / 2;
        drawTrack(guiGraphics, trackMinX, trackMaxX + HANDLE_WIDTH, trackY);

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        int handleX = handleXForScale(trackMinX, trackMaxX, HANDLE_WIDTH, selectedScale);
        guiGraphics.blitSprite(HANDLE, handleX, y + 1, HANDLE_WIDTH, height - 2);
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }

        dragging = true;
        updateFromMouse(mousePos.getX());
        return true;
    }

    @Override
    public boolean onMouseDrag(Point mousePos, int button) {
        if (!dragging || button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }

        updateFromMouse(mousePos.getX());
        return true;
    }

    @Override
    public boolean onMouseUp(Point mousePos, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = false;
        }
        return false;
    }

    @Override
    public boolean wantsAllMouseUpEvents() {
        return true;
    }

    public static int scaleFromMouseX(int minX, int maxX, int handleWidth, double mouseX) {
        int availableWidth = Math.max(1, maxX - minX);
        double position = Mth.clamp((mouseX - minX) / availableWidth, 0.0, 1.0);
        return TerminalGuiScale.clamp((int) Math.round(position * TerminalGuiScale.MAX_FIXED_SCALE));
    }

    public static int handleXForScale(int minX, int maxX, int handleWidth, int scale) {
        int availableWidth = Math.max(1, maxX - minX);
        double position = TerminalGuiScale.clamp(scale) / (double) TerminalGuiScale.MAX_FIXED_SCALE;
        return minX + (int) Math.round(position * availableWidth);
    }

    private Component getMessage() {
        Component scaleLabel = selectedScale == TerminalGuiScale.SYNC_WITH_MINECRAFT
                ? GuiText.TerminalSettingsGuiScaleSync.text()
                : Component.literal(selectedScale + "x");
        return GuiText.TerminalSettingsGuiScale.text(scaleLabel);
    }

    private void updateFromMouse(double mouseX) {
        int trackMinX = getTrackMinX();
        int trackMaxX = getTrackMaxX();
        setSelectedScale(scaleFromMouseX(trackMinX, trackMaxX, HANDLE_WIDTH, mouseX), true);
    }

    private int getTrackMinX() {
        return x + getLabelWidth(width) + TEXT_TRACK_GAP;
    }

    private int getTrackMaxX() {
        return x + width - HANDLE_WIDTH;
    }

    private void setSelectedScale(int selectedScale, boolean notify) {
        int newScale = TerminalGuiScale.clamp(selectedScale);
        if (newScale == this.selectedScale) {
            return;
        }

        this.selectedScale = newScale;
        if (notify) {
            changeListener.accept(this.selectedScale);
        }
    }

    private static void drawTrack(GuiGraphics guiGraphics, int minX, int maxX, int y) {
        guiGraphics.fill(minX, y, maxX, y + TRACK_HEIGHT, 0xff56556d);
        guiGraphics.fill(minX + 1, y + 1, maxX - 1, y + TRACK_HEIGHT - 1, 0xffc7c9d5);
        guiGraphics.fill(minX + 3, y + 1, maxX - 3, y + TRACK_HEIGHT - 1, 0xfff4f6ff);
    }

    private static int getLabelWidth(int width) {
        return Math.min(LABEL_WIDTH, Math.max(0, width / 2));
    }
}
