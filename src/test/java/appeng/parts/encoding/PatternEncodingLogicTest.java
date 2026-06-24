package appeng.parts.encoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class PatternEncodingLogicTest {
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
