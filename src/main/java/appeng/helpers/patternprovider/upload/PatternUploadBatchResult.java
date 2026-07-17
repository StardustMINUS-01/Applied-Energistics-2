package appeng.helpers.patternprovider.upload;

import java.util.List;

public record PatternUploadBatchResult(
        List<PatternUploadResult> uploadedPatterns,
        int targetFullCount,
        int invalidSourceCount,
        int failedCount) {

    public PatternUploadBatchResult {
        uploadedPatterns = List.copyOf(uploadedPatterns);
    }

    public int uploadedCount() {
        return uploadedPatterns.size();
    }
}
