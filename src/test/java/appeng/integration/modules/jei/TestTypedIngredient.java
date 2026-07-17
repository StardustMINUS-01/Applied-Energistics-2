package appeng.integration.modules.jei;

import net.minecraft.world.item.ItemStack;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;

record TestTypedIngredient<T>(T ingredient) implements ITypedIngredient<T> {
    @Override
    public IIngredientType<T> getType() {
        if (ingredient instanceof ItemStack) {
            @SuppressWarnings("unchecked")
            IIngredientType<T> type = (IIngredientType<T>) VanillaTypes.ITEM_STACK;
            return type;
        }
        throw new IllegalStateException("Unsupported test ingredient: " + ingredient);
    }

    @Override
    public T getIngredient() {
        return ingredient;
    }
}
