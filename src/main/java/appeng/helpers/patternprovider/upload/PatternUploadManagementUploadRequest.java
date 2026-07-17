package appeng.helpers.patternprovider.upload;

import java.util.List;

public record PatternUploadManagementUploadRequest(List<PatternUploadSourceRef> sources, String targetGroupName) {
    public PatternUploadManagementUploadRequest {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
