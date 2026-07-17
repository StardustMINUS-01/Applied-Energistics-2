package appeng.crafting.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingStartMode;
import appeng.api.networking.crafting.IBulkCraftingProvider;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.ledger.LedgerCraftingPlan;
import appeng.crafting.ledger.LedgerCraftingPlanner;
import appeng.crafting.ledger.PatternCraftingTask;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.BaseActionSource;
import appeng.me.service.CraftingService;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class CraftingCpuLogicTest {
    private final RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    @Test
    void submitsLedgerPlanAsLedgerJob() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(mock(MEStorage.class));
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var plan = new LedgerCraftingPlan(output, 64, false, false, new KeyCounter(), new KeyCounter(),
                new KeyCounter(), Map.of(), List.of());
        var logic = new CraftingCpuLogic(cluster);

        var result = logic.trySubmitJob(grid, plan, new BaseActionSource(), null);

        assertThat(result.successful()).isTrue();
        assertThat(logic.hasJob()).isTrue();
        assertThat(logic.isLedgerJobActive()).isTrue();
    }

    @Test
    void forceStartSubmitStartsJobAndTracksMissingInitialItems() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.STONE), 10);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(4L), eq(Actionable.MODULATE), eq(src))).thenReturn(4L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 4);
        var missingItems = new KeyCounter();
        missingItems.add(input.what(), 6);
        var plan = new LedgerCraftingPlan(output, 64, true, false, usedItems, new KeyCounter(), missingItems,
                Map.of(), List.of());
        var logic = new CraftingCpuLogic(cluster);

        var normalResult = logic.trySubmitJob(grid, plan, src, null);
        assertThat(normalResult.successful()).isFalse();
        assertThat(logic.hasJob()).isFalse();

        var forceStartResult = logic.trySubmitJob(grid, plan, src, null, CraftingStartMode.FORCE_START);

        assertThat(forceStartResult.successful()).isTrue();
        assertThat(logic.hasJob()).isTrue();
        assertThat(logic.getWaitingFor(input.what())).isEqualTo(6);

        var allWaitingFor = new java.util.HashSet<appeng.api.stacks.AEKey>();
        logic.getAllWaitingFor(allWaitingFor);
        assertThat(allWaitingFor).contains(input.what());

        var allItems = new KeyCounter();
        logic.getAllItems(allItems);
        assertThat(allItems.get(input.what())).isEqualTo(10);
    }

    @Test
    void forceStartInsertConsumesDebtOnlyWhenModulating() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.STONE), 10);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(4L), eq(Actionable.MODULATE), eq(src))).thenReturn(4L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 4);
        var missingItems = new KeyCounter();
        missingItems.add(input.what(), 6);
        var plan = new LedgerCraftingPlan(output, 64, true, false, usedItems, new KeyCounter(), missingItems,
                Map.of(), List.of());
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null, CraftingStartMode.FORCE_START).successful()).isTrue();

        assertThat(logic.insert(input.what(), 3, Actionable.SIMULATE)).isEqualTo(3);
        assertThat(logic.getWaitingFor(input.what())).isEqualTo(6);
        assertThat(logic.getStored(input.what())).isEqualTo(4);

        assertThat(logic.insert(input.what(), 99, Actionable.MODULATE)).isEqualTo(6);

        assertThat(logic.getWaitingFor(input.what())).isZero();
        assertThat(logic.getStored(input.what())).isEqualTo(10);
    }

    @Test
    void forceStartSupplyUnblocksLedgerTaskDispatch() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.OAK_LOG), 5);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(5L), eq(Actionable.MODULATE), eq(src))).thenReturn(0L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 5);
        var missingItems = new KeyCounter();
        missingItems.add(input.what(), 5);
        var pattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(output)
                .addPreciseInput(1, input)
                .build();
        var plan = new LedgerCraftingPlan(output, 64, true, false, usedItems, new KeyCounter(), missingItems,
                Map.of(pattern, 1L), List.of(new PatternCraftingTask(pattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null, CraftingStartMode.FORCE_START).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var provider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(pattern)).thenReturn(List.of(provider));
        when(provider.pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isZero();
        assertThat(logic.getLedgerExecutionStats().inputUnavailableSkips()).isEqualTo(1);

        assertThat(logic.insert(input.what(), 5, Actionable.MODULATE)).isEqualTo(5);

        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(1);
        assertThat(logic.getWaitingFor(output.what())).isEqualTo(1);
        assertThat(logic.getStored(input.what())).isZero();
        verify(provider).pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void forceStartPartialSupplyDoesNotDispatchUntilPatternInputIsComplete() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.OAK_LOG), 10);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(10L), eq(Actionable.MODULATE), eq(src))).thenReturn(0L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 10);
        var missingItems = new KeyCounter();
        missingItems.add(input.what(), 10);
        var pattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(output)
                .addPreciseInput(1, input)
                .build();
        var plan = new LedgerCraftingPlan(output, 64, true, false, usedItems, new KeyCounter(), missingItems,
                Map.of(pattern, 1L), List.of(new PatternCraftingTask(pattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null, CraftingStartMode.FORCE_START).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var provider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(pattern)).thenReturn(List.of(provider));
        when(provider.pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(logic.insert(input.what(), 5, Actionable.MODULATE)).isEqualTo(5);
        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isZero();
        assertThat(logic.getStored(input.what())).isEqualTo(5);

        assertThat(logic.insert(input.what(), 5, Actionable.MODULATE)).isEqualTo(5);
        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(1);
        assertThat(logic.getStored(input.what())).isZero();
        verify(provider).pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void forceStartExecutesPlannerAttemptAfterAllMissingInputsAreSupplied() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.OAK_LOG), 5);
        var output = new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 10);
        var pattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(output)
                .addPreciseInput(1, input)
                .build();
        var plan = new LedgerCraftingPlanner(new KeyCounter(), Map.of(output.what(), List.of(pattern)))
                .plan(output, CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThat(plan.simulation()).isTrue();
        assertThat(plan.missingItems().get(input.what())).isEqualTo(5);
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null, CraftingStartMode.FORCE_START).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var provider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(pattern)).thenReturn(List.of(provider));
        when(provider.pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(logic.insert(input.what(), 5, Actionable.MODULATE)).isEqualTo(5);

        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(1);
        verify(provider).pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void readsLegacyManualSupplyNbtAsForceStartDebt() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.STONE), 10);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(4L), eq(Actionable.MODULATE), eq(src))).thenReturn(4L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 4);
        var missingItems = new KeyCounter();
        missingItems.add(input.what(), 6);
        var plan = new LedgerCraftingPlan(output, 64, true, false, usedItems, new KeyCounter(), missingItems,
                Map.of(), List.of());
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null, CraftingStartMode.FORCE_START).successful()).isTrue();
        var legacyTag = new CompoundTag();
        logic.writeToNBT(legacyTag, registries);
        legacyTag.put("manualSupply", legacyTag.get("forceStart"));
        legacyTag.remove("forceStart");

        var restored = new CraftingCpuLogic(cluster);
        restored.readFromNBT(legacyTag, registries);

        assertThat(restored.hasJob()).isTrue();
        assertThat(restored.getWaitingFor(input.what())).isEqualTo(6);
        assertThat(restored.getStored(input.what())).isEqualTo(4);
    }

    @Test
    void executesNextLedgerPatternTask() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 1);
        var pattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(output)
                .addPreciseInput(1, input)
                .build();
        var plan = new LedgerCraftingPlan(output, 64, false, false, usedItems, new KeyCounter(), new KeyCounter(),
                Map.of(pattern, 1L), List.of(new PatternCraftingTask(pattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var provider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(pattern)).thenReturn(List.of(provider));
        when(provider.pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var pushed = logic.executeCrafting(1, craftingService, energy, mock(Level.class));

        assertThat(pushed).isEqualTo(1);
        assertThat(logic.getWaitingFor(output.what())).isEqualTo(1);
        assertThat(logic.getPendingOutputs(output.what())).isEqualTo(0);
        verify(provider).pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any());

        logic.insert(output.what(), 1, Actionable.MODULATE);

        assertThat(logic.hasJob()).isFalse();
        assertThat(logic.isLedgerJobActive()).isFalse();
    }

    @Test
    void bulkPushesExternalInventoryLedgerTaskInOneDispatch() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(50L), eq(Actionable.MODULATE), eq(src))).thenReturn(50L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 50);
        var pattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(output)
                .addPreciseInput(1, input)
                .build();
        var plan = new LedgerCraftingPlan(output, 64, false, false, usedItems, new KeyCounter(), new KeyCounter(),
                Map.of(pattern, 50L), List.of(new PatternCraftingTask(pattern, 50)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var provider = new RecordingBulkProvider();
        when(craftingService.getProviders(pattern)).thenReturn(List.of(provider));
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var pushed = logic.executeCrafting(1, craftingService, energy, mock(Level.class));

        assertThat(pushed).isEqualTo(1);
        assertThat(provider.bulkPushes).isEqualTo(1);
        assertThat(provider.bulkInputHolder[0].get(input.what())).isEqualTo(50);
        assertThat(provider.pushes).isEqualTo(0);
        assertThat(logic.getWaitingFor(output.what())).isEqualTo(50);
        assertThat(logic.getPendingOutputs(output.what())).isEqualTo(0);
    }

    @Test
    void bulkPushesGregTechModernPatternBufferInOneDispatch() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(50L), eq(Actionable.MODULATE), eq(src))).thenReturn(50L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 50);
        var pattern = PatternDetailsHelper.decodePattern(AEItemKey.of(PatternDetailsHelper.encodeProcessingPattern(
                List.of(input),
                List.of(output))), mock(Level.class));
        var plan = new LedgerCraftingPlan(output, 64, false, false, usedItems, new KeyCounter(), new KeyCounter(),
                Map.of(pattern, 50L), List.of(new PatternCraftingTask(pattern, 50)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var provider = new com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine();
        when(craftingService.getProviders(pattern)).thenReturn(List.of(provider));
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var pushed = logic.executeCrafting(1, craftingService, energy, mock(Level.class));

        assertThat(pushed).isEqualTo(1);
        assertThat(provider.pushes).isEqualTo(1);
        assertThat(provider.lastInputHolder[0].get(input.what())).isEqualTo(50);
        assertThat(logic.getWaitingFor(output.what())).isEqualTo(50);
        assertThat(logic.getPendingOutputs(output.what())).isEqualTo(0);
    }

    @Test
    void doesNotBatchGtlPatternPartInterface() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(50L), eq(Actionable.MODULATE), eq(src))).thenReturn(50L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 50);
        var pattern = PatternDetailsHelper.decodePattern(AEItemKey.of(PatternDetailsHelper.encodeProcessingPattern(
                List.of(input),
                List.of(output))), mock(Level.class));
        var plan = new LedgerCraftingPlan(output, 64, false, false, usedItems, new KeyCounter(), new KeyCounter(),
                Map.of(pattern, 50L), List.of(new PatternCraftingTask(pattern, 50)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var provider = new RecordingGtlPatternPartProvider();
        when(craftingService.getProviders(pattern)).thenReturn(List.of(provider));
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var pushed = logic.executeCrafting(1, craftingService, energy, mock(Level.class));

        assertThat(pushed).isEqualTo(1);
        assertThat(provider.pushes).isEqualTo(1);
        assertThat(provider.lastInputHolder[0].get(input.what())).isEqualTo(1);
        assertThat(logic.getWaitingFor(output.what())).isEqualTo(1);
        assertThat(logic.getPendingOutputs(output.what())).isEqualTo(49);
    }

    @Test
    void doesNotBatchPlainProviderThatDoesNotDeclareBulkCapability() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(50L), eq(Actionable.MODULATE), eq(src))).thenReturn(50L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 50);
        var pattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(output)
                .addPreciseInput(1, input)
                .build();
        var plan = new LedgerCraftingPlan(output, 64, false, false, usedItems, new KeyCounter(), new KeyCounter(),
                Map.of(pattern, 50L), List.of(new PatternCraftingTask(pattern, 50)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var provider = new RecordingProvider();
        when(craftingService.getProviders(pattern)).thenReturn(List.of(provider));
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var pushed = logic.executeCrafting(1, craftingService, energy, mock(Level.class));

        assertThat(pushed).isEqualTo(1);
        assertThat(provider.pushes).isEqualTo(1);
        assertThat(provider.lastInputHolder[0].get(input.what())).isEqualTo(1);
        assertThat(logic.getWaitingFor(output.what())).isEqualTo(1);
        assertThat(logic.getPendingOutputs(output.what())).isEqualTo(49);
    }

    @Test
    void doesNotBulkPushPatternsThatRejectExternalInventoryInputs() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(3L), eq(Actionable.MODULATE), eq(src))).thenReturn(3L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 3);
        var pattern = mock(IPatternDetails.class);
        var patternInput = mock(IPatternDetails.IInput.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { patternInput });
        when(pattern.getOutputs()).thenReturn(List.of(output));
        when(pattern.supportsPushInputsToExternalInventory()).thenReturn(false);
        when(patternInput.getMultiplier()).thenReturn(1L);
        when(patternInput.getPossibleInputs()).thenReturn(new GenericStack[] { input });
        when(patternInput.isValid(eq(input.what()), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var plan = new LedgerCraftingPlan(output, 64, false, false, usedItems, new KeyCounter(), new KeyCounter(),
                Map.of(pattern, 3L), List.of(new PatternCraftingTask(pattern, 3)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var provider = new RecordingBulkProvider();
        when(craftingService.getProviders(pattern)).thenReturn(List.of(provider));
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var pushed = logic.executeCrafting(2, craftingService, energy, mock(Level.class));

        assertThat(pushed).isEqualTo(2);
        assertThat(provider.bulkPushes).isZero();
        assertThat(provider.pushes).isEqualTo(2);
        assertThat(logic.getWaitingFor(output.what())).isEqualTo(2);
        assertThat(logic.getPendingOutputs(output.what())).isEqualTo(1);
    }

    @Test
    void fallsBackToSingleDispatchWhenBulkProviderRejectsBatch() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(3L), eq(Actionable.MODULATE), eq(src))).thenReturn(3L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 3);
        var pattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(output)
                .addPreciseInput(1, input)
                .build();
        var plan = new LedgerCraftingPlan(output, 64, false, false, usedItems, new KeyCounter(), new KeyCounter(),
                Map.of(pattern, 3L), List.of(new PatternCraftingTask(pattern, 3)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var provider = new RecordingBulkProvider();
        provider.bulkResult = false;
        when(craftingService.getProviders(pattern)).thenReturn(List.of(provider));
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var pushed = logic.executeCrafting(2, craftingService, energy, mock(Level.class));

        assertThat(pushed).isEqualTo(2);
        assertThat(provider.bulkPushes).isEqualTo(1);
        assertThat(provider.pushes).isEqualTo(2);
        assertThat(logic.getWaitingFor(output.what())).isEqualTo(2);
        assertThat(logic.getPendingOutputs(output.what())).isEqualTo(1);
    }

    @Test
    void restoresLedgerJobFromNbt() {
        var cluster = mock(CraftingCPUCluster.class);
        var level = mock(Level.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        when(cluster.getLevel()).thenReturn(level);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 2);
        when(storage.extract(eq(input.what()), eq(2L), eq(Actionable.MODULATE), eq(src))).thenReturn(2L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 2);
        var pattern = PatternDetailsHelper.decodePattern(AEItemKey.of(PatternDetailsHelper.encodeProcessingPattern(
                List.of(input),
                List.of(new GenericStack(output.what(), 1)))), level);
        var plan = new LedgerCraftingPlan(output, 64, false, false, usedItems, new KeyCounter(), new KeyCounter(),
                Map.of(pattern, 2L), List.of(new PatternCraftingTask(pattern, 2)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var provider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(pattern)).thenReturn(List.of(provider));
        when(provider.pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(logic.executeCrafting(1, craftingService, energy, level)).isEqualTo(1);

        var tag = new CompoundTag();
        logic.writeToNBT(tag, registries);
        var restored = new CraftingCpuLogic(cluster);
        restored.readFromNBT(tag, registries);

        assertThat(restored.hasJob()).isTrue();
        assertThat(restored.isLedgerJobActive()).isTrue();
        assertThat(restored.getWaitingFor(output.what())).isEqualTo(1);
        assertThat(restored.getPendingOutputs(output.what())).isEqualTo(1);
        assertThat(restored.getFinalJobOutput()).isEqualTo(output);
        assertThat(restored.getLedgerExecutionStats().providerLookups()).isEqualTo(1);
        assertThat(restored.getLedgerExecutionStats().inputExtractions()).isEqualTo(1);
        assertThat(restored.getLedgerExecutionStats().patternPushes()).isEqualTo(1);
    }

    @Test
    void tickCraftingLogicExecutesLedgerTask() {
        var cluster = mock(CraftingCPUCluster.class);
        var level = mock(Level.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        when(cluster.getLevel()).thenReturn(level);
        when(cluster.getCoProcessors()).thenReturn(0);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 1);
        var pattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(output)
                .addPreciseInput(1, input)
                .build();
        var plan = new LedgerCraftingPlan(output, 64, false, false, usedItems, new KeyCounter(), new KeyCounter(),
                Map.of(pattern, 1L), List.of(new PatternCraftingTask(pattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var provider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(pattern)).thenReturn(List.of(provider));
        when(provider.pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        logic.tickCraftingLogic(energy, craftingService);

        assertThat(logic.getWaitingFor(output.what())).isEqualTo(1);
        assertThat(logic.getPendingOutputs(output.what())).isEqualTo(0);
        verify(provider).pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void storeItemsRejectsActiveLedgerJob() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 1);
        var plan = new LedgerCraftingPlan(output, 64, false, false, usedItems, new KeyCounter(), new KeyCounter(),
                Map.of(), List.of());
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();

        assertThatThrownBy(logic::storeItems)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CPU should not have a job");
    }

    @Test
    void cancelLedgerJobDumpsStoredInputsBackToNetwork() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        when(cluster.getGrid()).thenReturn(grid);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        when(cluster.getSrc()).thenReturn(src);
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        when(storage.insert(eq(input.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 1);
        var plan = new LedgerCraftingPlan(output, 64, false, false, usedItems, new KeyCounter(), new KeyCounter(),
                Map.of(), List.of());
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();

        logic.cancel();

        assertThat(logic.hasJob()).isFalse();
        assertThat(logic.getStored(input.what())).isEqualTo(0);
        verify(storage, times(1)).insert(eq(input.what()), eq(1L), eq(Actionable.MODULATE), eq(src));
    }

    @Test
    void completesMultiStepLedgerJobThroughTicks() {
        var cluster = mock(CraftingCPUCluster.class);
        var level = mock(Level.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        when(cluster.getLevel()).thenReturn(level);
        when(cluster.getCoProcessors()).thenReturn(3);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var intermediate = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(input.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 1);
        var childPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(intermediate)
                .addPreciseInput(1, input)
                .build();
        var parentPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(output)
                .addPreciseInput(1, intermediate)
                .build();
        var plan = new LedgerCraftingPlan(output, 64, false, false, usedItems, new KeyCounter(), new KeyCounter(),
                Map.of(childPattern, 1L, parentPattern, 1L),
                List.of(new PatternCraftingTask(childPattern, 1), new PatternCraftingTask(parentPattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var childProvider = mock(ICraftingProvider.class);
        var parentProvider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(childPattern)).thenReturn(List.of(childProvider));
        when(craftingService.getProviders(parentPattern)).thenReturn(List.of(parentProvider));
        when(childProvider.pushPattern(eq(childPattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(parentProvider.pushPattern(eq(parentPattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        logic.tickCraftingLogic(energy, craftingService);
        assertThat(logic.getWaitingFor(intermediate.what())).isEqualTo(1);
        assertThat(logic.getPendingOutputs(output.what())).isEqualTo(1);

        assertThat(logic.insert(intermediate.what(), 1, Actionable.MODULATE)).isEqualTo(1);
        logic.tickCraftingLogic(energy, craftingService);
        assertThat(logic.getWaitingFor(output.what())).isEqualTo(1);
        assertThat(logic.getPendingOutputs(output.what())).isEqualTo(0);

        assertThat(logic.insert(output.what(), 1, Actionable.MODULATE)).isEqualTo(0);
        assertThat(logic.hasJob()).isFalse();
    }

    @Test
    void executeLedgerCraftingRespectsMaxPatternsAcrossCompletedTasks() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var firstInput = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var secondInput = new GenericStack(AEItemKey.of(Items.DIRT), 1);
        var firstOutput = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var secondOutput = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(firstInput.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        when(storage.extract(eq(secondInput.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        var usedItems = new KeyCounter();
        usedItems.add(firstInput.what(), 1);
        usedItems.add(secondInput.what(), 1);
        var firstPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(firstOutput)
                .addPreciseInput(1, firstInput)
                .build();
        var secondPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(secondOutput)
                .addPreciseInput(1, secondInput)
                .build();
        var plan = new LedgerCraftingPlan(secondOutput, 64, false, false, usedItems, new KeyCounter(),
                new KeyCounter(), Map.of(firstPattern, 1L, secondPattern, 1L),
                List.of(new PatternCraftingTask(firstPattern, 1), new PatternCraftingTask(secondPattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var firstProvider = mock(ICraftingProvider.class);
        var secondProvider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(firstPattern)).thenReturn(List.of(firstProvider));
        when(craftingService.getProviders(secondPattern)).thenReturn(List.of(secondProvider));
        when(firstProvider.pushPattern(eq(firstPattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(secondProvider.pushPattern(eq(secondPattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(1);

        assertThat(logic.getWaitingFor(firstOutput.what())).isEqualTo(1);
        assertThat(logic.getWaitingFor(secondOutput.what())).isEqualTo(0);
        verify(firstProvider).pushPattern(eq(firstPattern), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verifyNoInteractions(secondProvider);
    }

    @Test
    void executeLedgerCraftingCanPushSameTaskRepeatedlyToSameAvailableProvider() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.STONE), 1);
        when(storage.extract(eq(input.what()), eq(3L), eq(Actionable.MODULATE), eq(src))).thenReturn(3L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 3);
        var pattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(output)
                .addPreciseInput(1, input)
                .build();
        var plan = new LedgerCraftingPlan(output, 64, false, false, usedItems, new KeyCounter(), new KeyCounter(),
                Map.of(pattern, 3L), List.of(new PatternCraftingTask(pattern, 3)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var provider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(pattern)).thenReturn(List.of(provider));
        when(provider.pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(logic.executeCrafting(3, craftingService, energy, mock(Level.class))).isEqualTo(3);

        verify(provider, times(3)).pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any());
        assertThat(logic.getWaitingFor(output.what())).isEqualTo(3);
        assertThat(logic.getPendingOutputs(output.what())).isEqualTo(0);
        assertThat(logic.getLedgerExecutionStats().patternPushes()).isEqualTo(3);
        assertThat(logic.getLedgerExecutionStats().inputExtractions()).isEqualTo(3);
    }

    @Test
    void groupsDuplicateLedgerPatternTasksBeforeExecution() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.STONE), 2);
        when(storage.extract(eq(input.what()), eq(2L), eq(Actionable.MODULATE), eq(src))).thenReturn(2L);
        var usedItems = new KeyCounter();
        usedItems.add(input.what(), 2);
        var patternOutput = new GenericStack(output.what(), 1);
        var pattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(patternOutput)
                .addPreciseInput(1, input)
                .build();
        var plan = new LedgerCraftingPlan(output, 64, false, false, usedItems, new KeyCounter(), new KeyCounter(),
                Map.of(pattern, 2L),
                List.of(new PatternCraftingTask(pattern, 1), new PatternCraftingTask(pattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var provider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(pattern)).thenReturn(List.of(provider));
        when(provider.pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(logic.executeCrafting(2, craftingService, energy, mock(Level.class))).isEqualTo(2);

        verify(craftingService, times(1)).getProviders(pattern);
        verify(provider, times(2)).pushPattern(eq(pattern), org.mockito.ArgumentMatchers.any());
        assertThat(logic.getPendingOutputs(output.what())).isEqualTo(0);
        assertThat(logic.getLedgerExecutionStats().providerLookups()).isEqualTo(1);
        assertThat(logic.getLedgerExecutionStats().patternPushes()).isEqualTo(2);
    }

    @Test
    void skipsInputExtractionForLedgerTaskWhenProvidersAreBusy() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var busyInput = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var readyInput = new GenericStack(AEItemKey.of(Items.DIRT), 1);
        var busyOutput = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var readyOutput = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(busyInput.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        when(storage.extract(eq(readyInput.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        var usedItems = new KeyCounter();
        usedItems.add(busyInput.what(), 1);
        usedItems.add(readyInput.what(), 1);
        var busyInputQueries = new AtomicLong();
        var busyPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(busyOutput)
                .addCountingPreciseInput(1, busyInputQueries, busyInput)
                .build();
        var readyPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(readyOutput)
                .addPreciseInput(1, readyInput)
                .build();
        var plan = new LedgerCraftingPlan(readyOutput, 64, false, false, usedItems, new KeyCounter(),
                new KeyCounter(), Map.of(busyPattern, 1L, readyPattern, 1L),
                List.of(new PatternCraftingTask(busyPattern, 1), new PatternCraftingTask(readyPattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var busyProvider = mock(ICraftingProvider.class);
        var readyProvider = mock(ICraftingProvider.class);
        when(busyProvider.isBusy()).thenReturn(true);
        when(craftingService.getProviders(busyPattern)).thenReturn(List.of(busyProvider));
        when(craftingService.getProviders(readyPattern)).thenReturn(List.of(readyProvider));
        when(readyProvider.pushPattern(eq(readyPattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(1);

        assertThat(busyInputQueries).hasValue(0);
        assertThat(logic.getWaitingFor(readyOutput.what())).isEqualTo(1);
        verify(busyProvider).isBusy();
        org.mockito.Mockito.verify(busyProvider, org.mockito.Mockito.never())
                .pushPattern(eq(busyPattern), org.mockito.ArgumentMatchers.any());
        verify(readyProvider).pushPattern(eq(readyPattern), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void exposesLedgerExecutionStatsForProviderScheduling() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var busyInput = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var readyInput = new GenericStack(AEItemKey.of(Items.DIRT), 1);
        var busyOutput = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var readyOutput = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(busyInput.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        when(storage.extract(eq(readyInput.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        var usedItems = new KeyCounter();
        usedItems.add(busyInput.what(), 1);
        usedItems.add(readyInput.what(), 1);
        var busyPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(busyOutput)
                .addPreciseInput(1, busyInput)
                .build();
        var readyPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(readyOutput)
                .addPreciseInput(1, readyInput)
                .build();
        var plan = new LedgerCraftingPlan(readyOutput, 64, false, false, usedItems, new KeyCounter(),
                new KeyCounter(), Map.of(busyPattern, 1L, readyPattern, 1L),
                List.of(new PatternCraftingTask(busyPattern, 1), new PatternCraftingTask(readyPattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var busyProvider = mock(ICraftingProvider.class);
        var readyProvider = mock(ICraftingProvider.class);
        when(busyProvider.isBusy()).thenReturn(true);
        when(craftingService.getProviders(busyPattern)).thenReturn(List.of(busyProvider));
        when(craftingService.getProviders(readyPattern)).thenReturn(List.of(readyProvider));
        when(readyProvider.pushPattern(eq(readyPattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(1);

        var stats = logic.getLedgerExecutionStats();
        assertThat(stats.providerLookups()).isEqualTo(2);
        assertThat(stats.providerBusyChecks()).isEqualTo(2);
        assertThat(stats.busyProviderSkips()).isEqualTo(1);
        assertThat(stats.inputExtractions()).isEqualTo(1);
        assertThat(stats.patternPushes()).isEqualTo(1);
        assertThat(stats.completedTasks()).isEqualTo(1);
    }

    @Test
    void skipsLedgerTaskWhenInputsAreNotReadyAndContinuesToReadyTask() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var missingInput = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var readyInput = new GenericStack(AEItemKey.of(Items.DIRT), 1);
        var blockedOutput = new GenericStack(AEItemKey.of(Items.EMERALD), 1);
        var readyOutput = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(readyInput.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        var usedItems = new KeyCounter();
        usedItems.add(readyInput.what(), 1);
        var blockedPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(blockedOutput)
                .addPreciseInput(1, missingInput)
                .build();
        var readyPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(readyOutput)
                .addPreciseInput(1, readyInput)
                .build();
        var plan = new LedgerCraftingPlan(readyOutput, 64, false, false, usedItems, new KeyCounter(),
                new KeyCounter(), Map.of(blockedPattern, 1L, readyPattern, 1L),
                List.of(new PatternCraftingTask(blockedPattern, 1), new PatternCraftingTask(readyPattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var blockedProvider = mock(ICraftingProvider.class);
        var readyProvider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(blockedPattern)).thenReturn(List.of(blockedProvider));
        when(craftingService.getProviders(readyPattern)).thenReturn(List.of(readyProvider));
        when(readyProvider.pushPattern(eq(readyPattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(1);

        var stats = logic.getLedgerExecutionStats();
        assertThat(stats.inputUnavailableSkips()).isEqualTo(1);
        assertThat(stats.inputExtractions()).isEqualTo(1);
        assertThat(logic.getWaitingFor(readyOutput.what())).isEqualTo(1);
        org.mockito.Mockito.verify(blockedProvider, org.mockito.Mockito.never())
                .pushPattern(eq(blockedPattern), org.mockito.ArgumentMatchers.any());
        verify(readyProvider).pushPattern(eq(readyPattern), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotRecheckBlockedLedgerTaskInputsUntilInventoryChanges() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var missingInput = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var readyInput = new GenericStack(AEItemKey.of(Items.DIRT), 1);
        var blockedOutput = new GenericStack(AEItemKey.of(Items.EMERALD), 1);
        var readyOutput = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(readyInput.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        var usedItems = new KeyCounter();
        usedItems.add(readyInput.what(), 1);
        var blockedInputQueries = new AtomicLong();
        var blockedPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(blockedOutput)
                .addCountingPreciseInput(1, blockedInputQueries, missingInput)
                .build();
        var readyPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(readyOutput)
                .addPreciseInput(1, readyInput)
                .build();
        var plan = new LedgerCraftingPlan(readyOutput, 64, false, false, usedItems, new KeyCounter(),
                new KeyCounter(), Map.of(blockedPattern, 1L, readyPattern, 1L),
                List.of(new PatternCraftingTask(blockedPattern, 1), new PatternCraftingTask(readyPattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var blockedProvider = mock(ICraftingProvider.class);
        var readyProvider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(blockedPattern)).thenReturn(List.of(blockedProvider));
        when(craftingService.getProviders(readyPattern)).thenReturn(List.of(readyProvider));
        when(readyProvider.pushPattern(eq(readyPattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(1);
        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(0);

        assertThat(blockedInputQueries).hasValue(1);
        assertThat(logic.getLedgerExecutionStats().inputUnavailableSkips()).isEqualTo(1);
        assertThat(logic.getLedgerExecutionStats().inputBlockedSkips()).isEqualTo(1);
    }

    @Test
    void doesNotRecheckBlockedLedgerTaskProvidersUntilInventoryChanges() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var missingInput = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var readyInput = new GenericStack(AEItemKey.of(Items.DIRT), 1);
        var blockedOutput = new GenericStack(AEItemKey.of(Items.EMERALD), 1);
        var readyOutput = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        when(storage.extract(eq(readyInput.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        var usedItems = new KeyCounter();
        usedItems.add(readyInput.what(), 1);
        var blockedPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(blockedOutput)
                .addPreciseInput(1, missingInput)
                .build();
        var readyPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(readyOutput)
                .addPreciseInput(1, readyInput)
                .build();
        var plan = new LedgerCraftingPlan(readyOutput, 64, false, false, usedItems, new KeyCounter(),
                new KeyCounter(), Map.of(blockedPattern, 1L, readyPattern, 1L),
                List.of(new PatternCraftingTask(blockedPattern, 1), new PatternCraftingTask(readyPattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var blockedProvider = mock(ICraftingProvider.class);
        var readyProvider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(blockedPattern)).thenReturn(List.of(blockedProvider));
        when(craftingService.getProviders(readyPattern)).thenReturn(List.of(readyProvider));
        when(readyProvider.pushPattern(eq(readyPattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(1);
        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(0);

        verify(blockedProvider, times(1)).isBusy();
        assertThat(logic.getLedgerExecutionStats().providerLookups()).isEqualTo(2);
        assertThat(logic.getLedgerExecutionStats().inputBlockedSkips()).isEqualTo(1);
    }

    @Test
    void shortCircuitsLedgerExecutionWhenEveryRemainingTaskWaitsForInputs() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(mock(MEStorage.class));
        var firstInput = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var secondInput = new GenericStack(AEItemKey.of(Items.DIRT), 1);
        var firstOutput = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var secondOutput = new GenericStack(AEItemKey.of(Items.EMERALD), 1);
        var firstInputQueries = new AtomicLong();
        var secondInputQueries = new AtomicLong();
        var firstPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(firstOutput)
                .addCountingPreciseInput(1, firstInputQueries, firstInput)
                .build();
        var secondPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(secondOutput)
                .addCountingPreciseInput(1, secondInputQueries, secondInput)
                .build();
        var plan = new LedgerCraftingPlan(secondOutput, 64, false, false, new KeyCounter(), new KeyCounter(),
                new KeyCounter(), Map.of(firstPattern, 1L, secondPattern, 1L),
                List.of(new PatternCraftingTask(firstPattern, 1), new PatternCraftingTask(secondPattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, new BaseActionSource(), null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var firstProvider = mock(ICraftingProvider.class);
        var secondProvider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(firstPattern)).thenReturn(List.of(firstProvider));
        when(craftingService.getProviders(secondPattern)).thenReturn(List.of(secondProvider));
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(0);
        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(0);

        assertThat(firstInputQueries).hasValue(1);
        assertThat(secondInputQueries).hasValue(1);
        verify(firstProvider, times(1)).isBusy();
        verify(secondProvider, times(1)).isBusy();
        assertThat(logic.getLedgerExecutionStats().inputUnavailableSkips()).isEqualTo(2);
        assertThat(logic.getLedgerExecutionStats().inputBlockedSkips()).isEqualTo(0);
        assertThat(logic.getLedgerExecutionStats().inputBlockedShortCircuits()).isEqualTo(1);
    }

    @Test
    void rechecksBlockedLedgerTaskInputsAfterInventoryChanges() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var sourceInput = new GenericStack(AEItemKey.of(Items.DIRT), 1);
        var intermediate = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var finalOutput = new GenericStack(AEItemKey.of(Items.EMERALD), 1);
        when(storage.extract(eq(sourceInput.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        var usedItems = new KeyCounter();
        usedItems.add(sourceInput.what(), 1);
        var blockedInputQueries = new AtomicLong();
        var blockedPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(finalOutput)
                .addCountingPreciseInput(1, blockedInputQueries, intermediate)
                .build();
        var readyPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(intermediate)
                .addPreciseInput(1, sourceInput)
                .build();
        var plan = new LedgerCraftingPlan(finalOutput, 64, false, false, usedItems, new KeyCounter(),
                new KeyCounter(), Map.of(blockedPattern, 1L, readyPattern, 1L),
                List.of(new PatternCraftingTask(blockedPattern, 1), new PatternCraftingTask(readyPattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var blockedProvider = mock(ICraftingProvider.class);
        var readyProvider = mock(ICraftingProvider.class);
        when(craftingService.getProviders(blockedPattern)).thenReturn(List.of(blockedProvider));
        when(craftingService.getProviders(readyPattern)).thenReturn(List.of(readyProvider));
        when(readyProvider.pushPattern(eq(readyPattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(blockedProvider.pushPattern(eq(blockedPattern), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(1);
        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(0);
        assertThat(logic.insert(intermediate.what(), 1, Actionable.MODULATE)).isEqualTo(1);
        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(1);

        assertThat(blockedInputQueries).hasValue(2);
        assertThat(logic.getLedgerExecutionStats().inputUnavailableSkips()).isEqualTo(1);
        assertThat(logic.getLedgerExecutionStats().inputBlockedSkips()).isEqualTo(1);
        assertThat(logic.getLedgerExecutionStats().inputExtractions()).isEqualTo(2);
        verify(readyProvider).pushPattern(eq(readyPattern), org.mockito.ArgumentMatchers.any());
        verify(blockedProvider).pushPattern(eq(blockedPattern), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ledgerExecutionContinuesAfterLastScannedTaskWhenEarlierProviderIsBusy() {
        var cluster = mock(CraftingCPUCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getAvailableStorage()).thenReturn(1024L);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        var storage = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
        var src = new BaseActionSource();
        var busyInput = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var firstReadyInput = new GenericStack(AEItemKey.of(Items.DIRT), 1);
        var secondReadyInput = new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 1);
        var busyOutput = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var firstReadyOutput = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var secondReadyOutput = new GenericStack(AEItemKey.of(Items.EMERALD), 1);
        when(storage.extract(eq(busyInput.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        when(storage.extract(eq(firstReadyInput.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        when(storage.extract(eq(secondReadyInput.what()), eq(1L), eq(Actionable.MODULATE), eq(src))).thenReturn(1L);
        var usedItems = new KeyCounter();
        usedItems.add(busyInput.what(), 1);
        usedItems.add(firstReadyInput.what(), 1);
        usedItems.add(secondReadyInput.what(), 1);
        var busyPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(busyOutput)
                .addPreciseInput(1, busyInput)
                .build();
        var firstReadyPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(firstReadyOutput)
                .addPreciseInput(1, firstReadyInput)
                .build();
        var secondReadyPattern = new appeng.crafting.simulation.helpers.ProcessingPatternBuilder(secondReadyOutput)
                .addPreciseInput(1, secondReadyInput)
                .build();
        var plan = new LedgerCraftingPlan(secondReadyOutput, 64, false, false, usedItems, new KeyCounter(),
                new KeyCounter(), Map.of(busyPattern, 1L, firstReadyPattern, 1L, secondReadyPattern, 1L),
                List.of(new PatternCraftingTask(busyPattern, 1), new PatternCraftingTask(firstReadyPattern, 1),
                        new PatternCraftingTask(secondReadyPattern, 1)));
        var logic = new CraftingCpuLogic(cluster);
        assertThat(logic.trySubmitJob(grid, plan, src, null).successful()).isTrue();
        var craftingService = mock(CraftingService.class);
        var busyProvider = mock(ICraftingProvider.class);
        var firstReadyProvider = mock(ICraftingProvider.class);
        var secondReadyProvider = mock(ICraftingProvider.class);
        when(busyProvider.isBusy()).thenReturn(true);
        when(craftingService.getProviders(busyPattern)).thenReturn(List.of(busyProvider));
        when(craftingService.getProviders(firstReadyPattern)).thenReturn(List.of(firstReadyProvider));
        when(craftingService.getProviders(secondReadyPattern)).thenReturn(List.of(secondReadyProvider));
        when(firstReadyProvider.pushPattern(eq(firstReadyPattern), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        when(secondReadyProvider.pushPattern(eq(secondReadyPattern), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(1);
        assertThat(logic.executeCrafting(1, craftingService, energy, mock(Level.class))).isEqualTo(1);

        verify(busyProvider, times(1)).isBusy();
        verify(firstReadyProvider).pushPattern(eq(firstReadyPattern), org.mockito.ArgumentMatchers.any());
        verify(secondReadyProvider).pushPattern(eq(secondReadyPattern), org.mockito.ArgumentMatchers.any());
        assertThat(logic.getWaitingFor(firstReadyOutput.what())).isEqualTo(1);
        assertThat(logic.getWaitingFor(secondReadyOutput.what())).isEqualTo(1);
    }

    private static final class RecordingBulkProvider implements ICraftingProvider, IBulkCraftingProvider {
        int pushes;
        int bulkPushes;
        boolean bulkResult = true;
        KeyCounter[] bulkInputHolder;

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            pushes++;
            return true;
        }

        @Override
        public boolean pushPatternBatchToExternalInventory(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            bulkPushes++;
            bulkInputHolder = inputHolder;
            return bulkResult;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private static final class RecordingProvider implements ICraftingProvider {
        int pushes;
        KeyCounter[] lastInputHolder;

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            pushes++;
            lastInputHolder = inputHolder;
            return true;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private static final class RecordingGtlPatternPartProvider
            implements org.gtlcore.gtlcore.api.machine.trait.IMEPatternPartMachine {
        int pushes;
        KeyCounter[] lastInputHolder;

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            pushes++;
            lastInputHolder = inputHolder;
            return true;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }
}
