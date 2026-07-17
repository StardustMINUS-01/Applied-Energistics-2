package appeng.integration.modules.jei;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.client.gui.SearchTextDropTarget;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class JeiSearchTextGhostIngredientHandlerTest {
    @Test
    void createsSearchFieldTargetForItemStacks() {
        var handler = new JeiSearchTextGhostIngredientHandler<TestSearchScreen>();
        var screen = new TestSearchScreen();
        var typedIngredient = new TestTypedIngredient<>(new ItemStack(Items.DIAMOND));

        var targets = handler.getTargetsTyped(screen, typedIngredient, true);

        assertThat(targets).hasSize(1);
        assertThat(targets.getFirst().getArea()).isEqualTo(screen.area);
    }

    @Test
    void writesItemDisplayNameWhenDropped() {
        var handler = new JeiSearchTextGhostIngredientHandler<TestSearchScreen>();
        var screen = new TestSearchScreen();
        var stack = new ItemStack(Items.DIAMOND);
        var typedIngredient = new TestTypedIngredient<>(stack);

        handler.getTargetsTyped(screen, typedIngredient, true)
                .getFirst()
                .accept(stack);

        assertThat(screen.searchText).isEqualTo(stack.getHoverName().getString());
    }

    @Test
    void skipsScreensWithoutVisibleSearchArea() {
        var handler = new JeiSearchTextGhostIngredientHandler<TestSearchScreen>();
        var screen = new TestSearchScreen();
        screen.area = null;

        assertThat(handler.getTargetsTyped(screen, new TestTypedIngredient<>(new ItemStack(Items.DIAMOND)), true))
                .isEmpty();
    }

    private static final class TestSearchScreen extends Screen implements SearchTextDropTarget {
        private Rect2i area = new Rect2i(10, 20, 30, 40);
        private String searchText = "";

        private TestSearchScreen() {
            super(Component.empty());
        }

        @Override
        public Optional<Rect2i> getSearchTextDropArea() {
            return Optional.ofNullable(area);
        }

        @Override
        public void setSearchTextFromDrop(String searchText) {
            this.searchText = searchText;
        }
    }
}
