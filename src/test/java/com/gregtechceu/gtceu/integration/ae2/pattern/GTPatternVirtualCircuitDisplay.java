package com.gregtechceu.gtceu.integration.ae2.pattern;

import java.util.OptionalInt;

import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class GTPatternVirtualCircuitDisplay {
    private GTPatternVirtualCircuitDisplay() {
    }

    public static ItemStack toDisplayStack(int circuit) {
        return IntCircuitBehaviour.stack(circuit);
    }

    public static Component tooltip(int circuit) {
        return Component.literal("Circuit " + circuit);
    }

    public static OptionalInt readDisplayCircuit(ItemStack encodedPattern) {
        return GTPatternVirtualCircuit.read(encodedPattern);
    }
}
