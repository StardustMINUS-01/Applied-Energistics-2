package appeng.integration.jei.patternencoding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.integration.modules.gtceu.GTCEuPatternMetadataBridge;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.util.inv.PlayerInternalInventory;

public final class RecipeChainPatternEncodingService {
    private RecipeChainPatternEncodingService() {
    }

    public static BatchPatternEncodeResult encodeRecipeChainPatterns(ServerPlayer player,
            List<PatternEncodeRequest> requests) {
        if (!(player.containerMenu instanceof PatternEncodingTermMenu menu)) {
            return stoppedBeforeProcessing(requests, PatternEncodeStopReason.NOT_PATTERN_ENCODING_TERMINAL);
        }
        return encodeRecipeChainPatterns(player, menu, requests);
    }

    public static BatchPatternEncodeResult encodeRecipeChainPatterns(ServerPlayer player, PatternEncodingTermMenu menu,
            List<PatternEncodeRequest> requests) {
        int encodedCount = 0;
        int skippedExistingCount = 0;
        int skippedInvalidCount = 0;
        var stopReason = PatternEncodeStopReason.NONE;
        var entries = new ArrayList<PatternEncodeEntryResult>(requests.size());
        var preparedEntries = prepareEntries(player, requests);

        for (var preparedEntry : preparedEntries) {
            if (preparedEntry.status == PatternEncodeEntryStatus.SKIPPED_EXISTING_PRIMARY_OUTPUT) {
                skippedExistingCount++;
            } else if (preparedEntry.status == PatternEncodeEntryStatus.SKIPPED_INVALID_RECIPE
                    || preparedEntry.status == PatternEncodeEntryStatus.SKIPPED_INVALID_PATTERN) {
                skippedInvalidCount++;
            }
        }

        var writableCount = countWritable(preparedEntries);
        if (!menu.hasBlankPatterns(writableCount)) {
            entries.addAll(entriesForInsufficientBlankPatterns(preparedEntries));
            return new BatchPatternEncodeResult(
                    0,
                    skippedExistingCount,
                    skippedInvalidCount,
                    countNotProcessed(entries),
                    PatternEncodeStopReason.NO_BLANK_PATTERN,
                    entries);
        }

        for (int i = 0; i < preparedEntries.size(); i++) {
            var preparedEntry = preparedEntries.get(i);
            if (preparedEntry.status != PatternEncodeEntryStatus.ENCODED) {
                entries.add(preparedEntry.toResult());
                continue;
            }

            var encodedPattern = preparedEntry.encodedPattern;

            var playerInv = new PlayerInternalInventory(player.getInventory());
            if (!canInsertIntoPlayerInventory(playerInv, encodedPattern)) {
                stopReason = PatternEncodeStopReason.NO_INVENTORY_SPACE;
                addNotProcessedEntries(entries, requests, i);
                break;
            }

            if (!insertIntoPlayerInventory(playerInv, encodedPattern)) {
                stopReason = PatternEncodeStopReason.NO_INVENTORY_SPACE;
                addNotProcessedEntries(entries, requests, i);
                break;
            }

            if (!menu.consumeBlankPatterns(1)) {
                stopReason = PatternEncodeStopReason.NO_BLANK_PATTERN;
                addNotProcessedEntries(entries, requests, i + 1);
                break;
            }

            encodedCount++;
            entries.add(entry(preparedEntry.request, PatternEncodeEntryStatus.ENCODED, null));
        }

        return new BatchPatternEncodeResult(
                encodedCount,
                skippedExistingCount,
                skippedInvalidCount,
                countNotProcessed(entries),
                stopReason,
                entries);
    }

