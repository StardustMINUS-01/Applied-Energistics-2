package appeng.helpers.patternprovider.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.blockentities.PatternContainerGroup;

class PatternUploadPacketCodecTest {
    @Test
    void targetGroupPacketRoundTripsWithoutRecentPattern() {
        var original = new PatternUploadTargetGroup(
                "assembler",
                new PatternContainerGroup(null, Component.literal("assembler"), List.of()),
                0,
                9,
                false,
                true,
                ItemStack.EMPTY);
        var buffer = createBuffer();

        original.writeToPacket(buffer);
        var decoded = new PatternUploadTargetGroup(buffer);

        assertThat(decoded.name()).isEqualTo("assembler");
        assertThat(decoded.recentUploadedPattern()).matches(ItemStack::isEmpty);
    }

    @Test
    void managementDataPacketRoundTripsWithoutVirtualSourceSlots() {
        var original = new PatternUploadManagementData(PatternUploadTargetGroups.EMPTY);
        var buffer = createBuffer();

        original.writeToPacket(buffer);
        var decoded = new PatternUploadManagementData(buffer);

        assertThat(decoded.targetGroups()).isEqualTo(PatternUploadTargetGroups.EMPTY);
    }

    private static RegistryFriendlyByteBuf createBuffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
