/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.crafting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.crafting.ledger.LedgerCraftingPlanner;

public class CraftingCalculation {
    private final IGrid grid;
    private final Level level;
    private final AEKey output;
    private final long requestedAmount;
    private final CalculationStrategy strategy;

    public CraftingCalculation(Level level, IGrid grid, ICraftingSimulationRequester simRequester,
            GenericStack output, CalculationStrategy strategy) {
        this.grid = grid;
        this.level = level;
        this.output = output.what();
        this.requestedAmount = output.amount();
        this.strategy = strategy;
    }

    public ICraftingPlan run() {
        try {
            return runLedgerPlan();
        } catch (Exception ex) {
            AELog.info(ex, "Exception during crafting calculation.");
            throw new RuntimeException(ex);
        }
    }

    private ICraftingPlan runLedgerPlan() {
        var initialInventory = new KeyCounter();
        for (var entry : grid.getStorageService().getCachedInventory()) {
            if (!entry.getKey().equals(output)) {
                initialInventory.add(entry.getKey(), entry.getLongValue());
            }
        }

        var craftingService = grid.getCraftingService();
        var patterns = new HashMap<AEKey, List<appeng.api.crafting.IPatternDetails>>();
        var emitableItems = new HashSet<AEKey>();
        if (craftingService.canEmitFor(output)) {
            emitableItems.add(output);
        }
        for (var craftable : craftingService.getCraftables(what -> true)) {
            if (craftingService.canEmitFor(craftable)) {
                emitableItems.add(craftable);
            }

            var availablePatterns = craftingService.getCraftingFor(craftable);
            if (!availablePatterns.isEmpty()) {
                patterns.put(craftable, List.copyOf(availablePatterns));
                for (var pattern : availablePatterns) {
                    for (var input : pattern.getInputs()) {
                        for (var possibleInput : input.getPossibleInputs()) {
                            if (craftingService.canEmitFor(possibleInput.what())) {
                                emitableItems.add(possibleInput.what());
                            }
                        }
                    }
                }
            }
        }

        var planner = new LedgerCraftingPlanner(initialInventory, patterns, emitableItems, level);
        return planner.plan(new GenericStack(output, requestedAmount), strategy);
    }

}
