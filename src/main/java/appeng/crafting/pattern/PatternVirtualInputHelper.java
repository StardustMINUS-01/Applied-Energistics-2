package appeng.crafting.pattern;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.GenericStack;
import appeng.core.localization.GuiText;
import appeng.integration.modules.gtceu.GTCEuPatternMetadataBridge;

public final class PatternVirtualInputHelper {
    private PatternVirtualInputHelper() {
    }

    public static ItemStack encodeProcessingPattern(List<GenericStack> sparseInputs, List<GenericStack> sparseOutputs,
            List<PatternCatalyst> catalysts) {
        var realInputs = new ArrayList<>(sparseInputs);
        var normalCatalysts = new ArrayList<PatternCatalyst>();
        for (var catalyst : catalysts) {
            if (catalyst.sourceSlot() < 0 || catalyst.sourceSlot() >= realInputs.size()
                    || !catalyst.stack().equals(realInputs.get(catalyst.sourceSlot()))) {
                throw new IllegalArgumentException("Catalyst must match a processing input slot.");
            }
            if (GTCEuPatternMetadataBridge.getCircuitConfiguration(catalyst.stack()).isPresent()) {
                continue;
            }
            realInputs.set(catalyst.sourceSlot(), null);
            normalCatalysts.add(catalyst);
        }

        var encoded = GTCEuPatternMetadataBridge.encodeProcessingPatternWithVirtualCircuitMetadata(
                realInputs,
                sparseOutputs,
                java.util.OptionalInt.empty());
        if (!normalCatalysts.isEmpty()) {
            encoded.set(AEComponents.PATTERN_VIRTUAL_INPUTS, new PatternVirtualInputs(normalCatalysts));
        }
        return encoded;
    }

    public static PatternVirtualInputs getCatalysts(ItemStack encodedPattern) {
        return encodedPattern.getOrDefault(AEComponents.PATTERN_VIRTUAL_INPUTS, PatternVirtualInputs.EMPTY);
    }

    public static Component makeCatalystTooltipLine(GenericStack stack) {
        return Component.empty()
                .append(GuiText.PatternTooltipCatalyst.text())
                .append(Component.literal(": "))
                .append(Component.literal(EncodedPatternItem.getTooltipEntryLine(stack).getString()))
                .withStyle(ChatFormatting.GREEN);
    }
}
