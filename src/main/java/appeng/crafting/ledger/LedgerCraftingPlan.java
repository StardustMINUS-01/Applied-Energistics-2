package appeng.crafting.ledger;

import java.util.List;
import java.util.Map;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

public record LedgerCraftingPlan(
        GenericStack finalOutput,
        long bytes,
        boolean simulation,
        boolean multiplePaths,
        KeyCounter usedItems,
        KeyCounter emittedItems,
        KeyCounter missingItems,
        Map<IPatternDetails, Long> patternTimes,
        List<CraftingTask> tasks,
        CraftingPlanningStats planningStats) implements ICraftingPlan {
    public LedgerCraftingPlan {
        tasks = List.copyOf(tasks);
    }

    public LedgerCraftingPlan(GenericStack finalOutput, long bytes, boolean simulation, boolean multiplePaths,
            KeyCounter usedItems, KeyCounter emittedItems, KeyCounter missingItems,
            Map<IPatternDetails, Long> patternTimes, List<CraftingTask> tasks) {
        this(finalOutput, bytes, simulation, multiplePaths, usedItems, emittedItems, missingItems, patternTimes, tasks,
                CraftingPlanningStats.EMPTY);
    }
}
