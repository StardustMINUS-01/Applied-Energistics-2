package appeng.integration.modules.itemlists;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.core.network.ServerboundPacket;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.integration.modules.gtceu.GTCEuPatternMetadataBridge;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
import appeng.parts.encoding.EncodingMode;
import appeng.util.CraftingRecipeUtil;

public final class EncodingHelper {
    private EncodingHelper() {
    }

    /**
     * Order of priority: - Craftable Items - Undamaged Items - Items the player has the most of
     */
    static final Comparator<GridInventoryEntry> ENTRY_COMPARATOR = Comparator
            .comparing(GridInventoryEntry::isCraftable)
            .thenComparing(EncodingHelper::isUndamaged)
            .thenComparing(GridInventoryEntry::getStoredAmount);

    private static Boolean isUndamaged(GridInventoryEntry entry) {
        return !(entry.getWhat() instanceof AEItemKey itemKey) || !itemKey.isDamaged();
    }

    public static void encodeProcessingRecipe(PatternEncodingTermMenu menu, List<List<GenericStack>> genericIngredients,
            List<GenericStack> genericResults) {
        encodeProcessingRecipe(menu, genericIngredients, genericResults, OptionalInt.empty());
    }

    public static void encodeProcessingRecipe(PatternEncodingTermMenu menu, List<List<GenericStack>> genericIngredients,
            List<GenericStack> genericResults, OptionalInt virtualCircuit) {
        encodeProcessingRecipe(menu, genericIngredients, genericResults, virtualCircuit, List.of());
    }

    public static void encodeProcessingRecipe(PatternEncodingTermMenu menu, List<List<GenericStack>> genericIngredients,
            List<GenericStack> genericResults, OptionalInt virtualCircuit, List<GenericStack> catalysts) {
        menu.setMode(EncodingMode.PROCESSING);
        var ingredients = createProcessingIngredients(genericIngredients, virtualCircuit, catalysts);

        // Note that this runs on the client and getClientRepo() is guaranteed to be available there.
        var ingredientPriorities = getIngredientPriorities(menu, ENTRY_COMPARATOR);

        var encodedInputs = encodeProcessingInputsIntoSlots(
                ingredients,
                ingredientPriorities,
                menu.getProcessingInputSlots());
        encodeStacksIntoSlots(
                // For the outputs, it's only one possible item per slot
                genericResults.stream().map(List::of).toList(),
                ingredientPriorities,
                menu.getProcessingOutputSlots());

        for (int slot = 0; slot < encodedInputs.size(); slot++) {
            if (encodedInputs.get(slot).role().usesVirtualInputMetadata()) {
                menu.toggleCatalyst(slot);
            }
        }
    }

    private static List<SelectedProcessingInput> encodeProcessingInputsIntoSlots(
            List<ProcessingIngredient> ingredients,
            Map<AEKey, Integer> ingredientPriorities,
            FakeSlot[] slots) {
        var selectedStacks = selectStacksPreservingSlots(
                ingredients.stream().map(ProcessingIngredient::candidates).toList(), ingredientPriorities);
        var encodedInputs = new ArrayList<SelectedProcessingInput>(selectedStacks.size());
        for (int slot = 0; slot < selectedStacks.size(); slot++) {
            var selectedStack = selectedStacks.get(slot);
            encodedInputs.add(
                    new SelectedProcessingInput(selectedStack,
                            selectedStack != null ? ingredients.get(slot).role() : ProcessingInputRole.NORMAL));
        }

        for (int slot = 0; slot < slots.length; slot++) {
            var selected = slot < encodedInputs.size() ? encodedInputs.get(slot) : null;
            var stack = selected != null && selected.stack() != null ? GenericStack.wrapInItemStack(selected.stack())
                    : ItemStack.EMPTY;
            sendFilter(slots[slot], stack);
        }
        return encodedInputs;
    }

    private static void encodeStacksIntoSlots(List<List<GenericStack>> candidatesBySlot,
            Map<AEKey, Integer> ingredientPriorities,
            FakeSlot[] slots) {
        var selectedStacks = selectStacksPreservingSlots(candidatesBySlot, ingredientPriorities);
        for (int slot = 0; slot < slots.length; slot++) {
            var selectedStack = slot < selectedStacks.size() ? selectedStacks.get(slot) : null;
            var stack = selectedStack != null ? GenericStack.wrapInItemStack(selectedStack) : ItemStack.EMPTY;
            sendFilter(slots[slot], stack);
        }
    }

    static List<@Nullable GenericStack> selectStacksPreservingSlots(List<List<GenericStack>> candidatesBySlot,
            Map<AEKey, Integer> ingredientPriorities) {
        var selectedStacks = new ArrayList<@Nullable GenericStack>(candidatesBySlot.size());
        for (var candidates : candidatesBySlot) {
            selectedStacks.add(candidates.isEmpty() ? null : findBestIngredient(ingredientPriorities, candidates));
        }
        return selectedStacks;
    }

