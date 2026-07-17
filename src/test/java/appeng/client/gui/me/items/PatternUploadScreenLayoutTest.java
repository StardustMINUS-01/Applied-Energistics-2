package appeng.client.gui.me.items;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class PatternUploadScreenLayoutTest {
    @Test
    void usesThePatternUploadLayoutInsteadOfTheCraftingReportLayout() throws Exception {
        var root = readScreen("pattern_upload_select.json");
        var widgets = root.getAsJsonObject("widgets");

        var search = widgets.getAsJsonObject("search");
        assertThat(search.get("left").getAsInt()).isEqualTo(50);
        assertThat(search.get("top").getAsInt()).isEqualTo(4);
        assertThat(widgets.has("back")).isTrue();
        assertThat(widgets.has("cancel")).isFalse();

        var scrollbar = widgets.getAsJsonObject("scrollbar");
        assertThat(scrollbar.get("top").getAsInt()).isEqualTo(19);
        assertThat(scrollbar.get("height").getAsInt()).isEqualTo(180);
        assertThat(root.has("background")).isFalse();
        assertThat(root.getAsJsonObject("generatedBackground").get("height").getAsInt()).isEqualTo(206);
    }

    @Test
    void usesAConnectedSteppedBackgroundForTheManagementLayout() throws Exception {
        var root = readScreen("pattern_upload_management.json");

        assertThat(root.getAsJsonObject("widgets").getAsJsonObject("search").get("left").getAsInt()).isEqualTo(50);
        assertThat(root.has("generatedBackground")).isFalse();
        assertThat(root.has("background")).isTrue();
        var background = root.getAsJsonObject("background");
        assertThat(background.get("texture").getAsString()).isEqualTo("guis/pattern_upload_management.png");
        assertThat(background.get("textureWidth").getAsInt()).isEqualTo(238);
        assertThat(background.get("textureHeight").getAsInt()).isEqualTo(248);
        assertThat(background.getAsJsonArray("srcRect").get(2).getAsInt()).isEqualTo(238);
        assertThat(background.getAsJsonArray("srcRect").get(3).getAsInt()).isEqualTo(248);
    }

    @Test
    void arrangesTheManagementSourcesAsThreeRowsOfNineAndOneRowOfTen() throws Exception {
        var slots = readScreen("pattern_upload_management.json").getAsJsonObject("slots");

        var inventory = slots.getAsJsonObject("PLAYER_INVENTORY");
        var hotbar = slots.getAsJsonObject("PLAYER_HOTBAR");
        var encodedPattern = slots.getAsJsonObject("ENCODED_PATTERN");
        assertThat(inventory.get("left").getAsInt()).isEqualTo(10);
        assertThat(inventory.get("top").getAsInt()).isEqualTo(168);
        assertThat(hotbar.get("left").getAsInt()).isEqualTo(10);
        assertThat(hotbar.get("top").getAsInt()).isEqualTo(222);
        assertThat(encodedPattern.get("left").getAsInt()).isEqualTo(172);
        assertThat(encodedPattern.get("top").getAsInt()).isEqualTo(222);
    }

    private JsonObject readScreen(String fileName) throws Exception {
        var resourcePath = "assets/ae2/screens/" + fileName;
        var resource = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertThat(resource).as(resourcePath).isNotNull();

        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
