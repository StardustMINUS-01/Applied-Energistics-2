package appeng.client.gui;

import org.joml.Vector2i;
import org.joml.Vector2ic;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.Rect2i;

public class AboveWidgetTooltipPositioner implements ClientTooltipPositioner {
    private static final int GAP = 4;

    private final Rect2i widgetArea;

    public AboveWidgetTooltipPositioner(Rect2i widgetArea) {
        this.widgetArea = widgetArea;
    }

    @Override
    public Vector2ic positionTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth,
            int tooltipHeight) {
        var defaultPosition = DefaultTooltipPositioner.INSTANCE.positionTooltip(screenWidth, screenHeight, mouseX,
                mouseY, tooltipWidth, tooltipHeight);
        var tooltipY = widgetArea.getY() - GAP - tooltipHeight;
        return new Vector2i(defaultPosition.x(), tooltipY);
    }
}
