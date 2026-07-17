package appeng.helpers.pattern;

import java.util.List;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.PatternVirtualInputHelper;
import appeng.integration.modules.gtceu.GTCEuPatternMetadataBridge;

/** Restores non-consumable inputs for display-only pattern previews. */
public final class PatternPreviewVirtualInputHelper {
    private PatternPreviewVirtualInputHelper() {
    }

    public static void restoreVirtualInputs(ItemStack encodedPattern, List<GenericStack[]> inputs) {
        var virtualCircuit = GTCEuPatternMetadataBridge.getVirtualCircuitDisplayInput(encodedPattern);
        if (virtualCircuit != null) {
            placeDisplayInput(inputs, virtualCircuit, -1);
        }

        for (var catalyst : PatternVirtualInputHelper.getCatalysts(encodedPattern).entries()) {
            placeDisplayInput(inputs, catalyst.stack(), catalyst.sourceSlot());
        }
    }

    /**
     * Checks the final preview position instead of the original pattern slot. A virtual circuit may occupy a catalyst's
     * former source slot while the preview is being reconstructed.
     */
    public static boolean isCatalystPreviewSlot(ItemStack encodedPattern, List<GenericStack[]> inputs, int slotIndex,
            GenericStack displayStack) {
        if (slotIndex < 0 || slotIndex >= inputs.size()) {
            return false;
        }

        for (var previewStack : inputs.get(slotIndex)) {
            if (!previewStack.what().equals(displayStack.what())) {
                continue;
            }
            if (PatternVirtualInputHelper.getCatalysts(encodedPattern).entries().stream()
                    .anyMatch(catalyst -> catalyst.stack().equals(previewStack))) {
                return true;
            }
            return GTCEuPatternMetadataBridge.getVirtualCircuitFromEncodedPattern(encodedPattern).isPresent()
                    && GTCEuPatternMetadataBridge.getCircuitConfiguration(previewStack).isPresent();
        }

        return false;
    }

    private static void placeDisplayInput(List<GenericStack[]> inputs, GenericStack displayInput, int preferredSlot) {
        while (inputs.size() < AEProcessingPattern.MAX_INPUT_SLOTS) {
            inputs.add(new GenericStack[0]);
        }

        if (preferredSlot >= 0 && preferredSlot < inputs.size() && inputs.get(preferredSlot).length == 0) {
            inputs.set(preferredSlot, new GenericStack[] { displayInput });
            return;
        }

        for (int slot = 0; slot < inputs.size(); slot++) {
            if (inputs.get(slot).length == 0) {
                inputs.set(slot, new GenericStack[] { displayInput });
                return;
            }
        }
    }
}
