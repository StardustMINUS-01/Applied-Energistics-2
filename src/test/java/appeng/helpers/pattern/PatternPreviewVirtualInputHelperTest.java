package appeng.helpers.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.PatternCatalyst;
import appeng.crafting.pattern.PatternVirtualInputHelper;
import appeng.integration.modules.gtceu.GTCEuPatternMetadataBridge;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class PatternPreviewVirtualInputHelperTest {
    @Test
    void restoresEveryCatalystIntoThePatternPreview() {
        var realInput = GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        var firstCatalyst = GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT, 64));
        var secondCatalyst = GenericStack.fromItemStack(new ItemStack(Items.DIAMOND, 3));
        var output = GenericStack.fromItemStack(new ItemStack(Items.IRON_BLOCK));
        var encoded = PatternVirtualInputHelper.encodeProcessingPattern(
                List.of(realInput, firstCatalyst, secondCatalyst),
                List.of(output),
                List.of(new PatternCatalyst(1, firstCatalyst), new PatternCatalyst(2, secondCatalyst)));
        var previewInputs = new ArrayList<GenericStack[]>(List.of(
                new GenericStack[] { realInput },
                new GenericStack[0],
                new GenericStack[0]));

        PatternPreviewVirtualInputHelper.restoreVirtualInputs(encoded, previewInputs);

        assertThat(previewInputs.get(1)).containsExactly(firstCatalyst);
        assertThat(previewInputs.get(2)).containsExactly(secondCatalyst);
    }

    @Test
    void marksCatalystAfterVirtualCircuitDisplacesItsOriginalSlot() {
        var realInput = GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        var catalyst = GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT, 64));
        var circuit = GenericStack.fromItemStack(IntCircuitBehaviour.stack(3));
        var output = GenericStack.fromItemStack(new ItemStack(Items.IRON_BLOCK));
        var encoded = PatternVirtualInputHelper.encodeProcessingPattern(
                List.of(realInput, catalyst, circuit),
                List.of(output),
                List.of(new PatternCatalyst(1, catalyst), new PatternCatalyst(2, circuit)));
        var previewInputs = new ArrayList<GenericStack[]>(List.<GenericStack[]>of(new GenericStack[] { realInput }));

        PatternPreviewVirtualInputHelper.restoreVirtualInputs(encoded, previewInputs);

        assertThat(previewInputs.get(1)[0])
                .isEqualTo(GTCEuPatternMetadataBridge.getVirtualCircuitDisplayInput(encoded));
        assertThat(previewInputs.get(2)).containsExactly(catalyst);
        assertThat(PatternPreviewVirtualInputHelper.isCatalystPreviewSlot(encoded, previewInputs, 2, catalyst))
                .isTrue();
    }
}
