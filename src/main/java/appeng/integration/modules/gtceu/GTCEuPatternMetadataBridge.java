package appeng.integration.modules.gtceu;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

public final class GTCEuPatternMetadataBridge {
    private static final String GT_RECIPE_CLASS = "com.gregtechceu.gtceu.api.recipe.GTRecipe";
    private static final String RECIPE_METADATA_CLASS = "com.gregtechceu.gtceu.integration.ae2.pattern.GTRecipePatternMetadata";
    private static final String VIRTUAL_CIRCUIT_CLASS = "com.gregtechceu.gtceu.integration.ae2.pattern.GTPatternVirtualCircuit";
    private static final String VIRTUAL_CIRCUIT_DISPLAY_CLASS = "com.gregtechceu.gtceu.integration.ae2.pattern.GTPatternVirtualCircuitDisplay";
    private static final String INT_CIRCUIT_CLASS = "com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour";

    private GTCEuPatternMetadataBridge() {
    }

    public static ItemStack encodeProcessingPatternWithVirtualCircuitMetadata(
            List<GenericStack> sparseInputs,
            List<GenericStack> sparseOutputs,
            OptionalInt preferredVirtualCircuit) {
        var extracted = extractVirtualCircuit(sparseInputs, preferredVirtualCircuit);
        var encoded = PatternDetailsHelper.encodeProcessingPattern(extracted.sparseInputs(), sparseOutputs);
        writeVirtualCircuit(encoded, extracted.virtualCircuit());
        return encoded;
    }

    public static OptionalInt getVirtualCircuitFromRecipe(@Nullable Object recipeLike) {
        return getVirtualCircuitFromRecipe(recipeLike, 0);
    }

    public static OptionalInt getVirtualCircuitFromEncodedPattern(ItemStack encodedPattern) {
        var read = staticMethod(VIRTUAL_CIRCUIT_CLASS, "read", ItemStack.class);
        if (read == null) {
            return OptionalInt.empty();
        }

        var result = invoke(read, encodedPattern);
        return result instanceof OptionalInt optional ? optional : OptionalInt.empty();
    }

    public static void writeVirtualCircuit(ItemStack encodedPattern, OptionalInt circuit) {
        var writeInPlace = staticMethod(VIRTUAL_CIRCUIT_CLASS, "writeInPlace", ItemStack.class, OptionalInt.class);
        if (writeInPlace != null) {
            invoke(writeInPlace, encodedPattern, circuit);
        }
    }

    public static void appendVirtualCircuitDisplayInput(ItemStack encodedPattern, PatternDetailsTooltip tooltip) {
        var displayInput = getVirtualCircuitDisplayInput(encodedPattern);
        if (displayInput != null) {
            tooltip.addInput(displayInput);
        }
    }

    public static List<GenericStack> restoreVirtualCircuitInput(
            ItemStack encodedPattern,
            List<GenericStack> sparseInputs,
            int maxSlots) {
        var displayInput = getVirtualCircuitDisplayInput(encodedPattern);
        if (displayInput == null || containsCircuitInput(sparseInputs)) {
            return sparseInputs;
        }

        var inputs = new ArrayList<>(sparseInputs);
        for (int i = 0; i < inputs.size() && i < maxSlots; i++) {
            if (inputs.get(i) == null) {
                inputs.set(i, displayInput);
                return Collections.unmodifiableList(inputs);
            }
        }

        if (inputs.size() < maxSlots) {
            inputs.add(displayInput);
            return Collections.unmodifiableList(inputs);
        }

        return sparseInputs;
    }

    @Nullable
    public static GenericStack getVirtualCircuitDisplayInput(ItemStack encodedPattern) {
        var circuit = getVirtualCircuitFromEncodedPattern(encodedPattern);
        if (circuit.isEmpty()) {
            return null;
        }

        var toDisplayStack = staticMethod(VIRTUAL_CIRCUIT_DISPLAY_CLASS, "toDisplayStack", int.class);
        if (toDisplayStack == null) {
            return null;
        }

        var displayStack = invoke(toDisplayStack, circuit.getAsInt());
        if (displayStack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
            return GenericStack.fromItemStack(itemStack);
        }
        return null;
    }

    public static List<List<GenericStack>> appendVirtualCircuitIngredient(
            List<List<GenericStack>> genericIngredients,
            OptionalInt virtualCircuit) {
        if (virtualCircuit.isEmpty()) {
            return genericIngredients;
        }
        if (containsCircuitIngredient(genericIngredients)) {
            return genericIngredients;
        }

        var displayStack = getVirtualCircuitDisplayStack(virtualCircuit.getAsInt());
        var displayGenericStack = displayStack != null ? GenericStack.fromItemStack(displayStack) : null;
        if (displayGenericStack == null) {
            return genericIngredients;
        }

        var result = new ArrayList<List<GenericStack>>(genericIngredients.size() + 1);
        result.addAll(genericIngredients);
        result.add(List.of(displayGenericStack));
        return result;
    }

