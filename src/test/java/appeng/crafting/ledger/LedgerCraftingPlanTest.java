package appeng.crafting.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class LedgerCraftingPlanTest {
    @Test
    void exposesCurrentCraftingPlanFieldsAndTaskList() {
        var finalOutput = new GenericStack(AEItemKey.of(Items.DIAMOND), 4);
        var usedItems = new KeyCounter();
        usedItems.add(AEItemKey.of(Items.STONE), 2);
        var emittedItems = new KeyCounter();
        var missingItems = new KeyCounter();
        var task = new TestTask();

        ICraftingPlan plan = new LedgerCraftingPlan(
                finalOutput,
                42,
                false,
                true,
                usedItems,
                emittedItems,
                missingItems,
                Map.of(),
                List.of(task));

        assertThat(plan.finalOutput()).isEqualTo(finalOutput);
        assertThat(plan.bytes()).isEqualTo(42);
        assertThat(plan.simulation()).isFalse();
        assertThat(plan.multiplePaths()).isTrue();
        assertThat(plan.usedItems()).isSameAs(usedItems);
        assertThat(plan.emittedItems()).isSameAs(emittedItems);
        assertThat(plan.missingItems()).isSameAs(missingItems);
        assertThat(plan.patternTimes()).isEmpty();
        assertThat(((LedgerCraftingPlan) plan).tasks()).containsExactly(task);
    }

    private static final class TestTask implements CraftingTask {
    }
}
