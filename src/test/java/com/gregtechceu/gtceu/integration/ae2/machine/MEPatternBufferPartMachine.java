package com.gregtechceu.gtceu.integration.ae2.machine;

import java.util.List;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;

public class MEPatternBufferPartMachine implements ICraftingProvider {
    public int pushes;
    public KeyCounter[] lastInputHolder;

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
