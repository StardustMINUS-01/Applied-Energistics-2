package appeng.crafting.ledger;

import java.util.ArrayDeque;
import java.util.Objects;

import appeng.api.stacks.AEKey;

public final class CraftingRequest {
    private final AEKey what;
    private final long amount;
    private long remainingAmount;
    private final ArrayDeque<ContributionEntry> contributions = new ArrayDeque<>();

    public CraftingRequest(AEKey what, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }

        this.what = Objects.requireNonNull(what);
        this.amount = amount;
        this.remainingAmount = amount;
    }

    public AEKey what() {
        return what;
    }

    public long amount() {
        return amount;
    }

    public long remainingAmount() {
        return remainingAmount;
    }

    public long fulfilledAmount() {
        return amount - remainingAmount;
    }

    public void fulfill(long amount, CraftingContribution contribution) {
        if (amount <= 0) {
            throw new IllegalArgumentException("fulfilled amount must be positive");
        }
        if (amount > remainingAmount) {
            throw new IllegalArgumentException("fulfilled amount exceeds remaining request");
        }

        remainingAmount -= amount;
        contributions.addLast(new ContributionEntry(amount, Objects.requireNonNull(contribution)));
    }

    public void refund(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("refund amount must be non-negative");
        }
        if (amount > fulfilledAmount()) {
            throw new IllegalArgumentException("refund amount exceeds fulfilled request");
        }

        long left = amount;
        while (left > 0) {
            var entry = contributions.removeLast();
            var refunded = Math.min(left, entry.amount);
            entry.contribution.refund(refunded);
            remainingAmount += refunded;
            left -= refunded;

            var kept = entry.amount - refunded;
            if (kept > 0) {
                contributions.addLast(new ContributionEntry(kept, entry.contribution));
            }
        }
    }

    public void fullRefund() {
        refund(fulfilledAmount());
    }

    private record ContributionEntry(long amount, CraftingContribution contribution) {
    }
}
