package appeng.integration.modules.jei;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;

import appeng.menu.me.items.PatternEncodingTermMenu;

class JeiPatternEncodingRecipeTransferTest {
    @Test
    void retainsTheRegisteredPatternEncodingMenuType() {
        var transfer = new JeiPatternEncodingRecipeTransfer<>(PatternEncodingTermMenu.class,
                PatternEncodingTermMenu.TYPE, null);

        assertThat(transfer.getContainerClass()).isEqualTo(PatternEncodingTermMenu.class);
        assertThat(transfer.getMenuType()).contains(PatternEncodingTermMenu.TYPE);
    }

    @Test
    void keepsEmptyCraftingSlotsForRecipeGuides() {
        var first = itemSlot(new ItemStack(Items.COBBLESTONE));
        var empty = itemSlot(ItemStack.EMPTY);
        var third = itemSlot(new ItemStack(Items.COBBLED_DEEPSLATE));
        var recipeSlots = mock(IRecipeSlotsView.class);
        when(recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)).thenReturn(List.of(first, empty, third));

        var inputs = JeiStackHelper.ofCraftingInputs(recipeSlots);

        assertThat(inputs).hasSize(3);
        assertThat(inputs.get(0)).hasSize(1);
        assertThat(inputs.get(1)).isEmpty();
        assertThat(inputs.get(2)).hasSize(1);
    }

    private static IRecipeSlotView itemSlot(ItemStack stack) {
        var slot = mock(IRecipeSlotView.class);
        when(slot.getIngredients(VanillaTypes.ITEM_STACK))
                .thenReturn(stack.isEmpty() ? Stream.empty() : Stream.of(stack));
        when(slot.getIngredients(NeoForgeTypes.FLUID_STACK)).thenReturn(Stream.empty());
        return slot;
    }
}
