package appeng.client.gui;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

public final class SearchText {
    private SearchText() {
    }

    public static Optional<String> fromItemStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }

        String displayName = stack.getHoverName().getString().trim();
        return displayName.isEmpty() ? Optional.empty() : Optional.of(displayName);
    }
}
