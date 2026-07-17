package appeng.parts.encoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;

import org.junit.jupiter.api.Test;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.integration.modules.gtceu.GTCEuPatternMetadataBridge;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class PatternEncodingLogicTest {
    private static final RegistryAccess REGISTRIES = RegistryAccess
            .fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final GenericStack MANY_STICKS = new GenericStack(AEItemKey.of(Items.STICK), 42);
    private static final GenericStack MANY_STONES = new GenericStack(AEItemKey.of(Items.STONE), 99);
    private static final GenericStack IRON_OUTPUT = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 7);
    private static final GenericStack WATER = new GenericStack(AEFluidKey.of(Fluids.WATER), AEFluidKey.AMOUNT_BUCKET);

    @Test
    void switchingToCraftingUsesSeparateInputDraft() {
        var logic = newLogic();

        logic.setMode(EncodingMode.PROCESSING);
        logic.getEncodedInputInv().setStack(0, MANY_STICKS);
        logic.getEncodedInputInv().setStack(9, WATER);

        logic.setMode(EncodingMode.CRAFTING);

        assertThat(logic.getEncodedInputInv().getStack(0)).isNull();
        assertThat(logic.getEncodedInputInv().getStack(9)).isNull();
    }

    @Test
    void processingDraftSurvivesCraftingEdits() {
        var logic = newLogic();

        logic.setMode(EncodingMode.PROCESSING);
        logic.getEncodedInputInv().setStack(0, MANY_STICKS);
        logic.getEncodedInputInv().setStack(9, WATER);
        logic.getEncodedOutputInv().setStack(0, IRON_OUTPUT);

        logic.setMode(EncodingMode.CRAFTING);
        logic.getEncodedInputInv().setStack(0, MANY_STONES);

        logic.setMode(EncodingMode.PROCESSING);

        assertThat(logic.getEncodedInputInv().getStack(0)).isEqualTo(MANY_STICKS);
        assertThat(logic.getEncodedInputInv().getStack(9)).isEqualTo(WATER);
        assertThat(logic.getEncodedOutputInv().getStack(0)).isEqualTo(IRON_OUTPUT);
    }

    @Test
    void slotEditsUpdateCurrentModeDraft() {
        var logic = newLogic();

        logic.setMode(EncodingMode.PROCESSING);
        logic.getEncodedInputInv().setStack(0, MANY_STICKS);
        logic.setMode(EncodingMode.CRAFTING);
        logic.setMode(EncodingMode.PROCESSING);

        logic.getEncodedInputInv().setStack(0, WATER);
        logic.setMode(EncodingMode.CRAFTING);
        logic.setMode(EncodingMode.PROCESSING);

        assertThat(logic.getEncodedInputInv().getStack(0)).isEqualTo(WATER);
    }

    @Test
    void clientModeSyncDoesNotRestoreLocalDraftsOverServerSlotSync() {
        var logic = newLogic(true);

        logic.getEncodedInputInv().setStack(0, MANY_STICKS);
        logic.setMode(EncodingMode.PROCESSING);

        assertThat(logic.getEncodedInputInv().getStack(0)).isEqualTo(MANY_STICKS);
    }

    @Test
    void readFromNbtToleratesMissingLevelDuringBlockEntityLoad() {
        var saved = new CompoundTag();
        var logic = newLogic();
        logic.setMode(EncodingMode.PROCESSING);
        logic.getEncodedInputInv().setStack(0, MANY_STICKS);
        logic.writeToNBT(saved, REGISTRIES);

        var restored = new TestHost(null).logic;
        restored.readFromNBT(saved, REGISTRIES);

        assertThat(restored.getEncodedInputInv().getStack(0)).isEqualTo(MANY_STICKS);
    }

    @Test
    void countsOnlyBlankPatternsInBlankPatternInventory() {
        var logic = newLogic();
        logic.getBlankPatternInv().setItemDirect(0, AEItems.BLANK_PATTERN.stack(7));

        assertThat(logic.getAvailableBlankPatternCount()).isEqualTo(7);
        assertThat(logic.hasBlankPatterns(7)).isTrue();
        assertThat(logic.hasBlankPatterns(8)).isFalse();
    }

    @Test
    void nonBlankItemsDoNotCountAsBlankPatterns() {
        var logic = newLogic();
        logic.getBlankPatternInv().setItemDirect(0, new ItemStack(Items.STICK));

        assertThat(logic.getAvailableBlankPatternCount()).isZero();
        assertThat(logic.hasBlankPatterns(1)).isFalse();
    }

    @Test
    void consumesBlankPatternsFromBlankPatternInventory() {
        var logic = newLogic();
        logic.getBlankPatternInv().setItemDirect(0, AEItems.BLANK_PATTERN.stack(3));

        assertThat(logic.consumeBlankPatterns(2)).isTrue();
        assertThat(logic.getAvailableBlankPatternCount()).isEqualTo(1);

        assertThat(logic.consumeBlankPatterns(2)).isFalse();
        assertThat(logic.getAvailableBlankPatternCount()).isEqualTo(1);
    }

    @Test
    void loadingProcessingPatternRestoresVirtualCircuitMetadataAsInputSlot() {
        var logic = newLogic();
        var realInput = new GenericStack(AEItemKey.of(Items.DIAMOND), 4);
        var oversizedCircuitStack = IntCircuitBehaviour.stack(7);
        oversizedCircuitStack.setCount(99);
        var oversizedCircuitInput = GenericStack.fromItemStack(oversizedCircuitStack);
        var output = new GenericStack(AEItemKey.of(Items.STICK), 1);

        var encoded = GTCEuPatternMetadataBridge.encodeProcessingPatternWithVirtualCircuitMetadata(
                java.util.List.of(realInput, oversizedCircuitInput),
                java.util.List.of(output),
                java.util.OptionalInt.empty());

        logic.getEncodedPatternInv().setItemDirect(0, encoded);

        assertThat(PatternDetailsHelper.isEncodedPattern(encoded)).isTrue();
        assertThat(logic.getMode()).isEqualTo(EncodingMode.PROCESSING);
        assertThat(logic.getEncodedInputInv().getStack(0)).isEqualTo(realInput);
        assertThat(logic.getEncodedInputInv().getStack(1))
                .isEqualTo(GenericStack.fromItemStack(IntCircuitBehaviour.stack(7)));
        assertThat(logic.isCatalyst(1)).isTrue();
        assertThat(logic.getEncodedInputInv().getStack(2)).isNull();
        assertThat(logic.getEncodedOutputInv().getStack(0)).isEqualTo(output);
    }

    private static PatternEncodingLogic newLogic() {
        return newLogic(false);
    }

    private static PatternEncodingLogic newLogic(boolean clientSide) {
        var level = mock(Level.class);
        when(level.isClientSide()).thenReturn(clientSide);
        return new TestHost(level).logic;
    }

    private static class TestHost implements IPatternTerminalLogicHost {
        private final Level level;
        private final PatternEncodingLogic logic = new PatternEncodingLogic(this);

        TestHost(Level level) {
            this.level = level;
        }

        @Override
        public PatternEncodingLogic getLogic() {
            return logic;
        }

        @Override
        public Level getLevel() {
            return level;
        }

        @Override
        public void markForSave() {
        }
    }
}