    @Nullable
    public static ItemStack getVirtualCircuitDisplayStack(int circuit) {
        var toDisplayStack = staticMethod(VIRTUAL_CIRCUIT_DISPLAY_CLASS, "toDisplayStack", int.class);
        if (toDisplayStack == null) {
            return null;
        }

        var displayStack = invoke(toDisplayStack, circuit);
        if (displayStack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
            return itemStack;
        }
        return null;
    }

    private static VirtualCircuitExtraction extractVirtualCircuit(List<GenericStack> sparseInputs,
            OptionalInt preferredVirtualCircuit) {
        var inputs = new ArrayList<GenericStack>(sparseInputs);

        OptionalInt foundCircuit = OptionalInt.empty();
        for (int i = 0; i < inputs.size(); i++) {
            var circuit = getCircuitConfiguration(inputs.get(i));
            if (circuit.isPresent()) {
                if (foundCircuit.isEmpty()) {
                    foundCircuit = circuit;
                }
                inputs.set(i, null);
            }
        }

        if (foundCircuit.isEmpty()) {
            foundCircuit = preferredVirtualCircuit;
        }

        return new VirtualCircuitExtraction(Collections.unmodifiableList(inputs), foundCircuit);
    }

    private static boolean containsCircuitIngredient(List<List<GenericStack>> genericIngredients) {
        for (var ingredient : genericIngredients) {
            for (var stack : ingredient) {
                if (getCircuitConfiguration(stack).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsCircuitInput(List<GenericStack> sparseInputs) {
        for (var stack : sparseInputs) {
            if (getCircuitConfiguration(stack).isPresent()) {
                return true;
            }
        }
        return false;
    }

    public static OptionalInt getCircuitConfiguration(@Nullable GenericStack stack) {
        if (stack == null || stack.amount() <= 0 || !(stack.what() instanceof AEItemKey itemKey)) {
            return OptionalInt.empty();
        }

        var isIntegratedCircuit = staticMethod(INT_CIRCUIT_CLASS, "isIntegratedCircuit", ItemStack.class);
        var getCircuitConfiguration = staticMethod(INT_CIRCUIT_CLASS, "getCircuitConfiguration", ItemStack.class);
        if (isIntegratedCircuit == null || getCircuitConfiguration == null) {
            return OptionalInt.empty();
        }

        var itemStack = itemKey.toStack(1);
        if (!Boolean.TRUE.equals(invoke(isIntegratedCircuit, itemStack))) {
            return OptionalInt.empty();
        }

        var configuration = invoke(getCircuitConfiguration, itemStack);
        if (configuration instanceof Integer circuit && isValidVirtualCircuit(circuit)) {
            return OptionalInt.of(circuit);
        }
        return OptionalInt.empty();
    }

    private static boolean isValidVirtualCircuit(int circuit) {
        var isValid = staticMethod(VIRTUAL_CIRCUIT_CLASS, "isValid", int.class);
        var result = isValid != null ? invoke(isValid, circuit) : null;
        return result instanceof Boolean valid ? valid : circuit >= 0;
    }

    private static OptionalInt getVirtualCircuitFromRecipe(@Nullable Object recipeLike, int depth) {
        if (recipeLike == null || depth > 4) {
            return OptionalInt.empty();
        }

        var gtRecipeClass = classOrNull(GT_RECIPE_CLASS);
        if (gtRecipeClass != null && gtRecipeClass.isInstance(recipeLike)) {
            var getVirtualCircuit = staticMethod(RECIPE_METADATA_CLASS, "getVirtualCircuit", gtRecipeClass);
            if (getVirtualCircuit == null) {
                return OptionalInt.empty();
            }

            var result = invoke(getVirtualCircuit, recipeLike);
            return result instanceof OptionalInt optional ? optional : OptionalInt.empty();
        }

        for (var methodName : List.of("gtceu$getRecipe", "getRecipe", "recipe", "value")) {
            var unwrapped = invokeNoArg(recipeLike, methodName);
            var circuit = getVirtualCircuitFromRecipe(unwrapped, depth + 1);
            if (circuit.isPresent()) {
                return circuit;
            }
        }

        for (var fieldName : List.of("recipe", "gtRecipe")) {
            var unwrapped = readField(recipeLike, fieldName);
            var circuit = getVirtualCircuitFromRecipe(unwrapped, depth + 1);
            if (circuit.isPresent()) {
                return circuit;
            }
        }

        return OptionalInt.empty();
    }

    @Nullable
    private static Class<?> classOrNull(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    @Nullable
    private static Method staticMethod(String className, String methodName, Class<?>... parameterTypes) {
        var clazz = classOrNull(className);
        if (clazz == null) {
            return null;
        }

        try {
            var method = clazz.getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | SecurityException e) {
            return null;
        }
    }

    @Nullable
    private static Object invoke(Method method, Object... args) {
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError e) {
            return null;
        }
    }

    @Nullable
    private static Object invokeNoArg(Object target, String methodName) {
        try {
            var method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException | LinkageError e) {
            return null;
        }
    }

    @Nullable
    private static Object readField(Object target, String fieldName) {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException | LinkageError e) {
                return null;
            }
        }
        return null;
    }

    private record VirtualCircuitExtraction(List<GenericStack> sparseInputs, OptionalInt virtualCircuit) {
    }
}
