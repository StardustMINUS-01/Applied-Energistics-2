/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package appeng.crafting.execution;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.IBulkCraftingProvider;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.core.network.ClientboundPacket;
import appeng.core.network.clientbound.CraftingJobStatusPacket;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.ledger.LedgerCraftingPlan;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.hooks.ticking.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;

/**
 * Stores the crafting logic of a crafting CPU.
 */
public class CraftingCpuLogic {
    private static final Set<String> GREGTECH_MODERN_PATTERN_BUFFER_TYPES = Set.of(
            "com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine");

    final CraftingCPUCluster cluster;
    /**
     * Current job.
     */
    private ExecutingCraftingJob job = null;
    private ExecutingLedgerCraftingJob ledgerJob = null;
    /**
     * Inventory.
     */
    private final ListCraftingInventory inventory = new ListCraftingInventory(CraftingCpuLogic.this::postChange);
    /**
     * Used crafting operations over the last 3 ticks.
     */
    private final int[] usedOps = new int[3];
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    /**
     * True if the CPU is currently trying to clear its inventory but is not able to.
     */
    private boolean cantStoreItems = false;

    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();
    private long cpuInventoryChangeSerial = 0;

    public CraftingCpuLogic(CraftingCPUCluster cluster) {
        this.cluster = cluster;
    }

    public ICraftingSubmitResult trySubmitJob(IGrid grid, ICraftingPlan plan, IActionSource src,
            @Nullable ICraftingRequester requester) {
        // Already have a job.
        if (hasJob())
            return CraftingSubmitResult.CPU_BUSY;
        // Check that the node is active.
        if (!cluster.isActive())
            return CraftingSubmitResult.CPU_OFFLINE;
        // Check bytes.
        if (cluster.getAvailableStorage() < plan.bytes())
            return CraftingSubmitResult.CPU_TOO_SMALL;

        if (!inventory.list.isEmpty())
            AELog.warn("Crafting CPU inventory is not empty yet a job was submitted.");

        // Try to extract required items.
        var missingIngredient = CraftingCpuHelper.tryExtractInitialItems(plan, grid, inventory, src);
        if (missingIngredient != null)
            return CraftingSubmitResult.missingIngredient(missingIngredient);

        // Set CPU link and job.
        var playerId = src.player()
                .map(p -> p instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cluster);
        if (plan instanceof LedgerCraftingPlan ledgerPlan) {
            this.ledgerJob = new ExecutingLedgerCraftingJob(ledgerPlan, this::postChange, linkCpu, playerId);
        } else {
            this.job = new ExecutingCraftingJob(plan, this::postChange, linkCpu, playerId);
        }
        cluster.updateOutput(plan.finalOutput());
        cluster.markDirty();

        // TODO: post monitor difference?

        if (job != null) {
            notifyJobOwner(job, CraftingJobStatusPacket.Status.STARTED);
        } else {
            notifyJobOwner(ledgerJob, CraftingJobStatusPacket.Status.STARTED);
        }

        // Non-standalone jobs need another link for the requester, and both links need to be submitted to the cache.
        if (requester != null) {
            var linkReq = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, false, true), requester);

            var craftingService = (CraftingService) grid.getCraftingService();
            craftingService.addLink(linkCpu);
            craftingService.addLink(linkReq);

