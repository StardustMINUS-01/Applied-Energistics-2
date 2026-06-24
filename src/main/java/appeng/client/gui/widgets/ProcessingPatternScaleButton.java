package appeng.client.gui.widgets;

import java.util.List;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.core.localization.ButtonToolTips;

public class ProcessingPatternScaleButton extends AE2Button implements ITooltip {
    private final Component label;

    public ProcessingPatternScaleButton(int factor, Runnable onPress) {
        super(Component.literal(label(factor)), button -> onPress.run());
        this.label = Component.literal(label(factor));
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                ButtonToolTips.ScaleProcessingPattern.text(),
                ButtonToolTips.ScaleProcessingPatternHint.text(label));
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), getWidth(), getHeight());
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return visible;
    }

    private static String label(int factor) {
        if (factor > 0) {
            return "x" + factor;
        }
        return "\u00f7" + -factor;
    }
}
