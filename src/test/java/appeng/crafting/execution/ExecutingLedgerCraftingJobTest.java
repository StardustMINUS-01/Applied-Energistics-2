package appeng.crafting.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.ledger.CraftingTask;
import appeng.crafting.ledger.LedgerCraftingPlan;
import appeng.crafting.ledger.PatternCraftingTask;
import appeng.crafting.simulation.helpers.ProcessingPatternBuilder;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class ExecutingLedgerCraftingJobTest {
    @Test
    void preservesLedgerTaskOrderWithoutAggregatingPatternTimes() {
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var intermediate = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var firstPattern = new ProcessingPatternBuilder(intermediate)
                .addPreciseInput(1, input)
                .build();
        var secondPattern = new ProcessingPatternBuilder(output)
                .addPreciseInput(1, intermediate)
                .build();
        List<CraftingTask> tasks = List.of(
                new PatternCraftingTask(firstPattern, 2),
                new PatternCraftingTask(secondPattern, 1),
                new PatternCraftingTask(firstPattern, 3));
        var plan = new LedgerCraftingPlan(output, 64, false, false, new KeyCounter(), new KeyCounter(),
                new KeyCounter(), Map.of(firstPattern, 5L, secondPattern, 1L), tasks);
        var link = new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false),
                mock(ICraftingCPU.class));

        var job = new ExecutingLedgerCraftingJob(plan, ignored -> {
        }, link, null);

        assertThat(job.finalOutput).isEqualTo(output);
        assertThat(job.remainingAmount).isEqualTo(output.amount());
        assertThat(job.tasks).hasSize(3);
        assertThat(job.tasks.get(0).pattern()).isSameAs(firstPattern);
        assertThat(job.tasks.get(0).remainingTimes()).isEqualTo(2);
        assertThat(job.tasks.get(1).pattern()).isSameAs(secondPattern);
        assertThat(job.tasks.get(1).remainingTimes()).isEqualTo(1);
        assertThat(job.tasks.get(2).pattern()).isSameAs(firstPattern);
        assertThat(job.tasks.get(2).remainingTimes()).isEqualTo(3);
    }
}
