package appeng.crafting.ledger;

import java.util.Objects;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

public final class CraftingPlanningContext {
    private final KeyCounter availableItems = new KeyCounter();
    private final KeyCounter workingItems = new KeyCounter();
    private final KeyCounter usedItems = new KeyCounter();
    private final KeyCounter emittedItems = new KeyCounter();

    public CraftingPlanningContext(KeyCounter initialInventory) {
        Objects.requireNonNull(initialInventory);
        for (var entry : initialInventory) {
            var amount = entry.getLongValue();
            if (amount > 0) {
                availableItems.add(entry.getKey(), amount);
            }
        }
    }

    public long availableAmount(AEKey what) {
        return availableItems.get(what) + workingItems.get(what);
    }

    public KeyCounter usedItems() {
        return usedItems;
    }

    public KeyCounter emittedItems() {
        return emittedItems;
    }

    public long extractFromNetwork(CraftingRequest request) {
        var what = request.what();
        var extractedFromWorkingItems = Math.min(request.remainingAmount(), workingItems.get(what));
        if (extractedFromWorkingItems > 0) {
            workingItems.remove(what, extractedFromWorkingItems);
            request.fulfill(extractedFromWorkingItems,
                    new WorkingInventoryContribution(what, extractedFromWorkingItems));
        }

        var extracted = Math.min(request.remainingAmount(), availableItems.get(what));
        if (extracted <= 0) {
            return extractedFromWorkingItems;
        }

        availableItems.remove(what, extracted);
        usedItems.add(what, extracted);
        request.fulfill(extracted, new NetworkExtractionContribution(what, extracted));
        return extractedFromWorkingItems + extracted;
    }

    public CraftingRequest extractValidInput(IPatternDetails.IInput input, long amountPerUnit, long maxUnits,
            Level level) {
        for (var entry : workingItems) {
            var key = entry.getKey();
            var units = Math.min(maxUnits, entry.getLongValue() / amountPerUnit);
            if (units > 0 && input.isValid(key, level)) {
                var request = new CraftingRequest(key, units * amountPerUnit);
                extractFromNetwork(request);
                return request;
            }
        }

        for (var entry : availableItems) {
            var key = entry.getKey();
            var units = Math.min(maxUnits, entry.getLongValue() / amountPerUnit);
            if (units > 0 && input.isValid(key, level)) {
                var request = new CraftingRequest(key, units * amountPerUnit);
                extractFromNetwork(request);
                return request;
            }
        }

        return null;
    }

    public long emit(CraftingRequest request) {
        var amount = request.remainingAmount();
        if (amount <= 0) {
            return 0;
        }

        emittedItems.add(request.what(), amount);
        request.fulfill(amount, new EmittedContribution(request.what(), amount));
        return amount;
    }

    public void insertIntoWorkingInventory(AEKey what, long amount) {
        if (amount > 0) {
            workingItems.add(what, amount);
        }
    }

    private final class WorkingInventoryContribution implements CraftingContribution {
        private final AEKey what;
        private long refundableAmount;

        private WorkingInventoryContribution(AEKey what, long amount) {
            this.what = what;
            this.refundableAmount = amount;
        }

        @Override
        public void refund(long amount) {
            if (amount > refundableAmount) {
                throw new IllegalArgumentException("refund amount exceeds working inventory contribution");
            }

            refundableAmount -= amount;
            workingItems.add(what, amount);
        }
    }

    private final class NetworkExtractionContribution implements CraftingContribution {
        private final AEKey what;
        private long refundableAmount;

        private NetworkExtractionContribution(AEKey what, long amount) {
            this.what = what;
            this.refundableAmount = amount;
        }

        @Override
        public void refund(long amount) {
            if (amount > refundableAmount) {
                throw new IllegalArgumentException("refund amount exceeds extraction contribution");
            }

            refundableAmount -= amount;
            usedItems.remove(what, amount);
            availableItems.add(what, amount);
        }
    }

    private final class EmittedContribution implements CraftingContribution {
        private final AEKey what;
        private long refundableAmount;

        private EmittedContribution(AEKey what, long amount) {
            this.what = what;
            this.refundableAmount = amount;
        }

        @Override
        public void refund(long amount) {
            if (amount > refundableAmount) {
                throw new IllegalArgumentException("refund amount exceeds emitted contribution");
            }

            refundableAmount -= amount;
            emittedItems.remove(what, amount);
        }
    }
}