    private static List<ProcessingIngredient> createProcessingIngredients(List<List<GenericStack>> genericIngredients,
            OptionalInt virtualCircuit, List<GenericStack> catalysts) {
        return createProcessingIngredients(
                genericIngredients,
                virtualCircuit,
                catalysts,
                (stack, circuit) -> GTCEuPatternMetadataBridge.getCircuitConfiguration(stack).orElse(-1) == circuit);
    }

    static List<ProcessingIngredient> createProcessingIngredients(List<List<GenericStack>> genericIngredients,
            OptionalInt virtualCircuit, List<GenericStack> catalysts,
            BiPredicate<GenericStack, Integer> isVirtualCircuit) {
        var ingredients = new ArrayList<ProcessingIngredient>(genericIngredients.size() + catalysts.size() + 1);
        for (var candidates : genericIngredients) {
            ingredients.add(new ProcessingIngredient(List.copyOf(candidates), ProcessingInputRole.NORMAL));
        }

        for (var catalyst : catalysts) {
            if (virtualCircuit.isEmpty() || !isVirtualCircuit.test(catalyst, virtualCircuit.getAsInt())) {
                markExistingOrAppendCatalyst(ingredients, catalyst);
            }
        }

        if (virtualCircuit.isPresent()) {
            markExistingOrAppendVirtualCircuit(ingredients, virtualCircuit.getAsInt(), isVirtualCircuit);
        }

        return ingredients;
    }

    private static void markExistingOrAppendCatalyst(List<ProcessingIngredient> ingredients, GenericStack catalyst) {
        for (int slot = 0; slot < ingredients.size(); slot++) {
            var ingredient = ingredients.get(slot);
            if (ingredient.role() == ProcessingInputRole.NORMAL
                    && ingredient.candidates().stream().anyMatch(catalyst::equals)) {
                ingredients.set(slot, new ProcessingIngredient(List.of(catalyst), ProcessingInputRole.CATALYST));
                return;
            }
        }

        ingredients.add(new ProcessingIngredient(List.of(catalyst), ProcessingInputRole.CATALYST));
    }

    private static void markExistingOrAppendVirtualCircuit(List<ProcessingIngredient> ingredients, int circuit,
            BiPredicate<GenericStack, Integer> isVirtualCircuit) {
        for (int slot = 0; slot < ingredients.size(); slot++) {
            var ingredient = ingredients.get(slot);
            for (var candidate : ingredient.candidates()) {
                if (isVirtualCircuit.test(candidate, circuit)) {
                    ingredients.set(slot,
                            new ProcessingIngredient(List.of(candidate), ProcessingInputRole.VIRTUAL_CIRCUIT));
                    return;
                }
            }
        }

        var displayStack = GTCEuPatternMetadataBridge.getVirtualCircuitDisplayStack(circuit);
        if (displayStack != null) {
            var genericStack = GenericStack.fromItemStack(displayStack);
            if (genericStack != null) {
                ingredients.add(new ProcessingIngredient(List.of(genericStack), ProcessingInputRole.VIRTUAL_CIRCUIT));
            }
        }
    }

    private static void sendFilter(FakeSlot slot, ItemStack stack) {
        ServerboundPacket message = new InventoryActionPacket(InventoryAction.SET_FILTER, slot.index, stack);
        PacketDistributor.sendToServer(message);
    }

    private record ProcessingIngredient(List<GenericStack> candidates, ProcessingInputRole role) {
    }

    private record SelectedProcessingInput(@Nullable GenericStack stack, ProcessingInputRole role) {
    }

    private enum ProcessingInputRole {
        NORMAL,
        CATALYST,
        VIRTUAL_CIRCUIT;

        boolean usesVirtualInputMetadata() {
            return this != NORMAL;
        }
    }

    public static boolean isSupportedCraftingRecipe(@Nullable Recipe<?> recipe) {
        if (recipe == null) {
            return false;
        }
        var recipeType = recipe.getType();

        return recipeType == RecipeType.CRAFTING
                || recipeType == RecipeType.STONECUTTING
                || recipeType == RecipeType.SMITHING;
    }

