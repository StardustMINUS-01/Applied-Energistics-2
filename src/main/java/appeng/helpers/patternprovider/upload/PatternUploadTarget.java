package appeng.helpers.patternprovider.upload;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.helpers.patternprovider.PatternContainer;

public record PatternUploadTarget(
        int id,
        PatternContainerGroup group,
        long sortOrder,
        int emptySlots,
        int totalSlots) {

    public static PatternUploadTarget from(PatternContainer container) {
        var inventory = container.getTerminalPatternInventory();
        var emptySlots = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                emptySlots++;
            }
        }

        return new PatternUploadTarget(
                System.identityHashCode(container),
                container.getTerminalGroup(),
                container.getTerminalSortOrder(),
                emptySlots,
                inventory.size());
    }

    public String name() {
        return group.name().getString();
    }
}
