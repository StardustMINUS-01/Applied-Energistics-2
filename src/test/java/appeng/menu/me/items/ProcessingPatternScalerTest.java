package appeng.menu.me.items;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.util.BootstrapMinecraft;
import appeng.util.ConfigInventory;

@BootstrapMinecraft
class ProcessingPatternScalerTest {
    private static final AEItemKey STICK = AEItemKey.of(Items.STICK);
    private static final AEItemKey STONE = AEItemKey.of(Items.STONE);
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);

    @Test
    void multipliesInputsAndOutputsTogether() {
        var inputs = inventory(4);
        var outputs = inventory(2);
        inputs.setStack(0, new GenericStack(STICK, 2));
        inputs.setStack(2, new GenericStack(STONE, 3));
        outputs.setStack(0, new GenericStack(IRON, 1));

        assertThat(ProcessingPatternScaler.scale(inputs, outputs, 2)).isTrue();

        assertThat(inputs.getStack(0)).isEqualTo(new GenericStack(STICK, 4));
        assertThat(inputs.getStack(1)).isNull();
        assertThat(inputs.getStack(2)).isEqualTo(new GenericStack(STONE, 6));
        assertThat(outputs.getStack(0)).isEqualTo(new GenericStack(IRON, 2));
    }

    @Test
    void dividesInputsAndOutputsTogether() {
        var inputs = inventory(2);
        var outputs = inventory(1);
        inputs.setStack(0, new GenericStack(STICK, 10));
        inputs.setStack(1, new GenericStack(STONE, 5));
        outputs.setStack(0, new GenericStack(IRON, 15));

        assertThat(ProcessingPatternScaler.scale(inputs, outputs, -5)).isTrue();

        assertThat(inputs.getStack(0)).isEqualTo(new GenericStack(STICK, 2));
        assertThat(inputs.getStack(1)).isEqualTo(new GenericStack(STONE, 1));
        assertThat(outputs.getStack(0)).isEqualTo(new GenericStack(IRON, 3));
    }

    @Test
    void rejectsNonDivisibleDivisionWithoutChangingAnySlot() {
        var inputs = inventory(2);
        var outputs = inventory(1);
        inputs.setStack(0, new GenericStack(STICK, 10));
        inputs.setStack(1, new GenericStack(STONE, 4));
        outputs.setStack(0, new GenericStack(IRON, 3));

        assertThat(ProcessingPatternScaler.scale(inputs, outputs, -2)).isFalse();

        assertThat(inputs.getStack(0)).isEqualTo(new GenericStack(STICK, 10));
        assertThat(inputs.getStack(1)).isEqualTo(new GenericStack(STONE, 4));
        assertThat(outputs.getStack(0)).isEqualTo(new GenericStack(IRON, 3));
    }

    @Test
    void rejectsMultiplicationOverflowWithoutChangingAnySlot() {
        var inputs = inventory(1);
        var outputs = inventory(1);
        inputs.setStack(0, new GenericStack(STICK, Integer.MAX_VALUE));
        outputs.setStack(0, new GenericStack(IRON, 1));

        assertThat(ProcessingPatternScaler.scale(inputs, outputs, 2)).isFalse();

        assertThat(inputs.getStack(0)).isEqualTo(new GenericStack(STICK, Integer.MAX_VALUE));
        assertThat(outputs.getStack(0)).isEqualTo(new GenericStack(IRON, 1));
    }

    @Test
    void rejectsUnsupportedFactorWithoutChangingAnySlot() {
        var inputs = inventory(1);
        var outputs = inventory(1);
        inputs.setStack(0, new GenericStack(STICK, 2));
        outputs.setStack(0, new GenericStack(IRON, 1));

        assertThat(ProcessingPatternScaler.scale(inputs, outputs, 4)).isFalse();

        assertThat(inputs.getStack(0)).isEqualTo(new GenericStack(STICK, 2));
        assertThat(outputs.getStack(0)).isEqualTo(new GenericStack(IRON, 1));
    }

    private static ConfigInventory inventory(int size) {
        return ConfigInventory.configStacks(size).allowOverstacking(true).build();
    }
}