            return CraftingSubmitResult.successful(linkReq);
        } else {
            return CraftingSubmitResult.successful(null);
        }
    }

    public void tickCraftingLogic(IEnergyService eg, CraftingService cc) {
        // Don't tick if we're not active.
        if (!cluster.isActive())
            return;
        cantStoreItems = false;
        // If we don't have a job, just try to dump our items.
        if (this.job == null && this.ledgerJob == null) {
            this.storeItems();
            if (!this.inventory.list.isEmpty()) {
                cantStoreItems = true;
            }
            return;
        }
        if (this.ledgerJob != null) {
            if (ledgerJob.link.isCanceled()) {
                cancel();
                return;
            }
            if (ledgerJob.suspended) {
                return;
            }
        } else if (job.link.isCanceled()) {
            cancel();
            return;
        } else if (job.suspended) {
            return;
        }

        var remainingOperations = cluster.getCoProcessors() + 1 - (this.usedOps[0] + this.usedOps[1] + this.usedOps[2]);
        final var started = remainingOperations;

        if (remainingOperations > 0) {
            do {
                var pushedPatterns = executeCrafting(remainingOperations, cc, eg, cluster.getLevel());

                if (pushedPatterns > 0) {
                    remainingOperations -= pushedPatterns;
                } else {
                    break;
                }
            } while (remainingOperations > 0);
        }
        this.usedOps[2] = this.usedOps[1];
        this.usedOps[1] = this.usedOps[0];
        this.usedOps[0] = started - remainingOperations;
    }

    /**
     * Try to push patterns into available interfaces, i.e. do the actual crafting execution.
     *
     * @return How many patterns were successfully pushed.
     */
    public int executeCrafting(int maxPatterns, CraftingService craftingService, IEnergyService energyService,
            Level level) {
        var job = this.job;
        if (job == null) {
            return executeLedgerCrafting(maxPatterns, craftingService, energyService, level);
        }

        var pushedPatterns = 0;

        var it = job.tasks.entrySet().iterator();
        taskLoop: while (it.hasNext()) {
            var task = it.next();
            if (task.getValue().value <= 0) {
                it.remove();
                continue;
            }

            var details = task.getKey();
            var expectedOutputs = new KeyCounter();
            var expectedContainerItems = new KeyCounter();
            // Contains the inputs for the pattern.
            @Nullable
            var craftingContainer = CraftingCpuHelper.extractPatternInputs(
                    details, inventory, level, expectedOutputs, expectedContainerItems);

            // Try to push to each provider.
            for (var provider : craftingService.getProviders(details)) {
                if (craftingContainer == null)
                    break;
                if (provider.isBusy())
                    continue;

                var patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer);

                if (energyService.extractAEPower(patternPower, Actionable.SIMULATE,
                        PowerMultiplier.CONFIG) < patternPower - 0.01)
                    break;

                if (provider.pushPattern(details, craftingContainer)) {
                    energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    pushedPatterns++;

                    for (var expectedOutput : expectedOutputs) {
                        job.waitingFor.insert(expectedOutput.getKey(), expectedOutput.getLongValue(),
                                Actionable.MODULATE);
                    }
                    for (var expectedContainerItem : expectedContainerItems) {
                        job.waitingFor.insert(expectedContainerItem.getKey(), expectedContainerItem.getLongValue(),
                                Actionable.MODULATE);
                        job.timeTracker.addMaxItems(expectedContainerItem.getLongValue(),
                                expectedContainerItem.getKey().getType());
                    }

                    cluster.markDirty();

                    task.getValue().value--;
                    if (task.getValue().value <= 0) {
                        it.remove();
                        continue taskLoop;
                    }

                    if (pushedPatterns == maxPatterns) {
                        break taskLoop;
                    }

                    // Prepare next inputs.
                    expectedOutputs.reset();
                    expectedContainerItems.reset();
                    craftingContainer = CraftingCpuHelper.extractPatternInputs(details, inventory,
                            level, expectedOutputs, expectedContainerItems);
                }
            }

            // Failed to push this pattern, reinject the inputs.
            if (craftingContainer != null) {
                CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
            }
        }

        return pushedPatterns;
    }

    private int executeLedgerCrafting(int maxPatterns, CraftingService craftingService, IEnergyService energyService,
            Level level) {
        var job = this.ledgerJob;
        if (job == null) {
            return 0;
        }
        if (job.allInputsBlockedAtSerial == cpuInventoryChangeSerial) {
            job.inputBlockedShortCircuits++;
            return 0;
        }

        var pushedPatterns = 0;
        var scannedWithoutProgress = 0;
        var onlyInputWaitsPreventedProgress = true;
        taskLoop: while (!job.tasks.isEmpty() && pushedPatterns < maxPatterns
                && scannedWithoutProgress < job.tasks.size()) {
            if (job.nextTaskIndex >= job.tasks.size()) {
                job.nextTaskIndex = 0;
            }

            var task = job.tasks.get(job.nextTaskIndex);
            if (task.remainingTimes() <= 0) {
                job.tasks.remove(job.nextTaskIndex);
                continue;
            }

            var details = task.pattern();
            if (task.isInputBlocked(cpuInventoryChangeSerial)) {
                job.inputBlockedSkips++;
                job.nextTaskIndex++;
                scannedWithoutProgress++;
                continue;
            }

            var providers = new ArrayList<ICraftingProvider>();
            var hasAvailableProvider = false;
            job.providerLookups++;
            for (var provider : craftingService.getProviders(details)) {
                providers.add(provider);
                job.providerBusyChecks++;
                if (!provider.isBusy()) {
                    hasAvailableProvider = true;
                }
            }
            if (!hasAvailableProvider) {
                job.busyProviderSkips += providers.size();
                onlyInputWaitsPreventedProgress = false;
                job.nextTaskIndex++;
                scannedWithoutProgress++;
                continue;
            }

            if (tryPushLedgerTaskBatch(task, providers, energyService, level)) {
                var pushedTimes = task.remainingTimes();
                pushedPatterns++;
                job.patternPushes += pushedTimes;
                task.decrement(pushedTimes);
                job.tasks.remove(job.nextTaskIndex);
                job.completedTasks++;
                scannedWithoutProgress = 0;
                if (pushedPatterns == maxPatterns) {
                    break taskLoop;
                }
                continue;
            }

            var expectedOutputs = new KeyCounter();
            var expectedContainerItems = new KeyCounter();
            @Nullable
            var craftingContainer = CraftingCpuHelper.extractPatternInputs(
                    details, inventory, level, expectedOutputs, expectedContainerItems);
            if (craftingContainer == null) {
                job.inputUnavailableSkips++;
                task.markInputUnavailable(cpuInventoryChangeSerial);
            } else {
                job.inputExtractions++;
                task.clearInputUnavailable();
            }
            var pushedBeforeTask = pushedPatterns;

            for (var provider : providers) {
                while (craftingContainer != null && task.remainingTimes() > 0 && pushedPatterns < maxPatterns) {
                    if (provider.isBusy()) {
                        break;
                    }

                    var patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer);
                    if (energyService.extractAEPower(patternPower, Actionable.SIMULATE,
                            PowerMultiplier.CONFIG) < patternPower - 0.01) {
                        break;
                    }

                    if (!provider.pushPattern(details, craftingContainer)) {
                        break;
                    }

                    craftingContainer = null;
                    energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    pushedPatterns++;
                    job.patternPushes++;

                    addLedgerExpectedOutputs(expectedOutputs, expectedContainerItems);

                    task.decrement();
                    if (task.remainingTimes() <= 0) {
                        job.tasks.remove(job.nextTaskIndex);
                        job.completedTasks++;
                    }
                    scannedWithoutProgress = 0;

                    if (pushedPatterns == maxPatterns) {
                        break taskLoop;
                    }

                    if (task.remainingTimes() <= 0) {
                        continue taskLoop;
                    }

                    expectedOutputs.reset();
                    expectedContainerItems.reset();
                    craftingContainer = CraftingCpuHelper.extractPatternInputs(details, inventory,
                            level, expectedOutputs, expectedContainerItems);
                    if (craftingContainer == null) {
                        job.inputUnavailableSkips++;
                        task.markInputUnavailable(cpuInventoryChangeSerial);
                    } else {
                        job.inputExtractions++;
                        task.clearInputUnavailable();
                    }
                }
            }

            if (craftingContainer != null) {
                CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
            }

            if (pushedPatterns == pushedBeforeTask) {
                if (craftingContainer != null) {
                    onlyInputWaitsPreventedProgress = false;
                }
                job.nextTaskIndex++;
                scannedWithoutProgress++;
            } else if (!job.tasks.isEmpty() && job.nextTaskIndex < job.tasks.size()
                    && job.tasks.get(job.nextTaskIndex) == task) {
                job.nextTaskIndex++;
            }
        }

        if (pushedPatterns == 0 && !job.tasks.isEmpty() && scannedWithoutProgress >= job.tasks.size()
                && onlyInputWaitsPreventedProgress) {
            job.allInputsBlockedAtSerial = cpuInventoryChangeSerial;
        }

        return pushedPatterns;
    }

    private boolean tryPushLedgerTaskBatch(ExecutingLedgerCraftingJob.TaskProgress task,
            ArrayList<ICraftingProvider> providers, IEnergyService energyService, Level level) {
        var job = this.ledgerJob;
        if (job == null || task.remainingTimes() <= 1 || !task.pattern().supportsPushInputsToExternalInventory()) {
            return false;
        }

        var details = task.pattern();
        var times = task.remainingTimes();
        for (var provider : providers) {
            if (provider.isBusy() || !canBatchPushToProvider(details, provider)) {
                continue;
            }

            var expectedOutputs = new KeyCounter();
            var expectedContainerItems = new KeyCounter();
            var craftingContainer = CraftingCpuHelper.extractPatternInputs(
                    details, inventory, level, times, expectedOutputs, expectedContainerItems);
            if (craftingContainer == null) {
                return false;
            }

            var patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer);
            if (energyService.extractAEPower(patternPower, Actionable.SIMULATE,
                    PowerMultiplier.CONFIG) < patternPower - 0.01) {
                CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
                return false;
            }

            if (pushPatternBatch(details, provider, craftingContainer)) {
                energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                addLedgerExpectedOutputs(expectedOutputs, expectedContainerItems);
                task.clearInputUnavailable();
                return true;
            }

            CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
        }

        return false;
    }

    private static boolean canBatchPushToProvider(IPatternDetails details, ICraftingProvider provider) {
        if (provider instanceof IBulkCraftingProvider) {
            return true;
        }
        return details instanceof AEProcessingPattern && isGregTechModernPatternBufferProvider(provider.getClass());
    }

    private static boolean pushPatternBatch(IPatternDetails details, ICraftingProvider provider,
            KeyCounter[] craftingContainer) {
        if (provider instanceof IBulkCraftingProvider bulkProvider) {
            return bulkProvider.pushPatternBatchToExternalInventory(details, craftingContainer);
        }
        return provider.pushPattern(details, craftingContainer);
    }

    private static boolean isGregTechModernPatternBufferProvider(@Nullable Class<?> type) {
        if (type == null) {
            return false;
        }
        if (GREGTECH_MODERN_PATTERN_BUFFER_TYPES.contains(type.getName())) {
            return true;
        }
        return isGregTechModernPatternBufferProvider(type.getSuperclass());
    }

    private void addLedgerExpectedOutputs(KeyCounter expectedOutputs, KeyCounter expectedContainerItems) {
        var job = this.ledgerJob;
        if (job == null) {
            return;
        }

        for (var expectedOutput : expectedOutputs) {
            job.waitingFor.insert(expectedOutput.getKey(), expectedOutput.getLongValue(), Actionable.MODULATE);
        }
        for (var expectedContainerItem : expectedContainerItems) {
            job.waitingFor.insert(expectedContainerItem.getKey(), expectedContainerItem.getLongValue(),
                    Actionable.MODULATE);
            job.timeTracker.addMaxItems(expectedContainerItem.getLongValue(), expectedContainerItem.getKey().getType());
        }

        cluster.markDirty();
    }

    /**
     * Called by the CraftingService with an Integer.MAX_VALUE priority to inject items that are being waited for.
     *
     * @return Consumed amount.
     */
    public long insert(AEKey what, long amount, Actionable type) {
        // also stop accepting items when the job is complete, i.e. to prevent re-insertion when pushing out
        // items during storeItems
        if (what == null)
            return 0;

        if (ledgerJob != null) {
            return insertIntoLedgerJob(what, amount, type);
        }

        if (job == null) {
            return 0;
        }

        // Only accept items we are waiting for.
        var waitingFor = job.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (waitingFor <= 0) {
            return 0;
        }

        // Make sure we don't insert more than what we are waiting for.
        if (amount > waitingFor) {
            amount = waitingFor;
        }

        if (type == Actionable.MODULATE) {
            job.timeTracker.decrementItems(amount, what.getType()); // Process Fluid and Items
            job.waitingFor.extract(what, amount, Actionable.MODULATE);
            cluster.markDirty();
        }

        long inserted = amount;
        if (what.matches(job.finalOutput)) {
            // Final output is special: it goes directly into the requester
            inserted = job.link.insert(what, amount, type);

            // Note: we ignore any remainder (could be the entire input if there is no requester),
            // we already marked the items as done, and we might even finish the job.

            // This means that the job can be marked as finished even if some items were not actually inserted.
            // In some cases, repeated failed inserts of a fraction of the final output might prevent some recipes from
            // being pushed.
            // TODO: Look into fixing this, perhaps we could use the network monitor to check how much was really
            // TODO: inserted into the network.
            // TODO: Another solution is to wait until all recipes have been pushed before cancelling the job.

            if (type == Actionable.MODULATE) {
                // Update count and displayed CPU stack, and finish the job if possible.
                postChange(what);
                job.remainingAmount = Math.max(0, job.remainingAmount - amount);

                if (job.remainingAmount <= 0) {
                    finishJob(true);
                    cluster.updateOutput(null);
                } else {
                    cluster.updateOutput(new GenericStack(job.finalOutput.what(), job.remainingAmount));
                }
            }
        } else {
            if (type == Actionable.MODULATE) {
                inventory.insert(what, amount, Actionable.MODULATE);
            }
        }

        return inserted;
    }

    private long insertIntoLedgerJob(AEKey what, long amount, Actionable type) {
        var job = this.ledgerJob;
        if (job == null) {
            return 0;
        }

        var waitingFor = job.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (waitingFor <= 0) {
            return 0;
        }

        if (amount > waitingFor) {
            amount = waitingFor;
        }

        if (type == Actionable.MODULATE) {
            job.timeTracker.decrementItems(amount, what.getType());
            job.waitingFor.extract(what, amount, Actionable.MODULATE);
            cluster.markDirty();
        }

        long inserted = amount;
        if (what.matches(job.finalOutput)) {
            inserted = job.link.insert(what, amount, type);

            if (type == Actionable.MODULATE) {
                postChange(what);
                job.remainingAmount = Math.max(0, job.remainingAmount - amount);

                if (job.remainingAmount <= 0) {
                    finishLedgerJob(true);
                    cluster.updateOutput(null);
                } else {
                    cluster.updateOutput(new GenericStack(job.finalOutput.what(), job.remainingAmount));
                }
            }
        } else if (type == Actionable.MODULATE) {
            inventory.insert(what, amount, Actionable.MODULATE);
            cpuInventoryChangeSerial++;
        }

        return inserted;
    }

    /**
     * Finish the current job.
     *
     * @param success True if the job is complete, false if it was cancelled.
     */
    private void finishJob(boolean success) {
        if (success) {
            job.link.markDone();
        } else {
            job.link.cancel();
        }

        // TODO: log

        // Clear waitingFor list and post all the relevant changes.
        job.waitingFor.clear();
        // Notify opened menus of cancelled scheduled tasks.
        for (var entry : job.tasks.entrySet()) {
            for (var output : entry.getKey().getOutputs()) {
                postChange(output.what());
            }
        }

        notifyJobOwner(job,
                success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);

        // Finish job.
        this.job = null;

        // Store all remaining items.
        this.storeItems();
    }

    private void finishLedgerJob(boolean success) {
        if (success) {
            ledgerJob.link.markDone();
        } else {
            ledgerJob.link.cancel();
        }

        ledgerJob.waitingFor.clear();
        for (var task : ledgerJob.tasks) {
            for (var output : task.pattern().getOutputs()) {
                postChange(output.what());
            }
        }

        notifyJobOwner(ledgerJob,
                success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);

        this.ledgerJob = null;
        this.storeItems();
    }

    /**
     * Cancel the current job.
     */
    public void cancel() {
        // No job to cancel :P
        if (job == null && ledgerJob == null)
            return;

        // Clear displayed stack.
        cluster.updateOutput(null);

        if (ledgerJob != null) {
            finishLedgerJob(false);
        } else {
            finishJob(false);
        }
    }

    /**
     * Tries to dump all locally stored items back into the storage network.
     */
    public void storeItems() {
        Preconditions.checkState(job == null && ledgerJob == null,
                "CPU should not have a job to prevent re-insertion when dumping items");
        // Short-circuit if there is nothing to do.
        if (this.inventory.list.isEmpty())
            return;

        var g = cluster.getGrid();
        if (g == null)
            return;

        var storage = g.getStorageService().getInventory();

        for (var entry : this.inventory.list) {
            this.postChange(entry.getKey());
            var inserted = storage.insert(entry.getKey(), entry.getLongValue(),
                    Actionable.MODULATE, cluster.getSrc());

            // The network was unable to receive all of the items, i.e. no or not enough storage space left
            entry.setValue(entry.getLongValue() - inserted);
        }
        this.inventory.list.removeZeros();

        cluster.markDirty();
    }

    private void postChange(AEKey what) {
        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        for (var listener : listeners) {
            listener.accept(what);
        }
    }

    public long getLastModifiedOnTick() {
        return lastModifiedOnTick;
    }

    public boolean hasJob() {
        return this.job != null || this.ledgerJob != null;
    }

    boolean isLedgerJobActive() {
        return this.ledgerJob != null;
    }

    LedgerExecutionStats getLedgerExecutionStats() {
        return this.ledgerJob != null ? this.ledgerJob.executionStats() : LedgerExecutionStats.EMPTY;
    }

    @Nullable
    public GenericStack getFinalJobOutput() {
        if (this.job != null) {
            return this.job.finalOutput;
        }
        return this.ledgerJob != null ? this.ledgerJob.finalOutput : null;
    }

    public ElapsedTimeTracker getElapsedTimeTracker() {
        if (this.job != null) {
            return this.job.timeTracker;
        } else if (this.ledgerJob != null) {
            return this.ledgerJob.timeTracker;
        } else {
            return new ElapsedTimeTracker();
        }
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        this.inventory.readFromNBT(data.getList("inventory", 10), registries);
        if (data.contains("job")) {
            this.job = new ExecutingCraftingJob(data.getCompound("job"), registries, this::postChange, this);
            if (this.job.finalOutput == null) {
                finishJob(false);
            } else {
                cluster.updateOutput(new GenericStack(job.finalOutput.what(), job.remainingAmount));
            }
        } else if (data.contains("ledgerJob")) {
            this.ledgerJob = new ExecutingLedgerCraftingJob(data.getCompound("ledgerJob"), registries,
                    this::postChange, this);
            if (this.ledgerJob.finalOutput == null) {
                finishLedgerJob(false);
            } else {
                cluster.updateOutput(new GenericStack(ledgerJob.finalOutput.what(), ledgerJob.remainingAmount));
            }
        } else {
            cluster.updateOutput(null);
        }
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        data.put("inventory", this.inventory.writeToNBT(registries));
        if (this.job != null) {
            data.put("job", this.job.writeToNBT(registries));
        } else if (this.ledgerJob != null) {
            data.put("ledgerJob", this.ledgerJob.writeToNBT(registries));
        }
    }

    public ICraftingLink getLastLink() {
        if (this.job != null) {
            return this.job.link;
        }
        if (this.ledgerJob != null) {
            return this.ledgerJob.link;
        }
        return null;
    }

    public ListCraftingInventory getInventory() {
        return this.inventory;
    }

    /**
     * Register a listener that will receive stacks when either the stored items, await items or pending outputs change.
     * This is only used by the menu. Make sure to remove it by calling {@link #removeListener}.
     */
    public void addListener(Consumer<AEKey> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<AEKey> listener) {
        listeners.remove(listener);
    }

    public long getStored(AEKey template) {
        return this.inventory.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    public long getWaitingFor(AEKey template) {
        if (this.job != null) {
            return this.job.waitingFor.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
        } else if (this.ledgerJob != null) {
            return this.ledgerJob.waitingFor.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
        }
        return 0;
    }

    public void getAllWaitingFor(Set<AEKey> waitingFor) {
        if (this.job != null) {
            for (var entry : this.job.waitingFor.list) {
                waitingFor.add(entry.getKey());
            }
        } else if (this.ledgerJob != null) {
            for (var entry : this.ledgerJob.waitingFor.list) {
                waitingFor.add(entry.getKey());
            }
        }
    }

    public long getPendingOutputs(AEKey template) {
        long count = 0;
        if (this.job != null) {
            for (var t : job.tasks.entrySet()) {
                for (var output : t.getKey().getOutputs()) {
                    if (template.matches(output)) {
                        count += output.amount() * t.getValue().value;
                    }
                }
            }
        } else if (this.ledgerJob != null) {
            for (var task : ledgerJob.tasks) {
                for (var output : task.pattern().getOutputs()) {
                    if (template.matches(output)) {
                        count += output.amount() * task.remainingTimes();
                    }
                }
            }
        }
        return count;
    }

    /**
     * Used by the menu to gather all the kinds of stored items.
     */
    public void getAllItems(KeyCounter out) {
        out.addAll(this.inventory.list);
        if (this.job != null) {
            out.addAll(job.waitingFor.list);
            for (var t : job.tasks.entrySet()) {
                for (var output : t.getKey().getOutputs()) {
                    out.add(output.what(), output.amount() * t.getValue().value);
                }
            }
        } else if (this.ledgerJob != null) {
            out.addAll(ledgerJob.waitingFor.list);
            for (var task : ledgerJob.tasks) {
                for (var output : task.pattern().getOutputs()) {
                    out.add(output.what(), output.amount() * task.remainingTimes());
                }
            }
        }
    }

    public boolean isCantStoreItems() {
        return cantStoreItems;
    }

    public boolean isJobSuspended() {
        return job != null && job.suspended || ledgerJob != null && ledgerJob.suspended;
    }

    public void setJobSuspended(boolean suspended) {
        if (job != null && job.suspended != suspended) {
            job.suspended = suspended;
        } else if (ledgerJob != null && ledgerJob.suspended != suspended) {
            ledgerJob.suspended = suspended;
        }
    }

    private void notifyJobOwner(ExecutingCraftingJob job, CraftingJobStatusPacket.Status status) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();

        var playerId = job.playerId;
        if (playerId == null) {
            return;
        }

        var server = cluster.getLevel().getServer();
        var connectedPlayer = IPlayerRegistry.getConnected(server, playerId);
        if (connectedPlayer != null) {
            var jobId = job.link.getCraftingID();
            ClientboundPacket message = new CraftingJobStatusPacket(
                    jobId,
                    job.finalOutput.what(),
                    job.finalOutput.amount(),
                    job.remainingAmount,
                    status);
            connectedPlayer.connection.send(message);
        }
    }

    private void notifyJobOwner(ExecutingLedgerCraftingJob job, CraftingJobStatusPacket.Status status) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();

        var playerId = job.playerId;
        if (playerId == null) {
            return;
        }

        var server = cluster.getLevel().getServer();
        var connectedPlayer = IPlayerRegistry.getConnected(server, playerId);
        if (connectedPlayer != null) {
            var jobId = job.link.getCraftingID();
            ClientboundPacket message = new CraftingJobStatusPacket(
                    jobId,
                    job.finalOutput.what(),
                    job.finalOutput.amount(),
                    job.remainingAmount,
                    status);
            connectedPlayer.connection.send(message);
        }
    }
}
