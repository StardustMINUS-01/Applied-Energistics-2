package appeng.integration.jei.patternencoding;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.PatternCatalyst;
import appeng.util.CodecTestUtil;

class PatternEncodeRequestTest {

    @Test
    void roundTripsProcessingRequest() {
        var request = new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("jei", "processing"),
                ResourceLocation.fromNamespaceAndPath("test", "dust_to_plate"),
                PatternEncodeMode.PROCESSING,
                List.of(GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT, 64))),
                List.of(GenericStack.fromItemStack(new ItemStack(Items.IRON_BLOCK))),
                List.of(new PatternCatalyst(0, GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT, 64)))),
                List.of(),
                null,
                false,
                false);

        CodecTestUtil.testRoundtrip(PatternEncodeRequest.STREAM_CODEC, request);
    }

    @Test
    void roundTripsCraftingRequest() {
        var request = new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "oak_planks"),
                PatternEncodeMode.CRAFTING,
                List.of(GenericStack.fromItemStack(new ItemStack(Items.OAK_LOG))),
                List.of(),
                List.of(),
                List.of(GenericStack.fromItemStack(new ItemStack(Items.OAK_LOG))),
                ResourceLocation.fromNamespaceAndPath("minecraft", "oak_planks"),
                true,
                true);

        CodecTestUtil.testRoundtrip(PatternEncodeRequest.STREAM_CODEC, request);
    }
}
