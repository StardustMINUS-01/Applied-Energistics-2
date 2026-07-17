package appeng.crafting.ledger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.world.level.Level;

import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

public final class LedgerCraftingPlanner {
    private final KeyCounter initialInventory;
    private final Map<AEKey, List<IPatternDetails>> patterns;
    private final Set<AEKey> emitableItems;
    private final Level level;

    public LedgerCraftingPlanner(KeyCounter initialInventory) {
        this(initialInventory, Map.of());
    }

    public LedgerCraftingPlanner(KeyCounter initialInventory, Map<AEKey, List<IPatternDetails>> patterns) {
        this(initialInventory, patterns, Set.of());
    }

    public LedgerCraftingPlanner(KeyCounter initialInventory, Map<AEKey, List<IPatternDetails>> patterns,
            Set<AEKey> emitableItems) {
        this(initialInventory, patterns, emitableItems, null);
    }

    public LedgerCraftingPlanner(KeyCounter initialInventory, Map<AEKey, List<IPatternDetails>> patterns,
            Set<AEKey> emitableItems, Level level) {
        this.initialInventory = Objects.requireNonNull(initialInventory);
        this.patterns = Map.copyOf(patterns);
        this.emitableItems = Set.copyOf(emitableItems);
        this.level = level;
    }

    public ICraftingPlan plan(GenericStack what, CalculationStrategy strategy) {
        var context = new CraftingPlanningContext(initialInventory);
        var request = new CraftingRequest(what.what(), what.amount());
        var missingItems = new KeyCounter();
        var patternTimes = new HashMap<IPatternDetails, Long>();
        var attemptedPatternTimes = new HashMap<IPatternDetails, Long>();
        var tasks = new ArrayList<CraftingTask>();
        var flags = new PlanningFlags();
        var stats = new PlanningStats(what.amount());

        stats.addRequest(what.what(), what.amount());
        resolve(context, request, patternTimes, attemptedPatternTimes, tasks, missingItems, flags, stats,
                new HashSet<>());
        var finalAmount = strategy == CalculationStrategy.CRAFT_LESS && request.fulfilledAmount() > 0
                ? request.fulfilledAmount()
                : what.amount();
        var effectiveMissingItems = strategy == CalculationStrategy.CRAFT_LESS && request.fulfilledAmount() > 0
                ? new KeyCounter()
                : missingItems;
        var effectivePatternTimes = !effectiveMissingItems.isEmpty() ? attemptedPatternTimes : patternTimes;

        return new LedgerCraftingPlan(
                new GenericStack(what.what(), finalAmount),
                stats.bytes(effectivePatternTimes),
                !effectiveMissingItems.isEmpty(),
                flags.multiplePaths,
                context.usedItems(),
                context.emittedItems(),
                effectiveMissingItems,
                effectivePatternTimes,
                tasks,
                stats.toPublicStats(tasks.size()));
    }

