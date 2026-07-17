package appeng.integration.modules.itemlists;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.BiPredicate;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class EncodingHelperTest {

    @Test
    void selectedProcessingInputsPreserveDuplicateStacksAndEmptySlots() {
        var first = GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        var duplicate = GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT));
        Map<AEKey, Integer> priorities = Map.of(first.what(), 1, duplicate.what(), 2);

        var selected = EncodingHelper.selectStacksPreservingSlots(
                List.of(List.of(first), List.of(duplicate), List.of(duplicate), List.of(), List.of(first)),
                priorities);

        assertThat(selected).containsExactly(first, duplicate, duplicate, null, first);
    }

    @Test
    void processingInputsKeepFluidCatalystAndVirtualCircuitSeparate() {
        var circuit = GenericStack.fromItemStack(new ItemStack(Items.COMPARATOR));
        var realInput = GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        var fluidCatalyst = GenericStack.fromFluidStack(new FluidStack(Fluids.WATER, 100));

        assertThatCode(() -> {
            var ingredients = createProcessingIngredients(
                    List.of(List.of(circuit), List.of(realInput), List.of(fluidCatalyst)),
                    OptionalInt.of(3),
                    List.of(circuit, fluidCatalyst),
                    (stack, configuration) -> stack.equals(circuit) && configuration == 3);

            assertThat(ingredients).hasSize(3);
            assertProcessingIngredient(ingredients.get(0), circuit, "VIRTUAL_CIRCUIT");
            assertProcessingIngredient(ingredients.get(1), realInput, "NORMAL");
            assertProcessingIngredient(ingredients.get(2), fluidCatalyst, "CATALYST");
        }).doesNotThrowAnyException();
    }

    @Test
    void singletonCraftingSlotGuideSelectsItsLegalCandidate() throws ReflectiveOperationException {
        var cobbledDeepslate = GenericStack.fromItemStack(new ItemStack(Items.COBBLED_DEEPSLATE));
        var ingredient = Ingredient.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE);

        var guide = getGuidedIngredient(List.of(List.of(cobbledDeepslate)), 0, ingredient);

        assertThat(guide).isNotNull();
        assertThat(guide.getItem()).isEqualTo(Items.COBBLED_DEEPSLATE);
    }

    @SuppressWarnings("unchecked")
    private static List<?> createProcessingIngredients(
            List<List<GenericStack>> genericIngredients,
            OptionalInt virtualCircuit,
            List<GenericStack> catalysts,
            BiPredicate<GenericStack, Integer> isVirtualCircuit) throws ReflectiveOperationException {
        Method method = EncodingHelper.class.getDeclaredMethod(
                "createProcessingIngredients",
                List.class,
                OptionalInt.class,
                List.class,
                BiPredicate.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(null, genericIngredients, virtualCircuit, catalysts, isVirtualCircuit);
    }

    private static ItemStack getGuidedIngredient(List<List<GenericStack>> genericIngredients, int slot,
            Ingredient ingredient) throws ReflectiveOperationException {
        Method method = EncodingHelper.class.getDeclaredMethod(
                "getGuidedIngredient", List.class, int.class, Ingredient.class);
        method.setAccessible(true);
        return (ItemStack) method.invoke(null, genericIngredients, slot, ingredient);
    }

    @SuppressWarnings("unchecked")
    private static void assertProcessingIngredient(Object ingredient, GenericStack expectedStack, String expectedRole)
            throws ReflectiveOperationException {
        Method candidates = ingredient.getClass().getDeclaredMethod("candidates");
        Method role = ingredient.getClass().getDeclaredMethod("role");
        candidates.setAccessible(true);
        role.setAccessible(true);

        assertThat((List<GenericStack>) candidates.invoke(ingredient)).containsExactly(expectedStack);
        assertThat(role.invoke(ingredient).toString()).isEqualTo(expectedRole);
    }
}
