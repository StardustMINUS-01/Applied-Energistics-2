package appeng.helpers.patternprovider.upload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;

public final class PatternUploadRecallService {
    private PatternUploadRecallService() {
    }

    public static PatternUploadRecallResult recallLastUploadedPattern(UUID playerId, IGrid grid,
            InternalInventory resultInventory, InternalInventory playerInventory,
            PatternUploadRecallHistory history, int historyLimit) {
        return recallLastUploadedPattern(playerId, PatternUploadService.findVisibleTargets(grid), resultInventory,
                playerInventory, history, historyLimit, null, PatternUploadRecallStatus.RECALLED_TO_RESULT_SLOT);
    }

    public static PatternUploadRecallResult recallLastUploadedPattern(UUID playerId, IGrid grid,
            InternalInventory resultInventory, InternalInventory playerInventory,
            PatternUploadRecallHistory history, int historyLimit, String providerGroupName) {
        return recallLastUploadedPattern(playerId, PatternUploadService.findVisibleTargets(grid), resultInventory,
                playerInventory, history, historyLimit, providerGroupName,
                PatternUploadRecallStatus.RECALLED_TO_RESULT_SLOT);
    }

    public static PatternUploadRecallResult recallLastUploadedPatternToCursor(UUID playerId, IGrid grid,
            InternalInventory cursorInventory, PatternUploadRecallHistory history, int historyLimit,
            String providerGroupName) {
        return recallLastUploadedPattern(playerId, PatternUploadService.findVisibleTargets(grid), cursorInventory,
                InternalInventory.empty(), history, historyLimit, providerGroupName,
                PatternUploadRecallStatus.RECALLED_TO_CURSOR);
    }

    static PatternUploadRecallResult recallLastUploadedPattern(UUID playerId, Iterable<PatternContainer> providers,
            InternalInventory resultInventory, InternalInventory playerInventory,
            PatternUploadRecallHistory history, int historyLimit) {
        return recallLastUploadedPattern(playerId, providers, resultInventory, playerInventory, history, historyLimit,
                null, PatternUploadRecallStatus.RECALLED_TO_RESULT_SLOT);
    }

    private static PatternUploadRecallResult recallLastUploadedPattern(UUID playerId,
            Iterable<PatternContainer> providers,
            InternalInventory resultInventory, InternalInventory playerInventory,
            PatternUploadRecallHistory history, int historyLimit, @Nullable String providerGroupName,
            PatternUploadRecallStatus primaryDestinationStatus) {
        var providerList = new ArrayList<PatternContainer>();
        providers.forEach(providerList::add);

        while (true) {
            var record = providerGroupName == null
                    ? history.getMostRecentRecord(playerId, historyLimit)
                    : history.getMostRecentRecord(playerId, providerGroupName, historyLimit);
            if (record == null) {
                return PatternUploadRecallResult.of(PatternUploadRecallStatus.NO_RECALLABLE_PATTERN);
            }

            var matchingPattern = findMatchingPattern(providerList, record);
            if (matchingPattern == null) {
                history.removeRecord(playerId, record);
                continue;
            }

            var destination = findDestination(resultInventory, playerInventory, record.pattern(),
                    primaryDestinationStatus);
            if (destination == null) {
                return PatternUploadRecallResult.of(PatternUploadRecallStatus.NO_DESTINATION_SPACE);
            }

            var extracted = matchingPattern.inventory().extractItem(matchingPattern.slot(), 1, false);
            if (!ItemStack.isSameItemSameComponents(extracted, record.pattern()) || extracted.getCount() != 1) {
                history.removeRecord(playerId, record);
                continue;
            }

            if (!destination.inventory().addItems(extracted).isEmpty()) {
                matchingPattern.inventory().setItemDirect(matchingPattern.slot(), matchingPattern.originalStack());
                return PatternUploadRecallResult.of(PatternUploadRecallStatus.INSERT_FAILED);
            }

            history.removeRecord(playerId, record);
            return PatternUploadRecallResult.of(destination.status());
        }
    }

    @Nullable
    private static MatchingPattern findMatchingPattern(List<PatternContainer> providers,
            PatternUploadRecallRecord record) {
        var expectedPattern = record.pattern();
        for (var provider : providers) {
            if (!provider.isVisibleInTerminal()
                    || !provider.getTerminalGroup().name().getString().equals(record.providerGroupName())) {
                continue;
            }

            var inventory = provider.getTerminalPatternInventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                var pattern = inventory.getStackInSlot(slot);
                if (ItemStack.isSameItemSameComponents(pattern, expectedPattern)) {
                    return new MatchingPattern(inventory, slot, pattern.copy());
                }
            }
        }
        return null;
    }

    @Nullable
    private static RecallDestination findDestination(InternalInventory resultInventory,
            InternalInventory playerInventory, ItemStack pattern, PatternUploadRecallStatus primaryDestinationStatus) {
        if (resultInventory.size() > 0 && resultInventory.getStackInSlot(0).isEmpty()
                && resultInventory.simulateAdd(pattern).isEmpty()) {
            return new RecallDestination(resultInventory, primaryDestinationStatus);
        }
        if (playerInventory.simulateAdd(pattern).isEmpty()) {
            return new RecallDestination(playerInventory, PatternUploadRecallStatus.RECALLED_TO_PLAYER_INVENTORY);
        }
        return null;
    }

    private record MatchingPattern(InternalInventory inventory, int slot, ItemStack originalStack) {
    }

    private record RecallDestination(InternalInventory inventory, PatternUploadRecallStatus status) {
    }
}