    public static void encodeCraftingRecipe(PatternEncodingTermMenu menu,
            @Nullable RecipeHolder<?> recipe,
            List<List<GenericStack>> genericIngredients,
            Predicate<ItemStack> visiblePredicate) {
        if (recipe != null && recipe.value().getType().equals(RecipeType.STONECUTTING)) {
            menu.setMode(EncodingMode.STONECUTTING);
            menu.setStonecuttingRecipeId(recipe.id());
        } else if (recipe != null && recipe.value().getType().equals(RecipeType.SMITHING)) {
            menu.setMode(EncodingMode.SMITHING_TABLE);
        } else {
            menu.setMode(EncodingMode.CRAFTING);
        }

        // Note that this runs on the client and getClientRepo() is guaranteed to be available there.
        var prioritizedNetworkInv = getIngredientPriorities(menu, ENTRY_COMPARATOR);

        var encodedInputs = NonNullList.withSize(menu.getCraftingGridSlots().length, ItemStack.EMPTY);

        if (recipe != null) {
            // When we have access to a crafting recipe, we'll switch modes and try to find suitable
            // ingredients based on the recipe ingredients, which allows for fuzzy-matching.
            var ingredients3x3 = CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe.value());

            // Find a good match for every ingredient
            for (int slot = 0; slot < ingredients3x3.size(); slot++) {
                var ingredient = ingredients3x3.get(slot);
                if (ingredient.isEmpty()) {
                    continue; // Skip empty slots
                }

                var guidedIngredient = getGuidedIngredient(genericIngredients, slot, ingredient);
                if (guidedIngredient != null) {
                    encodedInputs.set(slot, guidedIngredient);
                    continue;
                }

                // Due to how some crafting recipes work, the ingredient can match more than just one item in the
                // network inventory. We'll find all network inventory entries that it matches and sort them
                // according to their suitability for encoding a pattern
                var bestNetworkIngredient = prioritizedNetworkInv.entrySet().stream()
                        .filter(ni -> ni.getKey() instanceof AEItemKey itemKey && itemKey.matches(ingredient))
                        .max(Comparator.comparingInt(Map.Entry::getValue))
                        .map(entry -> entry.getKey() instanceof AEItemKey itemKey ? itemKey.toStack() : null);

                // To avoid encoding hidden entries, we'll cycle through the ingredient and try to find a visible
                // stack, otherwise we'll use the first entry.
                var bestIngredient = bestNetworkIngredient.orElseGet(() -> {
                    for (var stack : ingredient.getItems()) {
                        if (visiblePredicate.test(stack)) {
                            return stack;
                        }
                    }
                    return ingredient.getItems()[0];
                });

                encodedInputs.set(slot, bestIngredient);
            }
        } else {
            for (int slot = 0; slot < genericIngredients.size(); slot++) {
                var genericIngredient = genericIngredients.get(slot);
                if (genericIngredient.isEmpty()) {
                    continue; // Skip empty slots
                }

                var bestIngredient = findBestIngredient(prioritizedNetworkInv, genericIngredient).what();

                // Clamp amounts to 1 in crafting table mode
                if (bestIngredient instanceof AEItemKey itemKey) {
                    encodedInputs.set(slot, itemKey.toStack());
                } else {
                    encodedInputs.set(slot, GenericStack.wrapInItemStack(bestIngredient, 1));
                }
            }
        }

        for (int i = 0; i < encodedInputs.size(); i++) {
            ItemStack encodedInput = encodedInputs.get(i);
            ServerboundPacket message = new InventoryActionPacket(
                    InventoryAction.SET_FILTER, menu.getCraftingGridSlots()[i].index, encodedInput);
            PacketDistributor.sendToServer(message);
        }

        // Clear out the processing outputs
        for (var outputSlot : menu.getProcessingOutputSlots()) {
            ServerboundPacket message = new InventoryActionPacket(
                    InventoryAction.SET_FILTER, outputSlot.index, ItemStack.EMPTY);
            PacketDistributor.sendToServer(message);
        }

    }

    @Nullable
    private static ItemStack getGuidedIngredient(List<List<GenericStack>> genericIngredients, int slot,
            net.minecraft.world.item.crafting.Ingredient ingredient) {
        if (slot >= genericIngredients.size()) {
            return null;
        }
        var candidates = genericIngredients.get(slot);
        if (candidates.size() != 1 || !(candidates.getFirst().what() instanceof AEItemKey itemKey)) {
            return null;
        }
        var guide = itemKey.toStack();
        return itemKey.matches(ingredient) ? guide : null;
    }

    // Given a set of possible ingredients, find the one that has the highest priority
    private static GenericStack findBestIngredient(Map<AEKey, Integer> ingredientPriorities,
            List<GenericStack> possibleIngredients) {
        return possibleIngredients.stream()
                .map(gi -> Pair.of(gi, ingredientPriorities.getOrDefault(gi.what(), Integer.MIN_VALUE)))
                .max(Comparator.comparingInt(Pair::getRight))
                .map(Pair::getLeft)
                .orElseThrow();
    }

    /**
     * Compute a map from all keys in the network inventory to their position when sorted by priority. Also takes the
     * player inventory into account for any items that are not already in the grid.
     * <p/>
     * Higher means higher priority.
     */
    public static Map<AEKey, Integer> getIngredientPriorities(MEStorageMenu menu,
            Comparator<GridInventoryEntry> comparator) {
        var orderedEntries = menu.getClientRepo().getAllEntries()
                .stream()
                .sorted(comparator)
                .map(GridInventoryEntry::getWhat)
                .toList();

        var result = new HashMap<AEKey, Integer>(orderedEntries.size());
        for (int i = 0; i < orderedEntries.size(); i++) {
            result.put(orderedEntries.get(i), i);
        }

        // Also consider the player inventory, but only as the last resort
        for (var item : menu.getPlayerInventory().items) {
            var key = AEItemKey.of(item);
            if (key != null) {
                // Use -1 as lower priority than the lowest network entry (which start at 0)
                result.putIfAbsent(key, -1);
            }
        }

        return result;
    }
}
