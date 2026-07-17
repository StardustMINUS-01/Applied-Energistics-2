package appeng.client.gui.me.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.client.gui.widgets.AETextField;

class MEStorageScreenTest {
    @Test
    void positionsDroppedSearchTextCursorAtTheStartWithoutSelectingText() {
        var searchField = mock(AETextField.class);

        MEStorageScreen.positionDroppedSearchCursor(searchField);

        verify(searchField).setCursorPosition(0);
        verify(searchField).setHighlightPos(0);
    }

    @Test
    void leftClickUsesTheCarriedItemNameWithoutChangingTheCarriedStack() {
        var carried = new ItemStack(Items.DIAMOND, 3);

        assertThat(MEStorageScreen.getCarriedItemSearchText(GLFW.GLFW_MOUSE_BUTTON_LEFT, carried))
                .contains(carried.getHoverName().getString());
        assertThat(MEStorageScreen.getCarriedItemSearchText(GLFW.GLFW_MOUSE_BUTTON_RIGHT, carried)).isEmpty();
        assertThat(carried.getCount()).isEqualTo(3);
    }
}
