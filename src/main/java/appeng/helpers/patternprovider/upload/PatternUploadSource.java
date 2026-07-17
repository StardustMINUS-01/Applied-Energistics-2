package appeng.helpers.patternprovider.upload;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;

public interface PatternUploadSource {
    ItemStack getStack();

    boolean removeUploadedStack(ItemStack expectedStack);

    static PatternUploadSource singleSlot(InternalInventory inventory, int slot) {
        Objects.checkIndex(slot, inventory.size());
        return new SingleSlotPatternUploadSource(inventory, slot);
    }

    final class SingleSlotPatternUploadSource implements PatternUploadSource {
        private final InternalInventory inventory;
        private final int slot;

        private SingleSlotPatternUploadSource(InternalInventory inventory, int slot) {
            this.inventory = Objects.requireNonNull(inventory);
            this.slot = slot;
        }

        @Override
        public ItemStack getStack() {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public boolean removeUploadedStack(ItemStack expectedStack) {
            var current = inventory.getStackInSlot(slot);
            if (current.getCount() != 1 || !ItemStack.isSameItemSameComponents(current, expectedStack)) {
                return false;
            }

            inventory.setItemDirect(slot, ItemStack.EMPTY);
            return true;
        }
    }
}
