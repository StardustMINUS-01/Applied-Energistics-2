package appeng.crafting.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.simulation.helpers.ProcessingPatternBuilder;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class LedgerCraftingPlannerTest {
    @Test
    void plansExtractionOnlyRequestFromNetworkInventory() {
        var stone = AEItemKey.of(Items.STONE);
        var inventory = new KeyCounter();
        inventory.add(stone, 10);
        var planner = new LedgerCraftingPlanner(inventory);

        var plan = planner.plan(new GenericStack(stone, 6), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan).isInstanceOf(LedgerCraftingPlan.class);
        assertThat(plan.simulation()).isFalse();
        assertThat(plan.finalOutput()).isEqualTo(new GenericStack(stone, 6));
        assertThat(plan.usedItems().get(stone)).isEqualTo(6);
        assertThat(plan.missingItems()).isEmpty();
        assertThat(plan.emittedItems()).isEmpty();
        assertThat(plan.patternTimes()).isEmpty();
        assertThat(((LedgerCraftingPlan) plan).tasks()).isEmpty();
    }

    @Test
    void accountsBytesForExtractionOnlyRequest() {
        var stone = AEItemKey.of(Items.STONE);
        var inventory = new KeyCounter();
        inventory.add(stone, 10);
        var planner = new LedgerCraftingPlanner(inventory);

        var plan = planner.plan(new GenericStack(stone, 6), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.bytes()).isEqualTo(14);
    }

    @Test
    void accountsBytesForContainerItemsReturnedByPatternInputs() {
        var waterBucket = new GenericStack(AEItemKey.of(Items.WATER_BUCKET), 1);
        var output = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var inventory = new KeyCounter();
        inventory.add(waterBucket.what(), 4);
        var pattern = new ProcessingPatternBuilder(output)
                .addPreciseInput(1, true, waterBucket)
                .build();
        var planner = new LedgerCraftingPlanner(inventory, Map.of(output.what(), List.of(pattern)));

        var plan = planner.plan(new GenericStack(output.what(), 4), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.bytes()).isEqualTo(32);
    }

    @Test
    void reportsMissingRemainderWhenExtractionCannotSatisfyFullRequest() {
        var stone = AEItemKey.of(Items.STONE);
        var inventory = new KeyCounter();
        inventory.add(stone, 4);
        var planner = new LedgerCraftingPlanner(inventory);

        var plan = planner.plan(new GenericStack(stone, 6), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.simulation()).isTrue();
        assertThat(plan.finalOutput()).isEqualTo(new GenericStack(stone, 6));
        assertThat(plan.usedItems().get(stone)).isEqualTo(4);
        assertThat(plan.missingItems().get(stone)).isEqualTo(2);
    }

    @Test
    void plansSinglePatternRequestInOneBatch() {
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var inventory = new KeyCounter();
        inventory.add(input.what(), 64);
        var pattern = new ProcessingPatternBuilder(output).addPreciseInput(1, input).build();
        var planner = new LedgerCraftingPlanner(inventory, Map.of(output.what(), List.of(pattern)));

        var plan = planner.plan(new GenericStack(output.what(), 64), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.simulation()).isFalse();
        assertThat(plan.usedItems().get(input.what())).isEqualTo(64);
        assertThat(plan.patternTimes()).containsEntry(pattern, 64L);
        assertThat(((LedgerCraftingPlan) plan).tasks()).hasSize(1);
    }

    @Test
    void exposesPlanningStatsForLargeBatchedPatternRequest() {
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var inventory = new KeyCounter();
        inventory.add(input.what(), 2048);
        var pattern = new ProcessingPatternBuilder(output).addPreciseInput(1, input).build();
        var planner = new LedgerCraftingPlanner(inventory, Map.of(output.what(), List.of(pattern)));

        var plan = (LedgerCraftingPlan) planner.plan(
                new GenericStack(output.what(), 2048),
                CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.planningStats().requestedAmount()).isEqualTo(2048);
        assertThat(plan.planningStats().requestResolutions()).isEqualTo(2);
        assertThat(plan.planningStats().uniqueRequestKeys()).isEqualTo(2);
        assertThat(plan.planningStats().patternAttempts()).isEqualTo(1);
        assertThat(plan.planningStats().plannedTasks()).isEqualTo(1);
    }

    @Test
    void plansGregtechScaleFiftyMillionItemOrderByGraphShape() {
        var amount = 50_000_000L;
        var rawMetal = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1);
        var rawWireMetal = new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 1);
        var plate = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var wire = new GenericStack(AEItemKey.of(Items.REDSTONE), 2);
        var circuit = new GenericStack(AEItemKey.of(Items.EMERALD), 1);
        var frame = new GenericStack(AEItemKey.of(Items.GOLD_BLOCK), 1);
        var machine = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);

        var platePattern = new ProcessingPatternBuilder(plate)
                .addPreciseInput(1, rawMetal)
                .build();
        var wirePattern = new ProcessingPatternBuilder(wire)
                .addPreciseInput(1, rawWireMetal)
                .build();
        var circuitPattern = new ProcessingPatternBuilder(circuit)
                .addPreciseInput(4, new GenericStack(wire.what(), 1))
                .addPreciseInput(1, plate)
                .build();
        var framePattern = new ProcessingPatternBuilder(frame)
                .addPreciseInput(8, rawMetal)
                .build();
        var machinePattern = new ProcessingPatternBuilder(machine)
                .addPreciseInput(4, plate)
                .addPreciseInput(2, circuit)
                .addPreciseInput(1, frame)
                .build();
        var inventory = new KeyCounter();
        inventory.add(rawMetal.what(), amount * 14);
        inventory.add(rawWireMetal.what(), amount * 4);
        var planner = new LedgerCraftingPlanner(inventory, Map.of(
                plate.what(), List.of(platePattern),
                wire.what(), List.of(wirePattern),
                circuit.what(), List.of(circuitPattern),
                frame.what(), List.of(framePattern),
                machine.what(), List.of(machinePattern)));

        var started = System.nanoTime();
        var plan = (LedgerCraftingPlan) planner.plan(
                new GenericStack(machine.what(), amount),
                CalculationStrategy.REPORT_MISSING_ITEMS);
        var elapsedNanos = System.nanoTime() - started;
        System.out.printf(
                "fiftyMillionPlanningMillis=%d, patternAttempts=%d, plannedTasks=%d%n",
                TimeUnit.NANOSECONDS.toMillis(elapsedNanos),
                plan.planningStats().patternAttempts(),
                plan.planningStats().plannedTasks());

        assertThat(plan.simulation()).isFalse();
        assertThat(plan.finalOutput()).isEqualTo(new GenericStack(machine.what(), amount));
        assertThat(plan.missingItems()).isEmpty();
        assertThat(plan.usedItems().get(rawMetal.what())).isEqualTo(amount * 14);
        assertThat(plan.usedItems().get(rawWireMetal.what())).isEqualTo(amount * 4);
        assertThat(plan.patternTimes())
                .containsEntry(machinePattern, amount)
                .containsEntry(circuitPattern, amount * 2)
                .containsEntry(platePattern, amount * 6)
                .containsEntry(framePattern, amount)
                .containsEntry(wirePattern, amount * 4);
        assertThat(plan.planningStats().requestedAmount()).isEqualTo(amount);
        assertThat(plan.planningStats().patternAttempts()).isEqualTo(6);
        assertThat(plan.planningStats().plannedTasks()).isEqualTo(6);
        assertThat(((LedgerCraftingPlan) plan).tasks()).hasSize(6);
    }

    @Test
    void comparesInsufficientGregtechScaleCraftLessWithBinaryRetryStrategy() {
        var requestedAmount = 50_000_000L;
        var craftableAmount = 25_000_000L;
        var rawPlateMetal = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1);
        var rawWireMetal = new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 1);
        var rawFrameMetal = new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1);
        var plate = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var wire = new GenericStack(AEItemKey.of(Items.REDSTONE), 2);
        var circuit = new GenericStack(AEItemKey.of(Items.EMERALD), 1);
        var frame = new GenericStack(AEItemKey.of(Items.GOLD_BLOCK), 1);
        var machine = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);

        var platePattern = new ProcessingPatternBuilder(plate)
                .addPreciseInput(1, rawPlateMetal)
                .build();
        var wirePattern = new ProcessingPatternBuilder(wire)
                .addPreciseInput(1, rawWireMetal)
                .build();
        var circuitPattern = new ProcessingPatternBuilder(circuit)
                .addPreciseInput(4, new GenericStack(wire.what(), 1))
                .addPreciseInput(1, plate)
                .build();
        var framePattern = new ProcessingPatternBuilder(frame)
                .addPreciseInput(8, rawFrameMetal)
                .build();
        var machinePattern = new ProcessingPatternBuilder(machine)
                .addPreciseInput(2, circuit)
                .addPreciseInput(1, frame)
                .build();
        var inventory = new KeyCounter();
        inventory.add(rawPlateMetal.what(), craftableAmount * 2);
        inventory.add(rawWireMetal.what(), craftableAmount * 4);
        inventory.add(rawFrameMetal.what(), craftableAmount * 8);
        var planner = new LedgerCraftingPlanner(inventory, Map.of(
                plate.what(), List.of(platePattern),
                wire.what(), List.of(wirePattern),
                circuit.what(), List.of(circuitPattern),
                frame.what(), List.of(framePattern),
                machine.what(), List.of(machinePattern)));

        var ledgerStarted = System.nanoTime();
        var ledgerPlan = (LedgerCraftingPlan) planner.plan(
                new GenericStack(machine.what(), requestedAmount),
                CalculationStrategy.CRAFT_LESS);
        var ledgerElapsedNanos = System.nanoTime() - ledgerStarted;

        var binaryResult = simulateLegacyBinaryCraftLess(
                planner, machine.what(), requestedAmount);

        System.out.printf(
                "insufficientFiftyMillionLedgerMillis=%d, ledgerPatternAttempts=%d, ledgerPlannedTasks=%d, "
                        + "binaryRetryMillis=%d, binaryPlanRuns=%d, binaryPatternAttempts=%d%n",
                TimeUnit.NANOSECONDS.toMillis(ledgerElapsedNanos),
                ledgerPlan.planningStats().patternAttempts(),
                ledgerPlan.planningStats().plannedTasks(),
                TimeUnit.NANOSECONDS.toMillis(binaryResult.elapsedNanos),
                binaryResult.planRuns,
                binaryResult.patternAttempts);

        assertThat(ledgerPlan.simulation()).isFalse();
        assertThat(ledgerPlan.finalOutput()).isEqualTo(new GenericStack(machine.what(), craftableAmount));
        assertThat(ledgerPlan.missingItems()).isEmpty();
        assertThat(ledgerPlan.planningStats().patternAttempts()).isEqualTo(5);
        assertThat(ledgerPlan.planningStats().plannedTasks()).isEqualTo(5);

        assertThat(binaryResult.finalAmount).isEqualTo(craftableAmount);
        assertThat(binaryResult.planRuns).isGreaterThan(20);
        assertThat(binaryResult.patternAttempts).isGreaterThan(ledgerPlan.planningStats().patternAttempts());
    }

    private static BinaryCraftLessResult simulateLegacyBinaryCraftLess(LedgerCraftingPlanner planner,
            AEKey output, long requestedAmount) {
        var started = System.nanoTime();
        long planRuns = 0;
        long patternAttempts = 0;

        var fullPlan = (LedgerCraftingPlan) planner.plan(
                new GenericStack(output, requestedAmount),
                CalculationStrategy.REPORT_MISSING_ITEMS);
        planRuns++;
        patternAttempts += fullPlan.planningStats().patternAttempts();
        if (!fullPlan.simulation()) {
            return new BinaryCraftLessResult(requestedAmount, planRuns, patternAttempts, System.nanoTime() - started);
        }

        var low = 0L;
        var high = requestedAmount;
        while (low < high) {
            var mid = low + (high - low + 1) / 2;
            var plan = (LedgerCraftingPlan) planner.plan(
                    new GenericStack(output, mid),
                    CalculationStrategy.REPORT_MISSING_ITEMS);
            planRuns++;
            patternAttempts += plan.planningStats().patternAttempts();
            if (plan.simulation()) {
                high = mid - 1;
            } else {
                low = mid;
            }
        }

        return new BinaryCraftLessResult(low, planRuns, patternAttempts, System.nanoTime() - started);
    }

    private record BinaryCraftLessResult(long finalAmount, long planRuns, long patternAttempts, long elapsedNanos) {
    }

    @Test
    void refundsUnresolvedOverRequestWhenMultiplePatternsShareOutput() {
        var input1 = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var input2 = new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var inventory = new KeyCounter();
        inventory.add(input1.what(), 16);
        inventory.add(input2.what(), 16);
        var pattern1 = new ProcessingPatternBuilder(output).addPreciseInput(1, input1).build();
        var pattern2 = new ProcessingPatternBuilder(output).addPreciseInput(1, input2).build();
        var planner = new LedgerCraftingPlanner(inventory, Map.of(output.what(), List.of(pattern1, pattern2)));

        var plan = planner.plan(new GenericStack(output.what(), 32), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.simulation()).isFalse();
        assertThat(plan.missingItems()).isEmpty();
        assertThat(plan.usedItems().get(input1.what())).isEqualTo(16);
        assertThat(plan.usedItems().get(input2.what())).isEqualTo(16);
        assertThat(plan.patternTimes()).containsEntry(pattern1, 16L).containsEntry(pattern2, 16L);
    }

    @Test
    void plansEmitableRequestWithoutNetworkExtractionOrPatterns() {
        var output = new GenericStack(AEItemKey.of(Items.STONE), 32);
        var planner = new LedgerCraftingPlanner(new KeyCounter(), Map.of(), Set.of(output.what()));

        var plan = planner.plan(output, CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.simulation()).isFalse();
        assertThat(plan.finalOutput()).isEqualTo(output);
        assertThat(plan.usedItems()).isEmpty();
        assertThat(plan.emittedItems().get(output.what())).isEqualTo(32);
        assertThat(plan.missingItems()).isEmpty();
        assertThat(plan.patternTimes()).isEmpty();
    }

    @Test
    void craftLessReturnsLargestLedgerSatisfiedAmountWithoutMissingRemainder() {
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var inventory = new KeyCounter();
        inventory.add(input.what(), 4);
        var pattern = new ProcessingPatternBuilder(output).addPreciseInput(1, input).build();
        var planner = new LedgerCraftingPlanner(inventory, Map.of(output.what(), List.of(pattern)));

        var plan = planner.plan(new GenericStack(output.what(), 6), CalculationStrategy.CRAFT_LESS);

        assertThat(plan.simulation()).isFalse();
        assertThat(plan.finalOutput()).isEqualTo(new GenericStack(output.what(), 4));
        assertThat(plan.usedItems().get(input.what())).isEqualTo(4);
        assertThat(plan.missingItems()).isEmpty();
        assertThat(plan.patternTimes()).containsEntry(pattern, 4L);
    }

    @Test
    void reportMissingItemsReportsLimitingPatternInputRemainder() {
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var inventory = new KeyCounter();
        inventory.add(input.what(), 4);
        var pattern = new ProcessingPatternBuilder(output).addPreciseInput(1, input).build();
        var planner = new LedgerCraftingPlanner(inventory, Map.of(output.what(), List.of(pattern)));

        var plan = planner.plan(new GenericStack(output.what(), 6), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.simulation()).isTrue();
        assertThat(plan.finalOutput()).isEqualTo(new GenericStack(output.what(), 6));
        assertThat(plan.usedItems().get(input.what())).isEqualTo(4);
        assertThat(plan.patternTimes()).containsEntry(pattern, 6L);
        assertThat(plan.missingItems().get(input.what())).isEqualTo(2);
    }

    @Test
    void usesStoredAlternativePatternInputWhenPrimaryInputIsMissing() {
        var primaryInput = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var alternativeInput = new GenericStack(AEItemKey.of(Items.DEEPSLATE), 1);
        var output = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var inventory = new KeyCounter();
        inventory.add(alternativeInput.what(), 8);
        var pattern = new ProcessingPatternBuilder(output)
                .addPreciseInput(1, primaryInput, alternativeInput)
                .build();
        var planner = new LedgerCraftingPlanner(inventory, Map.of(output.what(), List.of(pattern)));

        var plan = planner.plan(new GenericStack(output.what(), 8), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.simulation()).isFalse();
        assertThat(plan.usedItems().get(primaryInput.what())).isEqualTo(0);
        assertThat(plan.usedItems().get(alternativeInput.what())).isEqualTo(8);
        assertThat(plan.missingItems()).isEmpty();
        assertThat(plan.patternTimes()).containsEntry(pattern, 8L);
    }

    @Test
    void usesFuzzyCraftablePatternForPatternInput() {
        var childInput = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var damagedPickaxeStack = new ItemStack(Items.DIAMOND_PICKAXE);
        damagedPickaxeStack.setDamageValue(10);
        var damagedPickaxe = GenericStack.fromItemStack(damagedPickaxeStack);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var inventory = new KeyCounter();
        inventory.add(childInput.what(), 1);
        var childPattern = new ProcessingPatternBuilder(damagedPickaxe)
                .addPreciseInput(1, childInput)
                .build();
        var rootPattern = new ProcessingPatternBuilder(output)
                .addDamageableInput(Items.DIAMOND_PICKAXE)
                .build();
        var planner = new LedgerCraftingPlanner(inventory, Map.of(
                damagedPickaxe.what(), List.of(childPattern),
                output.what(), List.of(rootPattern)));

        var plan = planner.plan(new GenericStack(output.what(), 1), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.simulation()).isFalse();
        assertThat(plan.usedItems().get(childInput.what())).isEqualTo(1);
        assertThat(plan.missingItems()).isEmpty();
        assertThat(plan.patternTimes()).containsEntry(childPattern, 1L).containsEntry(rootPattern, 1L);
    }

    @Test
    void usesRemainingItemsFromChildPatternForSiblingInputs() {
        var waterBucket = new GenericStack(AEItemKey.of(Items.WATER_BUCKET), 1);
        var bucket = new GenericStack(AEItemKey.of(Items.BUCKET), 1);
        var intermediate = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var inventory = new KeyCounter();
        inventory.add(waterBucket.what(), 4);
        var childPattern = new ProcessingPatternBuilder(intermediate)
                .addPreciseInput(1, true, waterBucket)
                .build();
        var rootPattern = new ProcessingPatternBuilder(output)
                .addPreciseInput(1, intermediate)
                .addPreciseInput(1, bucket)
                .build();
        var planner = new LedgerCraftingPlanner(inventory, Map.of(
                intermediate.what(), List.of(childPattern),
                output.what(), List.of(rootPattern)));

        var plan = planner.plan(new GenericStack(output.what(), 4), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.simulation()).isFalse();
        assertThat(plan.usedItems().get(waterBucket.what())).isEqualTo(4);
        assertThat(plan.usedItems().get(bucket.what())).isEqualTo(0);
        assertThat(plan.missingItems()).isEmpty();
        assertThat(plan.patternTimes()).containsEntry(childPattern, 4L).containsEntry(rootPattern, 4L);
    }

    @Test
    void reportsMissingWhenPatternWouldRecursivelyRequireItsOwnOutput() {
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var recursivePattern = new ProcessingPatternBuilder(output)
                .addPreciseInput(1, output)
                .build();
        var planner = new LedgerCraftingPlanner(new KeyCounter(), Map.of(output.what(), List.of(recursivePattern)));

        var plan = planner.plan(new GenericStack(output.what(), 1), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.simulation()).isTrue();
        assertThat(plan.patternTimes()).containsEntry(recursivePattern, 1L);
        assertThat(plan.missingItems().get(output.what())).isEqualTo(1);
    }

    @Test
    void usesByproductsFromChildPatternForSiblingInputs() {
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var intermediate = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var byproduct = new GenericStack(AEItemKey.of(Items.EMERALD), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var inventory = new KeyCounter();
        inventory.add(input.what(), 4);
        var childPattern = new ProcessingPatternBuilder(intermediate, byproduct)
                .addPreciseInput(1, input)
                .build();
        var rootPattern = new ProcessingPatternBuilder(output)
                .addPreciseInput(1, intermediate)
                .addPreciseInput(1, byproduct)
                .build();
        var planner = new LedgerCraftingPlanner(inventory, Map.of(
                intermediate.what(), List.of(childPattern),
                output.what(), List.of(rootPattern)));

        var plan = planner.plan(new GenericStack(output.what(), 4), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.simulation()).isFalse();
        assertThat(plan.usedItems().get(input.what())).isEqualTo(4);
        assertThat(plan.usedItems().get(byproduct.what())).isEqualTo(0);
        assertThat(plan.missingItems()).isEmpty();
        assertThat(plan.patternTimes()).containsEntry(childPattern, 4L).containsEntry(rootPattern, 4L);
    }

    @Test
    void reusesExcessPrimaryOutputFromChildPatternForSiblingInputs() {
        var input = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var intermediate = new GenericStack(AEItemKey.of(Items.STONE), 2);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var inventory = new KeyCounter();
        inventory.add(input.what(), 1);
        var childPattern = new ProcessingPatternBuilder(intermediate)
                .addPreciseInput(1, input)
                .build();
        var rootPattern = new ProcessingPatternBuilder(output)
                .addPreciseInput(1, new GenericStack(intermediate.what(), 1))
                .addPreciseInput(1, new GenericStack(intermediate.what(), 1))
                .build();
        var planner = new LedgerCraftingPlanner(inventory, Map.of(
                intermediate.what(), List.of(childPattern),
                output.what(), List.of(rootPattern)));

        var plan = planner.plan(new GenericStack(output.what(), 1), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.simulation()).isFalse();
        assertThat(plan.usedItems().get(input.what())).isEqualTo(1);
        assertThat(plan.missingItems()).isEmpty();
        assertThat(plan.patternTimes()).containsEntry(childPattern, 1L).containsEntry(rootPattern, 1L);
    }

    @Test
    void refundsChildPatternWorkWhenSiblingInputLimitsParentPattern() {
        var childInput = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var limitedInput = new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1);
        var childOutput = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var inventory = new KeyCounter();
        inventory.add(childInput.what(), 10);
        inventory.add(limitedInput.what(), 4);
        var childPattern = new ProcessingPatternBuilder(childOutput)
                .addPreciseInput(1, childInput)
                .build();
        var rootPattern = new ProcessingPatternBuilder(output)
                .addPreciseInput(1, childOutput)
                .addPreciseInput(1, limitedInput)
                .build();
        var planner = new LedgerCraftingPlanner(inventory, Map.of(
                childOutput.what(), List.of(childPattern),
                output.what(), List.of(rootPattern)));

        var plan = planner.plan(new GenericStack(output.what(), 10), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.simulation()).isTrue();
        assertThat(plan.usedItems().get(childInput.what())).isEqualTo(4);
        assertThat(plan.usedItems().get(limitedInput.what())).isEqualTo(4);
        assertThat(plan.patternTimes()).containsEntry(childPattern, 10L).containsEntry(rootPattern, 10L);
        assertThat(plan.missingItems().get(limitedInput.what())).isEqualTo(6);
    }

    @Test
    void marksMultiplePathsWhenNestedInputHasMultiplePatterns() {
        var input1 = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1);
        var input2 = new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 1);
        var childOutput = new GenericStack(AEItemKey.of(Items.STONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var inventory = new KeyCounter();
        inventory.add(input1.what(), 1);
        var childPattern1 = new ProcessingPatternBuilder(childOutput).addPreciseInput(1, input1).build();
        var childPattern2 = new ProcessingPatternBuilder(childOutput).addPreciseInput(1, input2).build();
        var rootPattern = new ProcessingPatternBuilder(output).addPreciseInput(1, childOutput).build();
        var planner = new LedgerCraftingPlanner(inventory, Map.of(
                childOutput.what(), List.of(childPattern1, childPattern2),
                output.what(), List.of(rootPattern)));

        var plan = planner.plan(new GenericStack(output.what(), 1), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(plan.simulation()).isFalse();
        assertThat(plan.multiplePaths()).isTrue();
    }
}