    private void resolve(CraftingPlanningContext context, CraftingRequest request,
            Map<IPatternDetails, Long> patternTimes, Map<IPatternDetails, Long> attemptedPatternTimes,
            List<CraftingTask> tasks, KeyCounter missingItems, PlanningFlags flags, PlanningStats stats,
            Set<AEKey> parentRequests) {
        context.extractFromNetwork(request);
        if (request.remainingAmount() == 0) {
            return;
        }

        if (emitableItems.contains(request.what())) {
            context.emit(request);
            return;
        }

        if (!parentRequests.add(request.what())) {
            missingItems.add(request.what(), request.remainingAmount());
            return;
        }

        var availablePatterns = patterns.getOrDefault(request.what(), List.of());
        if (availablePatterns.size() > 1) {
            flags.multiplePaths = true;
        }
        var candidateMissingItems = new KeyCounter();
        IPatternDetails firstUnresolvedPattern = null;
        long firstUnresolvedPatternTimes = 0;
        for (var pattern : availablePatterns) {
            if (request.remainingAmount() == 0) {
                break;
            }

            var outputCount = getOutputCount(pattern, request.what());
            if (outputCount <= 0) {
                continue;
            }

            var requestedTimes = divideRoundingUp(request.remainingAmount(), outputCount);
            if (patternHasRemainingInputs(pattern)) {
                var missingBeforePattern = candidateMissingItems.size();
                for (long i = 0; i < requestedTimes && request.remainingAmount() > 0; i++) {
                    var attemptMissingItems = new KeyCounter();
                    var attempt = planPatternAttempt(context, pattern, 1, patternTimes, attemptedPatternTimes, tasks,
                            attemptMissingItems, flags, stats, parentRequests);
                    if (attempt.maxTimes <= 0) {
                        if (firstUnresolvedPattern == null) {
                            firstUnresolvedPattern = pattern;
                            firstUnresolvedPatternTimes = divideRoundingUp(request.remainingAmount(), outputCount);
                        }
                        merge(candidateMissingItems, attemptMissingItems);
                        break;
                    }
                    applyPatternAttempt(context, request, pattern, outputCount, attempt.maxTimes, attempt.childRequests,
                            patternTimes, tasks, stats);
                }
                if (request.remainingAmount() > 0 && candidateMissingItems.size() == missingBeforePattern) {
                    // Keep trying sibling patterns if this remaining-item pattern could not make more progress but did
                    // not produce a specific missing ingredient.
                    continue;
                }
            } else {
                var patternMissingItems = new KeyCounter();
                var attempt = planPatternAttempt(context, pattern, requestedTimes, patternTimes, attemptedPatternTimes,
                        tasks, patternMissingItems, flags, stats, parentRequests);
                if (attempt.maxTimes <= 0) {
                    if (firstUnresolvedPattern == null) {
                        firstUnresolvedPattern = pattern;
                        firstUnresolvedPatternTimes = divideRoundingUp(request.remainingAmount(), outputCount);
                    }
                    merge(candidateMissingItems, patternMissingItems);
                    continue;
                }
                applyPatternAttempt(context, request, pattern, outputCount, attempt.maxTimes, attempt.childRequests,
                        patternTimes, tasks, stats);

                if (request.remainingAmount() > 0) {
                    merge(candidateMissingItems, patternMissingItems);
                }
            }
        }

        parentRequests.remove(request.what());

        if (request.remainingAmount() > 0) {
            if (availablePatterns.isEmpty()) {
                missingItems.add(request.what(), request.remainingAmount());
            } else {
                merge(missingItems, candidateMissingItems);
                if (firstUnresolvedPattern != null) {
                    tasks.add(new PatternCraftingTask(firstUnresolvedPattern, firstUnresolvedPatternTimes));
                }
            }
        }
    }

    private ChildRequest resolveInput(CraftingPlanningContext context, IPatternDetails.IInput input,
            long requestedTimes, Map<IPatternDetails, Long> patternTimes,
            Map<IPatternDetails, Long> attemptedPatternTimes, List<CraftingTask> tasks, KeyCounter missingItems,
            PlanningFlags flags, PlanningStats stats, Set<AEKey> parentRequests) {
        var possibleInputs = input.getPossibleInputs();
        if (possibleInputs.length == 0) {
            return new ChildRequest(input, input.getMultiplier());
        }

        var childRequest = new ChildRequest(input, input.getMultiplier());
        var remainingUnits = input.getMultiplier() * requestedTimes;
        stats.addRequest(possibleInputs[0].what(), possibleInputs[0].amount() * remainingUnits);

        for (var template : possibleInputs) {
            if (remainingUnits == 0) {
                break;
            }

            var availableUnits = context.availableAmount(template.what()) / template.amount();
            var extractedUnits = Math.min(remainingUnits, availableUnits);
            if (extractedUnits <= 0) {
                continue;
            }

            var request = new CraftingRequest(template.what(), template.amount() * extractedUnits);
            context.extractFromNetwork(request);
            if (request.fulfilledAmount() > 0) {
                childRequest.add(request, template.amount());
                remainingUnits -= request.fulfilledAmount() / template.amount();
            }
        }

        if (remainingUnits > 0 && level != null) {
            var request = context.extractValidInput(input, possibleInputs[0].amount(), remainingUnits, level);
            if (request != null && request.fulfilledAmount() > 0) {
                childRequest.add(request, possibleInputs[0].amount());
                remainingUnits -= request.fulfilledAmount() / possibleInputs[0].amount();
            }
        }

        if (remainingUnits > 0) {
            var primaryInput = possibleInputs[0];
            var inputKey = findCraftableInputKey(input, possibleInputs);
            var request = new CraftingRequest(inputKey, primaryInput.amount() * remainingUnits);
            resolve(context, request, patternTimes, attemptedPatternTimes, tasks, missingItems, flags, stats,
                    parentRequests);
            childRequest.add(request, primaryInput.amount());
        }

        return childRequest;
    }

