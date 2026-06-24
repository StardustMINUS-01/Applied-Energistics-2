package appeng.client.gui;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;

import appeng.api.stacks.GenericStack;

/**
 * A stack that is rendered including its absolute on-screen bounds.
 */
public record StackWithBounds(GenericStack stack, Rect2i bounds) {
    @Nullable
    public static StackWithBounds fromSlot(AEBaseScreen<?> screen, Slot slot) {
        var item = slot.getItem();
        var stack = GenericStack.unwrapItemStack(item);
        if (stack != null) {
            return new StackWithBounds(
                    stack,
                    screen.toMinecraftRect(screen.getVirtualGuiLeft() + slot.x, screen.getVirtualGuiTop() + slot.y,
                            16, 16));
        }
        return null;
    }
}
