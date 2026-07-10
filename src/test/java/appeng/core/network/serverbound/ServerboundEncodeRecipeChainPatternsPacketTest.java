package appeng.core.network.serverbound;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.stacks.GenericStack;
import appeng.integration.jei.patternencoding.PatternEncodeMode;
import appeng.integration.jei.patternencoding.PatternEncodeRequest;
import appeng.util.CodecTestUtil;

class ServerboundEncodeRecipeChainPatternsPacketTest {

    @Test
    void roundTripsMultipleRequests() {
        var first = new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("jei", "processing"),
                ResourceLocation.fromNamespaceAndPath("test", "first"),
                PatternEncodeMode.PROCESSING,
                List.of(GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT))),
                List.of(GenericStack.fromItemStack(new ItemStack(Items.IRON_BLOCK))),
                null,
                false,
                false);
        var second = new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "oak_planks"),
                PatternEncodeMode.CRAFTING,
                List.of(GenericStack.fromItemStack(new ItemStack(Items.OAK_LOG))),
                List.of(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "oak_planks"),
                true,
                false);

        CodecTestUtil.testRoundtrip(ServerboundEncodeRecipeChainPatternsPacket.STREAM_CODEC,
                new ServerboundEncodeRecipeChainPatternsPacket(List.of(first, second)));
    }
}