    private PatternAttempt planPatternAttempt(CraftingPlanningContext context, IPatternDetails pattern,
            long requestedTimes, Map<IPatternDetails, Long> patternTimes,
            Map<IPatternDetails, Long> attemptedPatternTimes, List<CraftingTask> tasks, KeyCounter missingItems,
            PlanningFlags flags, PlanningStats stats, Set<AEKey> parentRequests) {
        stats.addPatternAttempt();
        attemptedPatternTimes.merge(pattern, requestedTimes, Long::sum);
        var childRequests = new ArrayList<ChildRequest>();
        for (var input : pattern.getInputs()) {
            childRequests.add(resolveInput(context, input, requestedTimes, patternTimes, attemptedPatternTimes, tasks,
                    missingItems, flags, stats, parentRequests));
        }

        var maxTimes = requestedTimes;
        for (var child : childRequests) {
            maxTimes = Math.min(maxTimes, child.fulfilledUnits() / child.unitsPerCraft);
        }

        return new PatternAttempt(maxTimes, childRequests);
    }

    private void applyPatternAttempt(CraftingPlanningContext context, CraftingRequest request, IPatternDetails pattern,
            long outputCount, long maxTimes, List<ChildRequest> childRequests, Map<IPatternDetails, Long> patternTimes,
            List<CraftingTask> tasks, PlanningStats stats) {
        for (var child : childRequests) {
            child.refundUnitsAfter(child.unitsPerCraft * maxTimes);
            child.insertRemainingItems(context, stats);
        }

        var produced = Math.min(request.remainingAmount(), outputCount * maxTimes);
        patternTimes.merge(pattern, maxTimes, Long::sum);
        tasks.add(new PatternCraftingTask(pattern, maxTimes));
        insertRemainingOutputs(context, request.what(), produced, pattern, maxTimes);
        request.fulfill(produced, new PatternContribution(pattern, outputCount, maxTimes, childRequests,
                patternTimes));
    }

