package appeng.crafting.ledger;

import appeng.api.crafting.IPatternDetails;

public record PatternCraftingTask(IPatternDetails pattern, long times) implements CraftingTask {
}
