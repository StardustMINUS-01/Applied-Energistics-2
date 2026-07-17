package appeng.menu.me.crafting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;

class CraftingStatusEntryTest {
    @Test
    void streamCodecRoundTripsForceStartAmount() {
        var original = new CraftingStatusEntry(
                42,
                AEItemKey.of(Items.DIAMOND),
                3,
                11,
                7,
                5);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        CraftingStatusEntry.STREAM_CODEC.encode(buffer, original);
        var decoded = CraftingStatusEntry.STREAM_CODEC.decode(buffer);

        assertThat(decoded.getSerial()).isEqualTo(original.getSerial());
        assertThat(decoded.getWhat()).isEqualTo(original.getWhat());
        assertThat(decoded.getStoredAmount()).isEqualTo(original.getStoredAmount());
        assertThat(decoded.getActiveAmount()).isEqualTo(original.getActiveAmount());
        assertThat(decoded.getPendingAmount()).isEqualTo(original.getPendingAmount());
        assertThat(decoded.getForceStartAmount()).isEqualTo(original.getForceStartAmount());
    }
}