    private static boolean patternHasRemainingInputs(IPatternDetails pattern) {
        for (var input : pattern.getInputs()) {
            for (var possibleInput : input.getPossibleInputs()) {
                if (input.getRemainingKey(possibleInput.what()) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private AEKey findCraftableInputKey(IPatternDetails.IInput input, GenericStack[] possibleInputs) {
        var acceptableAmount = possibleInputs[0].amount();
        for (var possibleInput : possibleInputs) {
            if (possibleInput.amount() == acceptableAmount && patterns.containsKey(possibleInput.what())) {
                return possibleInput.what();
            }
        }

        for (var possibleInput : possibleInputs) {
            if (possibleInput.amount() != acceptableAmount) {
                continue;
            }

            for (var craftable : patterns.keySet()) {
                if (possibleInput.what().fuzzyEquals(craftable, FuzzyMode.IGNORE_ALL)
                        && (level == null || input.isValid(craftable, level))) {
                    return craftable;
                }
            }
        }

        return possibleInputs[0].what();
    }

    private static void insertRemainingOutputs(CraftingPlanningContext context, AEKey requestedOutput,
            long consumedRequestedOutput, IPatternDetails pattern, long times) {
        var remainingConsumedOutput = consumedRequestedOutput;
        for (var output : pattern.getOutputs()) {
            var producedAmount = output.amount() * times;
            if (requestedOutput.matches(output)) {
                var consumedAmount = Math.min(producedAmount, remainingConsumedOutput);
                remainingConsumedOutput -= consumedAmount;
                var excessAmount = producedAmount - consumedAmount;
                if (excessAmount > 0) {
                    context.insertIntoWorkingInventory(output.what(), excessAmount);
                }
            } else {
                context.insertIntoWorkingInventory(output.what(), producedAmount);
            }
        }
    }

    private static long getOutputCount(IPatternDetails pattern, AEKey what) {
        long result = 0;
        for (var output : pattern.getOutputs()) {
            if (what.matches(output)) {
                result += output.amount();
            }
        }
        return result;
    }

    private static long divideRoundingUp(long dividend, long divisor) {
        return (dividend + divisor - 1) / divisor;
    }

    private static void merge(KeyCounter target, KeyCounter source) {
        for (var entry : source) {
            target.add(entry.getKey(), entry.getLongValue());
        }
    }

    private static final class ChildRequest {
        private final IPatternDetails.IInput input;
        private final long unitsPerCraft;
        private final List<ResolvedInput> inputs = new ArrayList<>();

        private ChildRequest(IPatternDetails.IInput input, long unitsPerCraft) {
            this.input = input;
            this.unitsPerCraft = unitsPerCraft;
        }

        private void add(CraftingRequest request, long amountPerUnit) {
            inputs.add(new ResolvedInput(request, amountPerUnit));
        }

        private long fulfilledUnits() {
            long result = 0;
            for (var input : inputs) {
                result += input.request.fulfilledAmount() / input.amountPerUnit;
            }
            return result;
        }

        private void refundUnitsAfter(long unitsToKeep) {
            for (var i = inputs.size() - 1; i >= 0; i--) {
                var input = inputs.get(i);
                var fulfilledUnits = input.request.fulfilledAmount() / input.amountPerUnit;
                if (fulfilledUnits <= unitsToKeep) {
                    unitsToKeep -= fulfilledUnits;
                    continue;
                }

                var refundUnits = fulfilledUnits - unitsToKeep;
                input.request.refund(refundUnits * input.amountPerUnit);
                unitsToKeep = 0;
            }
        }

        private void insertRemainingItems(CraftingPlanningContext context, PlanningStats stats) {
            for (var resolvedInput : inputs) {
                var remainingKey = input.getRemainingKey(resolvedInput.request.what());
                if (remainingKey != null) {
                    var amount = resolvedInput.request.fulfilledAmount() / resolvedInput.amountPerUnit;
                    context.insertIntoWorkingInventory(remainingKey,
                            amount);
                    stats.addStackBytes(remainingKey, amount);
                }
            }
        }
    }

    private record ResolvedInput(CraftingRequest request, long amountPerUnit) {
    }

    private record PatternAttempt(long maxTimes, List<ChildRequest> childRequests) {
    }

    private static final class PlanningFlags {
        private boolean multiplePaths;
    }

    private static final class PlanningStats {
        private final long requestedAmount;
        private double requestBytes;
        private final Set<AEKey> requestNodes = new HashSet<>();
        private long requestResolutions;
        private long patternAttempts;

        private PlanningStats(long requestedAmount) {
            this.requestedAmount = requestedAmount;
        }

        private void addRequest(AEKey what, long amount) {
            requestResolutions++;
            if (requestNodes.add(what)) {
                requestBytes += 8;
            }
            addStackBytes(what, amount);
        }

        private void addPatternAttempt() {
            patternAttempts++;
        }

        private void addStackBytes(AEKey what, long amount) {
            requestBytes += (double) amount / what.getAmountPerByte() * 8;
        }

        private long bytes(Map<IPatternDetails, Long> patternTimes) {
            double result = requestBytes;
            for (var times : patternTimes.values()) {
                result += times;
            }
            return (long) Math.ceil(result);
        }

        private CraftingPlanningStats toPublicStats(long plannedTasks) {
            return new CraftingPlanningStats(
                    requestedAmount,
                    requestResolutions,
                    requestNodes.size(),
                    patternAttempts,
                    plannedTasks);
        }
    }

    private static final class PatternContribution implements CraftingContribution {
        private final IPatternDetails pattern;
        private final long outputCount;
        private final List<ChildRequest> childRequests;
        private final Map<IPatternDetails, Long> patternTimes;
        private long refundableTimes;

        private PatternContribution(IPatternDetails pattern, long outputCount, long times,
                List<ChildRequest> childRequests, Map<IPatternDetails, Long> patternTimes) {
            this.pattern = pattern;
            this.outputCount = outputCount;
            this.refundableTimes = times;
            this.childRequests = List.copyOf(childRequests);
            this.patternTimes = patternTimes;
        }

        @Override
        public void refund(long amount) {
            var refundTimes = Math.min(refundableTimes, amount / outputCount);
            if (refundTimes <= 0) {
                return;
            }

            refundableTimes -= refundTimes;
            var remainingTimes = refundableTimes;
            for (var child : childRequests) {
                child.refundUnitsAfter(child.unitsPerCraft * remainingTimes);
            }

            patternTimes.computeIfPresent(pattern, (ignored, currentTimes) -> {
                var updatedTimes = currentTimes - refundTimes;
                return updatedTimes > 0 ? updatedTimes : null;
            });
        }
    }
}
