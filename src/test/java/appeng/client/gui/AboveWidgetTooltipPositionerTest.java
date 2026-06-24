package appeng.client.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.Rect2i;

class AboveWidgetTooltipPositionerTest {
    @Test
    void keepsTooltipAboveWidgetWithoutFixingHorizontalPosition() {
        var widgetArea = new Rect2i(100, 80, 18, 12);
        var positioner = new AboveWidgetTooltipPositioner(widgetArea);

        var leftMousePosition = positioner.positionTooltip(320, 240, 40, 120, 70, 28);
        var rightMousePosition = positioner.positionTooltip(320, 240, 160, 120, 70, 28);

        assertThat(leftMousePosition.x()).isEqualTo(
                DefaultTooltipPositioner.INSTANCE.positionTooltip(320, 240, 40, 120, 70, 28).x());
        assertThat(rightMousePosition.x()).isEqualTo(
                DefaultTooltipPositioner.INSTANCE.positionTooltip(320, 240, 160, 120, 70, 28).x());
        assertThat(leftMousePosition.x()).isNotEqualTo(rightMousePosition.x());

        assertThat(leftMousePosition.y() + 28).isLessThanOrEqualTo(widgetArea.getY() - 4);
        assertThat(rightMousePosition.y() + 28).isLessThanOrEqualTo(widgetArea.getY() - 4);
    }
}
