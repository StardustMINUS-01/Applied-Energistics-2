package appeng.helpers.patternprovider.upload;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

record PatternUploadRecallRecord(String providerGroupName, ItemStack pattern) {
    PatternUploadRecallRecord {
        Objects.requireNonNull(providerGroupName);
        Objects.requireNonNull(pattern);
        pattern = pattern.copyWithCount(1);
    }

    @Override
    public ItemStack pattern() {
        return pattern.copy();
    }
}
