package appeng.helpers.patternprovider.upload;

public record PatternUploadSourceRef(PatternUploadSourceKind kind, int slot) {
    public static PatternUploadSourceRef playerInventory(int slot) {
        return new PatternUploadSourceRef(PatternUploadSourceKind.PLAYER_INVENTORY, slot);
    }

    public static PatternUploadSourceRef encodedPatternSlot() {
        return new PatternUploadSourceRef(PatternUploadSourceKind.ENCODED_PATTERN_SLOT, 0);
    }
}
