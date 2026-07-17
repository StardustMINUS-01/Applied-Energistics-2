package appeng.integration.modules.jei;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;

import appeng.api.stacks.GenericStack;

final class JeiStackHelper {
    private JeiStackHelper() {
    }

    static List<List<GenericStack>> ofInputs(IRecipeSlotsView recipeSlots) {
        return recipeSlots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                .map(JeiStackHelper::ofSlot)
                .filter(stacks -> !stacks.isEmpty())
                .toList();
    }

    static List<List<GenericStack>> ofCraftingInputs(IRecipeSlotsView recipeSlots) {
        return recipeSlots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                .map(JeiStackHelper::ofSlot)
                .toList();
    }

    static List<GenericStack> ofOutputs(IRecipeSlotsView recipeSlots) {
        return recipeSlots.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .map(JeiStackHelper::firstStackOfSlot)
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<GenericStack> ofSlot(IRecipeSlotView slot) {
        return java.util.stream.Stream.concat(
                slot.getIngredients(VanillaTypes.ITEM_STACK).map(JeiStackHelper::fromItemStack),
                slot.getIngredients(NeoForgeTypes.FLUID_STACK).map(JeiStackHelper::fromFluidStack))
                .filter(Objects::nonNull)
                .toList();
    }

    @Nullable
    private static GenericStack firstStackOfSlot(IRecipeSlotView slot) {
        return ofSlot(slot).stream().findFirst().orElse(null);
    }

    @Nullable
    private static GenericStack fromItemStack(ItemStack stack) {
        return GenericStack.fromItemStack(stack.copy());
    }

    @Nullable
    private static GenericStack fromFluidStack(FluidStack stack) {
        return GenericStack.fromFluidStack(stack.copy());
    }
}
