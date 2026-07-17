package appeng.client.gui.me.items;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import appeng.core.AppEng;

class PatternUploadButtonRendererTest {
    @Test
    void selectsTheExactSizeTextureForEachButtonState() {
        assertThat(
                PatternUploadButtonRenderer.getTexture(PatternUploadButtonRenderer.Size.MANAGEMENT_MAIN, true, false))
                .isEqualTo(AppEng.makeId("textures/gui/sprites/upload_pattern_152x20.png"));
        assertThat(PatternUploadButtonRenderer.getTexture(PatternUploadButtonRenderer.Size.SELECT_MAIN, true, true))
                .isEqualTo(AppEng.makeId("textures/gui/sprites/upload_pattern_186x20_highlighted.png"));
        assertThat(
                PatternUploadButtonRenderer.getTexture(PatternUploadButtonRenderer.Size.MANAGEMENT_MAIN, false, false))
                .isEqualTo(AppEng.makeId("textures/gui/sprites/upload_pattern_152x20_disabled.png"));
    }
}
