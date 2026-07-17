package appeng.crafting.execution;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class ForceStartTrackerTest {
    private final RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    @Test
    void serializesWaitingItemsToNbt() {
        var stone = AEItemKey.of(Items.STONE);
        var diamond = AEItemKey.of(Items.DIAMOND);
        var missing = new KeyCounter();
        missing.add(stone, 12);
        missing.add(diamond, 3);
        var tracker = new ForceStartTracker();
        tracker.set(missing);

        var restored = new ForceStartTracker();
        restored.readFromNBT(tracker.writeToNBT(registries), registries);

        assertThat(restored.get(stone)).isEqualTo(12);
        assertThat(restored.get(diamond)).isEqualTo(3);
    }

    @Test
    void simulateInsertDoesNotMutateWaitingItems() {
        var stone = AEItemKey.of(Items.STONE);
        var missing = new KeyCounter();
        missing.add(stone, 12);
        var tracker = new ForceStartTracker();
        tracker.set(missing);

        assertThat(tracker.insert(stone, 5, Actionable.SIMULATE)).isEqualTo(5);

        assertThat(tracker.get(stone)).isEqualTo(12);
    }

    @Test
    void tracksAndSerializesFluidKeys() {
        var water = AEFluidKey.of(Fluids.WATER);
        var missing = new KeyCounter();
        missing.add(water, AEFluidKey.AMOUNT_BUCKET);
        var tracker = new ForceStartTracker();
        tracker.set(missing);

        assertThat(tracker.insert(water, AEFluidKey.AMOUNT_BUCKET / 4, Actionable.MODULATE))
                .isEqualTo(AEFluidKey.AMOUNT_BUCKET / 4);

        var restored = new ForceStartTracker();
        restored.readFromNBT(tracker.writeToNBT(registries), registries);

        assertThat(restored.get(water)).isEqualTo(AEFluidKey.AMOUNT_BUCKET * 3 / 4);
    }
}
