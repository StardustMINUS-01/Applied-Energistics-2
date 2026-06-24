package appeng.crafting.ledger;

public record CraftingPlanningStats(
        long requestedAmount,
        long requestResolutions,
        long uniqueRequestKeys,
        long patternAttempts,
        long plannedTasks) {
    public static final CraftingPlanningStats EMPTY = new CraftingPlanningStats(0, 0, 0, 0, 0);
}
