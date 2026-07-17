package appeng.helpers.patternprovider.upload;

public record PatternUploadRecallResult(PatternUploadRecallStatus status) {
    static PatternUploadRecallResult of(PatternUploadRecallStatus status) {
        return new PatternUploadRecallResult(status);
    }
}
