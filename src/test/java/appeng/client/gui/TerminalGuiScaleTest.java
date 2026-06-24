package appeng.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TerminalGuiScaleTest {

    @Test
    void fixedTerminalScaleRequestsWindowGuiScaleDirectly() {
        assertEquals(2, TerminalGuiScale.fixed(2).getWindowGuiScale(5));
    }

    @Test
    void syncScaleKeepsMinecraftGuiScale() {
        assertEquals(5, TerminalGuiScale.sync().getWindowGuiScale(5));
    }

    @Test
    void fixedScaleIsClampedToSupportedRange() {
        assertEquals(1, TerminalGuiScale.fixed(-1).getWindowGuiScale(5));
        assertEquals(5, TerminalGuiScale.fixed(10).getWindowGuiScale(5));
    }
}
