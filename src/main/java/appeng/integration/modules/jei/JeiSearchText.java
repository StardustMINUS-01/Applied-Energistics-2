package appeng.integration.modules.jei;

import java.util.Optional;

import net.minecraft.world.item.ItemStack;

import mezz.jei.api.ingredients.ITypedIngredient;

import appeng.client.gui.SearchText;

final class JeiSearchText {
    private JeiSearchText() {
    }

    static Optional<String> fromTypedIngredient(ITypedIngredient<?> ingredient) {
        if (ingredient.getIngredient() instanceof ItemStack stack) {
            return fromItemStack(stack);
        }
        return Optional.empty();
    }

    static Optional<String> fromItemStack(ItemStack stack) {
        return SearchText.fromItemStack(stack);
    }
}
