package appeng.crafting.execution;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.ledger.LedgerCraftingPlan;
import appeng.crafting.ledger.PatternCraftingTask;
import appeng.me.service.CraftingService;

class ExecutingLedgerCraftingJob {
    private static final String NBT_LINK = "link";
    private static final String NBT_PLAYER_ID = "playerId";
    private static final String NBT_FINAL_OUTPUT = "finalOutput";
    private static final String NBT_WAITING_FOR = "waitingFor";
    private static final String NBT_TIME_TRACKER = "timeTracker";
    private static final String NBT_REMAINING_AMOUNT = "remainingAmount";
    private static final String NBT_TASKS = "tasks";
    private static final String NBT_SUSPENDED = "suspended";
    private static final String NBT_REMAINING_TIMES = "remainingTimes";
    private static final String NBT_NEXT_TASK_INDEX = "nextTaskIndex";
    private static final String NBT_STATS = "stats";
    private static final String NBT_PROVIDER_LOOKUPS = "providerLookups";
    private static final String NBT_PROVIDER_BUSY_CHECKS = "providerBusyChecks";
    private static final String NBT_BUSY_PROVIDER_SKIPS = "busyProviderSkips";
    private static final String NBT_INPUT_EXTRACTIONS = "inputExtractions";
    private static final String NBT_INPUT_UNAVAILABLE_SKIPS = "inputUnavailableSkips";
    private static final String NBT_INPUT_BLOCKED_SKIPS = "inputBlockedSkips";
    private static final String NBT_INPUT_BLOCKED_SHORT_CIRCUITS = "inputBlockedShortCircuits";
    private static final String NBT_PATTERN_PUSHES = "patternPushes";
    private static final String NBT_COMPLETED_TASKS = "completedTasks";

    final CraftingLink link;
    final ListCraftingInventory waitingFor;
    final List<TaskProgress> tasks = new ArrayList<>();
    final ElapsedTimeTracker timeTracker;
    final GenericStack finalOutput;
    long remainingAmount;
    int nextTaskIndex;
    long providerLookups;
    long providerBusyChecks;
    long busyProviderSkips;
    long inputExtractions;
    long inputUnavailableSkips;
    long inputBlockedSkips;
    long inputBlockedShortCircuits;
    long patternPushes;
    long completedTasks;
    long allInputsBlockedAtSerial = -1;
    @Nullable
    final Integer playerId;
    boolean suspended;

    @FunctionalInterface
    interface CraftingDifferenceListener {
        void onCraftingDifference(AEKey what);
    }

    ExecutingLedgerCraftingJob(LedgerCraftingPlan plan, CraftingDifferenceListener postCraftingDifference,
            CraftingLink link, @Nullable Integer playerId) {
        this.finalOutput = plan.finalOutput();
        this.remainingAmount = finalOutput.amount();
        this.waitingFor = new ListCraftingInventory(postCraftingDifference::onCraftingDifference);
        this.timeTracker = new ElapsedTimeTracker();
        this.link = link;
        this.playerId = playerId;
        this.suspended = false;
        this.nextTaskIndex = 0;

        for (var entry : plan.emittedItems()) {
            waitingFor.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            timeTracker.addMaxItems(entry.getLongValue(), entry.getKey().getType());
        }
        for (var task : plan.tasks()) {
            if (task instanceof PatternCraftingTask patternTask) {
                addPatternTask(patternTask.pattern(), patternTask.times());
                addExpectedOutputs(patternTask.pattern(), patternTask.times());
            }
        }
    }

    ExecutingLedgerCraftingJob(CompoundTag data, HolderLookup.Provider registries,
            CraftingDifferenceListener postCraftingDifference, CraftingCpuLogic cpu) {
        this.link = new CraftingLink(data.getCompound(NBT_LINK), cpu.cluster);
        IGrid grid = cpu.cluster.getGrid();
        if (grid != null) {
            ((CraftingService) grid.getCraftingService()).addLink(link);
        }

        this.finalOutput = GenericStack.readTag(registries, data.getCompound(NBT_FINAL_OUTPUT));
        this.remainingAmount = data.getLong(NBT_REMAINING_AMOUNT);
        this.waitingFor = new ListCraftingInventory(postCraftingDifference::onCraftingDifference);
        this.waitingFor.readFromNBT(data.getList(NBT_WAITING_FOR, Tag.TAG_COMPOUND), registries);
        this.timeTracker = new ElapsedTimeTracker(data.getCompound(NBT_TIME_TRACKER));
        if (data.contains(NBT_PLAYER_ID, Tag.TAG_INT)) {
            this.playerId = data.getInt(NBT_PLAYER_ID);
        } else {
            this.playerId = null;
        }

        ListTag tasksTag = data.getList(NBT_TASKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < tasksTag.size(); i++) {
            var item = tasksTag.getCompound(i);
            var pattern = AEItemKey.fromTag(registries, item);
            var details = PatternDetailsHelper.decodePattern(pattern, cpu.cluster.getLevel());
            if (details != null) {
                addPatternTask(details, item.getLong(NBT_REMAINING_TIMES));
            }
        }

        this.suspended = data.getBoolean(NBT_SUSPENDED);
        this.nextTaskIndex = data.getInt(NBT_NEXT_TASK_INDEX);
        var stats = data.getCompound(NBT_STATS);
        this.providerLookups = stats.getLong(NBT_PROVIDER_LOOKUPS);
        this.providerBusyChecks = stats.getLong(NBT_PROVIDER_BUSY_CHECKS);
        this.busyProviderSkips = stats.getLong(NBT_BUSY_PROVIDER_SKIPS);
        this.inputExtractions = stats.getLong(NBT_INPUT_EXTRACTIONS);
        this.inputUnavailableSkips = stats.getLong(NBT_INPUT_UNAVAILABLE_SKIPS);
        this.inputBlockedSkips = stats.getLong(NBT_INPUT_BLOCKED_SKIPS);
        this.inputBlockedShortCircuits = stats.getLong(NBT_INPUT_BLOCKED_SHORT_CIRCUITS);
        this.patternPushes = stats.getLong(NBT_PATTERN_PUSHES);
        this.completedTasks = stats.getLong(NBT_COMPLETED_TASKS);
    }

