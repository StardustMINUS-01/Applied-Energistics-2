package appeng.helpers.patternprovider.upload;

import java.util.List;

import net.minecraft.world.item.ItemStack;

public record PatternUploadResult(PatternUploadStatus status, List<PatternUploadTarget> candidates,
        String uploadedGroupName, ItemStack uploadedPattern) {
    public static PatternUploadResult of(PatternUploadStatus status) {
        return new PatternUploadResult(status, List.of(), "", ItemStack.EMPTY);
    }

    public static PatternUploadResult of(PatternUploadStatus status, List<PatternUploadTarget> candidates) {
        return new PatternUploadResult(status, List.copyOf(candidates), "", ItemStack.EMPTY);
    }

    public static PatternUploadResult uploaded(String groupName, ItemStack pattern) {
        return new PatternUploadResult(PatternUploadStatus.UPLOADED, List.of(), groupName, pattern.copyWithCount(1));
    }
}