    private static List<PreparedEntry> prepareEntries(ServerPlayer player, List<PatternEncodeRequest> requests) {
        var preparedEntries = new ArrayList<PreparedEntry>(requests.size());
        var plannedPrimaryOutputs = new HashSet<AEKey>();

        for (var request : requests) {
            var encodedPattern = createEncodedPattern(player.level(), request);
            if (encodedPattern == null) {
                preparedEntries.add(PreparedEntry.skipped(request, PatternEncodeEntryStatus.SKIPPED_INVALID_RECIPE,
                        "Unable to encode recipe"));
                continue;
            }

            var details = PatternDetailsHelper.decodePattern(encodedPattern, player.level());
            if (details == null || details.getOutputs().isEmpty()) {
                preparedEntries.add(PreparedEntry.skipped(request, PatternEncodeEntryStatus.SKIPPED_INVALID_PATTERN,
                        "Encoded pattern is invalid"));
                continue;
            }

            var primaryOutput = details.getPrimaryOutput();
            if (hasPatternWithPrimaryOutput(player, primaryOutput)
                    || !plannedPrimaryOutputs.add(primaryOutput.what())) {
                preparedEntries.add(PreparedEntry.skipped(request,
                        PatternEncodeEntryStatus.SKIPPED_EXISTING_PRIMARY_OUTPUT,
                        "Pattern with same primary output already exists"));
                continue;
            }

            preparedEntries.add(PreparedEntry.writable(request, encodedPattern));
        }

        return preparedEntries;
    }

    private static int countWritable(List<PreparedEntry> preparedEntries) {
        int count = 0;
        for (var preparedEntry : preparedEntries) {
            if (preparedEntry.status == PatternEncodeEntryStatus.ENCODED) {
                count++;
            }
        }
        return count;
    }

    private static List<PatternEncodeEntryResult> entriesForInsufficientBlankPatterns(
            List<PreparedEntry> preparedEntries) {
        var entries = new ArrayList<PatternEncodeEntryResult>(preparedEntries.size());
        for (var preparedEntry : preparedEntries) {
            if (preparedEntry.status == PatternEncodeEntryStatus.ENCODED) {
                entries.add(entry(preparedEntry.request, PatternEncodeEntryStatus.NOT_PROCESSED, null));
            } else {
                entries.add(preparedEntry.toResult());
            }
        }
        return entries;
    }

