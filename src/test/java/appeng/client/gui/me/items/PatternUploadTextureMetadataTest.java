package appeng.client.gui.me.items;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonParser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PatternUploadTextureMetadataTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "upload_pattern.png.mcmeta",
            "upload_pattern_disabled.png.mcmeta",
            "upload_pattern_highlighted.png.mcmeta"
    })
    void uploadButtonTexturesUseTheLogicalSizeOfTheTripleResolutionAssets(String fileName) throws Exception {
        var resourcePath = "assets/ae2/textures/gui/sprites/" + fileName;
        var resource = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertThat(resource).as(resourcePath).isNotNull();

        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            var scaling = JsonParser.parseReader(reader)
                    .getAsJsonObject()
                    .getAsJsonObject("gui")
                    .getAsJsonObject("scaling");

            assertThat(scaling.get("type").getAsString()).isEqualTo("nine_slice");
            assertThat(scaling.get("width").getAsInt()).isEqualTo(200);
            assertThat(scaling.get("height").getAsInt()).isEqualTo(20);
            assertThat(scaling.get("border").getAsInt()).isEqualTo(3);
        }
    }
}
