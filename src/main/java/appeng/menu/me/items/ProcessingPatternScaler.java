package appeng.menu.me.items;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.GenericStack;
import appeng.util.ConfigInventory;

final class ProcessingPatternScaler {
    private ProcessingPatternScaler() {
    }

    static boolean scale(ConfigInventory inputs, ConfigInventory outputs, int factor) {
        if (!isSupportedFactor(factor)) {
            return false;
        }

        var scaledInputs = scaledStacks(inputs, factor);
        if (scaledInputs == null) {
            return false;
        }

        var scaledOutputs = scaledStacks(outputs, factor);
        if (scaledOutputs == null) {
            return false;
        }

        apply(inputs, scaledInputs);
        apply(outputs, scaledOutputs);
        return true;
    }

    private static boolean isSupportedFactor(int factor) {
        return factor == 2 || factor == 3 || factor == 5 || factor == -2 || factor == -3 || factor == -5;
    }

    private static @Nullable GenericStack[] scaledStacks(ConfigInventory inventory, int factor) {
        var scaled = new GenericStack[inventory.size()];
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = inventory.getStack(slot);
            if (stack == null) {
                continue;
            }

            var scaledAmount = scaledAmount(stack.amount(), factor);
            if (scaledAmount <= 0 || scaledAmount > Integer.MAX_VALUE) {
                return null;
            }

            scaled[slot] = new GenericStack(stack.what(), scaledAmount);
        }
        return scaled;
    }

    private static long scaledAmount(long amount, int factor) {
        if (factor > 0) {
            return amount * factor;
        }

        var divisor = -factor;
        if (amount % divisor != 0) {
            return 0;
        }
        return amount / divisor;
    }

    private static void apply(ConfigInventory inventory, GenericStack[] scaled) {
        for (int slot = 0; slot < scaled.length; slot++) {
            inventory.setStack(slot, scaled[slot]);
        }
    }
}
