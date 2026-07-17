package appeng.client.gui.me.items;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PatternUploadManagementScreenTest {
    @Test
    void highlightsSelectedPatternsInMultiSelectModeWithPaleGreen() {
        assertThat(PatternUploadManagementScreen.getMultiSelectOverlay(true)).isEqualTo(0x5540ff80);
        assertThat(PatternUploadManagementScreen.getMultiSelectOverlay(false)).isEqualTo(0x55000000);
    }

    @Test
    void treatsTheManualUploadSlotAsAnUploadAction() {
        assertThat(PatternUploadManagementScreen.isUploadActionSlot(18)).isTrue();
        assertThat(PatternUploadManagementScreen.isUploadActionSlot(169)).isTrue();
        assertThat(PatternUploadManagementScreen.isUploadActionSlot(170)).isFalse();
        assertThat(PatternUploadManagementScreen.isUploadActionSlot(188)).isTrue();
        assertThat(PatternUploadManagementScreen.isUploadActionSlot(205)).isTrue();
        assertThat(PatternUploadManagementScreen.isUploadActionSlot(206)).isFalse();
    }
}
