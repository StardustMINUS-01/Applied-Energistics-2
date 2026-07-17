package appeng.helpers.patternprovider.upload;

import org.jetbrains.annotations.Nullable;

public record PatternUploadTargetHint(@Nullable Integer selectedTargetId, @Nullable String selectedGroupName) {
    private static final PatternUploadTargetHint AUTO = new PatternUploadTargetHint(null, null);

    public static PatternUploadTargetHint auto() {
        return AUTO;
    }

    public static PatternUploadTargetHint selected(int targetId) {
        return new PatternUploadTargetHint(targetId, null);
    }

    public static PatternUploadTargetHint selectedGroup(String groupName) {
        return new PatternUploadTargetHint(null, groupName);
    }
}