    @Nullable
    static ItemStack createEncodedPattern(Level level, PatternEncodeRequest request) {
        try {
            return switch (request.mode()) {
                case PROCESSING -> createProcessingPattern(request);
                case CRAFTING -> createCraftingPattern(level, request);
                case STONECUTTING, SMITHING_TABLE -> null;
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    static ItemStack createProcessingPattern(PatternEncodeRequest request) {
        var inputs = request.sparseInputs();
        var outputs = request.sparseOutputs();
        if (inputs.size() > AEProcessingPattern.MAX_INPUT_SLOTS
                || outputs.size() > AEProcessingPattern.MAX_OUTPUT_SLOTS) {
            return null;
        }
        if (outputs.isEmpty() || !isValidStack(outputs.get(0))) {
            return null;
        }

        var hasInput = false;
        for (var input : inputs) {
            if (input != null) {
                if (!isValidStack(input)) {
                    return null;
                }
                hasInput = true;
            }
        }
        if (!hasInput) {
            return null;
        }

        for (var output : outputs) {
            if (output != null && !isValidStack(output)) {
                return null;
            }
        }

        try {
            return GTCEuPatternMetadataBridge.encodeProcessingPatternWithVirtualCircuitMetadata(
                    inputs, outputs, java.util.OptionalInt.empty());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    static ItemStack createCraftingPattern(Level level, PatternEncodeRequest request) {
        if (request.vanillaRecipeId() == null || request.sparseInputs().size() > 9) {
            return null;
        }

        var recipeHolder = level.getRecipeManager().byKey(request.vanillaRecipeId()).orElse(null);
        if (recipeHolder == null || recipeHolder.value().getType() != RecipeType.CRAFTING
                || !(recipeHolder.value() instanceof CraftingRecipe craftingRecipe)) {
            return null;
        }

        var ingredients = new ArrayList<ItemStack>(9);
        var hasInput = false;
        for (int i = 0; i < 9; i++) {
            var input = i < request.sparseInputs().size() ? request.sparseInputs().get(i) : null;
            if (input == null) {
                ingredients.add(ItemStack.EMPTY);
                continue;
            }
            if (!isValidStack(input) || !(input.what() instanceof AEItemKey itemKey)) {
                return null;
            }
            ingredients.add(itemKey.toStack());
            hasInput = true;
        }
        if (!hasInput) {
            return null;
        }

        var craftingInput = CraftingInput.of(3, 3, ingredients);
        if (!craftingRecipe.matches(craftingInput, level)) {
            return null;
        }

        var output = craftingRecipe.assemble(craftingInput, level.registryAccess());
        if (output.isEmpty()) {
            return null;
        }

        var typedRecipeHolder = new RecipeHolder<CraftingRecipe>(recipeHolder.id(), craftingRecipe);
        return PatternDetailsHelper.encodeCraftingPattern(
                typedRecipeHolder,
                ingredients.toArray(ItemStack[]::new),
                output,
                request.allowSubstitution(),
                request.allowFluidSubstitution());
    }

    static boolean hasPatternWithPrimaryOutput(ServerPlayer player, GenericStack primaryOutput) {
        for (var stack : player.getInventory().items) {
            var details = PatternDetailsHelper.decodePattern(stack, player.level());
            if (details == null || details.getOutputs().isEmpty()) {
                continue;
            }

            if (details.getPrimaryOutput().what().equals(primaryOutput.what())) {
                return true;
            }
        }
        return false;
    }

    static boolean canInsertIntoPlayerInventory(PlayerInternalInventory inventory, ItemStack encodedPattern) {
        return inventory.simulateAdd(encodedPattern).isEmpty();
    }

    static boolean insertIntoPlayerInventory(PlayerInternalInventory inventory, ItemStack encodedPattern) {
        return inventory.addItems(encodedPattern).isEmpty();
    }

    private static boolean isValidStack(@Nullable GenericStack stack) {
        return stack != null && stack.amount() > 0;
    }

    private static PatternEncodeEntryResult entry(PatternEncodeRequest request, PatternEncodeEntryStatus status,
            @Nullable String message) {
        return new PatternEncodeEntryResult(
                request.recipeUid(),
                status,
                message != null ? Component.literal(message) : null);
    }

    private static void addNotProcessedEntries(List<PatternEncodeEntryResult> entries,
            List<PatternEncodeRequest> requests, int firstNotProcessed) {
        for (int index = firstNotProcessed; index < requests.size(); index++) {
            entries.add(entry(requests.get(index), PatternEncodeEntryStatus.NOT_PROCESSED, null));
        }
    }

    private static int countNotProcessed(List<PatternEncodeEntryResult> entries) {
        int count = 0;
        for (var entry : entries) {
            if (entry.status() == PatternEncodeEntryStatus.NOT_PROCESSED) {
                count++;
            }
        }
        return count;
    }

    private static BatchPatternEncodeResult stoppedBeforeProcessing(List<PatternEncodeRequest> requests,
            PatternEncodeStopReason stopReason) {
        var entries = new ArrayList<PatternEncodeEntryResult>(requests.size());
        addNotProcessedEntries(entries, requests, 0);
        return new BatchPatternEncodeResult(0, 0, 0, requests.size(), stopReason, entries);
    }

    private record PreparedEntry(
            PatternEncodeRequest request,
            PatternEncodeEntryStatus status,
            @Nullable ItemStack encodedPattern,
            @Nullable String message) {

        static PreparedEntry writable(PatternEncodeRequest request, ItemStack encodedPattern) {
            return new PreparedEntry(request, PatternEncodeEntryStatus.ENCODED, encodedPattern, null);
        }

        static PreparedEntry skipped(PatternEncodeRequest request, PatternEncodeEntryStatus status,
                @Nullable String message) {
            return new PreparedEntry(request, status, null, message);
        }

        PatternEncodeEntryResult toResult() {
            return entry(request, status, message);
        }
    }
}
