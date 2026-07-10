package appeng.integration.jei.patternencoding;

import java.util.List;

public record BatchPatternEncodeResult(
        int encodedCount,
        int skippedExistingCount,
        int skippedInvalidCount,
        int remainingUnprocessedCount,
        PatternEncodeStopReason stopReason,
        List<PatternEncodeEntryResult> entries) {

    public BatchPatternEncodeResult {
        entries = List.copyOf(entries);
    }
}
