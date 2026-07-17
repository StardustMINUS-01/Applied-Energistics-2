package appeng.client.gui.me.items;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.style.Blitter;
import appeng.core.AppEng;

/** Draws the upload button from a pre-cropped texture at its exact GUI size. */
final class PatternUploadButtonRenderer {
    private PatternUploadButtonRenderer() {
    }

    static ResourceLocation getTexture(Size size, boolean active, boolean hovered) {
        var suffix = active ? hovered ? "_highlighted" : "" : "_disabled";
        return AppEng.makeId("textures/gui/sprites/upload_pattern_" + size.textureName + suffix + ".png");
    }

    static void render(GuiGraphics guiGraphics, Size size, boolean active, boolean hovered, int x, int y) {
        var texture = getTexture(size, active, hovered);
        Blitter.texture(texture, size.width, size.height)
                .src(0, 0, size.width, size.height)
                .dest(x, y, size.width, size.height)
                .blit(guiGraphics);
    }

    enum Size {
        SELECT_MAIN("186x20", 186, 20),
        MANAGEMENT_MAIN("152x20", 152, 20);

        private final String textureName;
        private final int width;
        private final int height;

        Size(String textureName, int width, int height) {
            this.textureName = textureName;
            this.width = width;
            this.height = height;
        }
    }

}
