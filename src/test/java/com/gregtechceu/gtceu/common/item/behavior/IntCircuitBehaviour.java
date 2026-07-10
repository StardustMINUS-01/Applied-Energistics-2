package com.gregtechceu.gtceu.common.item.behavior;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class IntCircuitBehaviour {
    public static final int CIRCUIT_MAX = 32;

    private static final String CONFIG = "gtceu_test_circuit_config";

    private IntCircuitBehaviour() {
    }

    public static ItemStack stack(int configuration) {
        var stack = new ItemStack(Items.COMPARATOR);
        setCircuitConfiguration(stack, configuration);
        return stack;
    }

    public static void setCircuitConfiguration(ItemStack itemStack, int configuration) {
        var tag = new CompoundTag();
        tag.putInt(CONFIG, configuration);
        itemStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getCircuitConfiguration(ItemStack itemStack) {
        var data = itemStack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0;
        }

        return data.copyTag().getInt(CONFIG);
    }

    public static boolean isIntegratedCircuit(ItemStack itemStack) {
        var data = itemStack.get(DataComponents.CUSTOM_DATA);
        return itemStack.is(Items.COMPARATOR) && data != null && data.copyTag().contains(CONFIG);
    }
}
