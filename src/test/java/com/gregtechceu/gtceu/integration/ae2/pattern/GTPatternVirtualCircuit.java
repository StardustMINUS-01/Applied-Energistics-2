package com.gregtechceu.gtceu.integration.ae2.pattern;

import java.util.OptionalInt;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class GTPatternVirtualCircuit {
    private static final String VIRTUAL_CIRCUIT = "gtceu_test_pattern_virtual_circuit";

    private GTPatternVirtualCircuit() {
    }

    public static OptionalInt read(ItemStack encodedPattern) {
        var data = encodedPattern.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return OptionalInt.empty();
        }

        var tag = data.copyTag();
        if (!tag.contains(VIRTUAL_CIRCUIT)) {
            return OptionalInt.empty();
        }

        var circuit = tag.getInt(VIRTUAL_CIRCUIT);
        return isValid(circuit) ? OptionalInt.of(circuit) : OptionalInt.empty();
    }

    public static void writeInPlace(ItemStack encodedPattern, OptionalInt circuit) {
        var tag = encodedPattern.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (circuit.isPresent() && isValid(circuit.getAsInt())) {
            tag.putInt(VIRTUAL_CIRCUIT, circuit.getAsInt());
            encodedPattern.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        } else {
            tag.remove(VIRTUAL_CIRCUIT);
            if (tag.isEmpty()) {
                encodedPattern.remove(DataComponents.CUSTOM_DATA);
            } else {
                encodedPattern.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
        }
    }

    public static boolean isValid(int circuit) {
        return circuit >= 0 && circuit <= 32;
    }
}
