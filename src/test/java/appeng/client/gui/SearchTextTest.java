package appeng.client.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class SearchTextTest {
    @Test
    void usesTheVisibleCustomNameForAnItemStack() {
        var stack = new ItemStack(Items.DIAMOND);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Calibrated Diamond"));

        assertThat(SearchText.fromItemStack(stack)).contains("Calibrated Diamond");
    }

    @Test
    void rejectsEmptyItemStacks() {
        assertThat(SearchText.fromItemStack(ItemStack.EMPTY)).isEmpty();
    }
}
