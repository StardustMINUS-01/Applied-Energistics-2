package appeng.integration.modules.jei;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class JeiSearchTextTest {
    @Test
    void usesItemDisplayNameAsSearchText() {
        var stack = new ItemStack(Items.DIAMOND);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Shiny Test Diamond"));

        assertThat(JeiSearchText.fromItemStack(stack)).hasValue("Shiny Test Diamond");
    }

    @Test
    void skipsEmptyStacks() {
        assertThat(JeiSearchText.fromItemStack(ItemStack.EMPTY)).isEmpty();
    }
}
