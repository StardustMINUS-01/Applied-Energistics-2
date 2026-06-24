package appeng.client.gui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import appeng.client.Point;
import appeng.client.gui.TerminalGuiScale;

class TerminalGuiScaleSliderTest {

    @Test
    void snapsClickPositionsToDiscreteScaleSteps() {
        assertEquals(TerminalGuiScale.SYNC_WITH_MINECRAFT,
                TerminalGuiScaleSlider.scaleFromMouseX(10, 110, 8, 10));
        assertEquals(1, TerminalGuiScaleSlider.scaleFromMouseX(10, 110, 8, 30));
        assertEquals(3, TerminalGuiScaleSlider.scaleFromMouseX(10, 110, 8, 68));
        assertEquals(5, TerminalGuiScaleSlider.scaleFromMouseX(10, 110, 8, 110));
    }

    @Test
    void mapsScaleStepsBackToHandlePositions() {
        assertEquals(10, TerminalGuiScaleSlider.handleXForScale(10, 110, 8,
                TerminalGuiScale.SYNC_WITH_MINECRAFT));
        assertEquals(50, TerminalGuiScaleSlider.handleXForScale(10, 110, 8, 2));
        assertEquals(110, TerminalGuiScaleSlider.handleXForScale(10, 110, 8, 5));
    }

    @Test
    void draggingThroughCompositeWidgetUpdatesScale() {
        int[] changedTo = { -1 };
        var slider = new TerminalGuiScaleSlider(TerminalGuiScale.SYNC_WITH_MINECRAFT, scale -> changedTo[0] = scale);
        slider.setPosition(new Point(10, 20));
        slider.setSize(180, 20);

        slider.onMouseDown(new Point(120, 25), 0);
        slider.onMouseDrag(new Point(182, 25), 0);

        assertEquals(5, slider.getSelectedScale());
        assertEquals(5, changedTo[0]);
    }
}