    CompoundTag writeToNBT(HolderLookup.Provider registries) {
        var data = new CompoundTag();

        var linkData = new CompoundTag();
        link.writeToNBT(linkData);
        data.put(NBT_LINK, linkData);

        data.put(NBT_FINAL_OUTPUT, GenericStack.writeTag(registries, finalOutput));
        data.put(NBT_WAITING_FOR, waitingFor.writeToNBT(registries));
        data.put(NBT_TIME_TRACKER, timeTracker.writeToNBT());

        var list = new ListTag();
        for (var task : tasks) {
            var item = task.pattern().getDefinition().toTag(registries);
            item.putLong(NBT_REMAINING_TIMES, task.remainingTimes());
            list.add(item);
        }
        data.put(NBT_TASKS, list);

        data.putLong(NBT_REMAINING_AMOUNT, remainingAmount);
        if (playerId != null) {
            data.putInt(NBT_PLAYER_ID, playerId);
        }

        data.putBoolean(NBT_SUSPENDED, suspended);
        data.putInt(NBT_NEXT_TASK_INDEX, nextTaskIndex);
        var stats = new CompoundTag();
        stats.putLong(NBT_PROVIDER_LOOKUPS, providerLookups);
        stats.putLong(NBT_PROVIDER_BUSY_CHECKS, providerBusyChecks);
        stats.putLong(NBT_BUSY_PROVIDER_SKIPS, busyProviderSkips);
        stats.putLong(NBT_INPUT_EXTRACTIONS, inputExtractions);
        stats.putLong(NBT_INPUT_UNAVAILABLE_SKIPS, inputUnavailableSkips);
        stats.putLong(NBT_INPUT_BLOCKED_SKIPS, inputBlockedSkips);
        stats.putLong(NBT_INPUT_BLOCKED_SHORT_CIRCUITS, inputBlockedShortCircuits);
        stats.putLong(NBT_PATTERN_PUSHES, patternPushes);
        stats.putLong(NBT_COMPLETED_TASKS, completedTasks);
        data.put(NBT_STATS, stats);
        return data;
    }

    LedgerExecutionStats executionStats() {
        return new LedgerExecutionStats(
                providerLookups,
                providerBusyChecks,
                busyProviderSkips,
                inputExtractions,
                inputUnavailableSkips,
                inputBlockedSkips,
                inputBlockedShortCircuits,
                patternPushes,
                completedTasks);
    }

    private void addPatternTask(IPatternDetails pattern, long times) {
        if (!tasks.isEmpty()) {
            var lastTask = tasks.getLast();
            if (isSamePattern(lastTask.pattern(), pattern)) {
                lastTask.addTimes(times);
                return;
            }
        }

        tasks.add(new TaskProgress(pattern, times));
    }

    private static boolean isSamePattern(IPatternDetails a, IPatternDetails b) {
        if (a == b) {
            return true;
        }

        try {
            return a.getDefinition().equals(b.getDefinition());
        } catch (UnsupportedOperationException ignored) {
            return false;
        }
    }

    private void addExpectedOutputs(IPatternDetails pattern, long times) {
        for (var output : pattern.getOutputs()) {
            var amount = output.amount() * times * output.what().getAmountPerUnit();
            timeTracker.addMaxItems(amount, output.what().getType());
        }
    }

    static final class TaskProgress {
        private final IPatternDetails pattern;
        private long remainingTimes;
        private long inputUnavailableAtSerial = -1;

        TaskProgress(IPatternDetails pattern, long remainingTimes) {
            this.pattern = pattern;
            this.remainingTimes = remainingTimes;
        }

        IPatternDetails pattern() {
            return pattern;
        }

        long remainingTimes() {
            return remainingTimes;
        }

        void decrement() {
            remainingTimes--;
        }

        void decrement(long amount) {
            remainingTimes -= amount;
        }

        void addTimes(long times) {
            remainingTimes += times;
        }

        boolean isInputBlocked(long cpuInventoryChangeSerial) {
            return inputUnavailableAtSerial == cpuInventoryChangeSerial;
        }

        void markInputUnavailable(long cpuInventoryChangeSerial) {
            inputUnavailableAtSerial = cpuInventoryChangeSerial;
        }

        void clearInputUnavailable() {
            inputUnavailableAtSerial = -1;
        }
    }
}
