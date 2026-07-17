package appeng.integration.modules.gtceu;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.localization.GuiText;

public final class GTCEuPatternMetadataBridge {
    private static final String GT_RECIPE_CLASS = "com.gregtechceu.gtceu.api.recipe.GTRecipe";
    private static final String RECIPE_METADATA_CLASS = "com.gregtechceu.gtceu.integration.ae2.pattern.GTRecipePatternMetadata";
    private static final String VIRTUAL_CIRCUIT_CLASS = "com.gregtechceu.gtceu.integration.ae2.pattern.GTPatternVirtualCircuit";
    private static final String VIRTUAL_CIRCUIT_DISPLAY_CLASS = "com.gregtechceu.gtceu.integration.ae2.pattern.GTPatternVirtualCircuitDisplay";
    private static final String INT_CIRCUIT_CLASS = "com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour";
    private static final String ITEM_RECIPE_CAPABILITY_CLASS = "com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability";
    private static final String FLUID_RECIPE_CAPABILITY_CLASS = "com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability";

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

    public static List<GenericStack> getNonConsumableInputsFromRecipe(@Nullable Object recipeLike) {
        return getNonConsumableInputsFromRecipe(recipeLike, 0);
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

    @Nullable
    public static GenericStack getVirtualCircuitNamedDisplayInput(ItemStack encodedPattern) {
        var circuit = getVirtualCircuitFromEncodedPattern(encodedPattern);
        if (circuit.isEmpty()) {
            return null;
        }

        var displayStack = getVirtualCircuitDisplayStack(circuit.getAsInt());
        return displayStack != null ? GenericStack.fromItemStack(displayStack) : null;
    }

    @Nullable
    public static ItemStack getVirtualCircuitDisplayStack(int circuit) {
        var toDisplayStack = staticMethod(VIRTUAL_CIRCUIT_DISPLAY_CLASS, "toDisplayStack", int.class);
        if (toDisplayStack == null) {
            return null;
        }

        var displayStack = invoke(toDisplayStack, circuit);
        if (displayStack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
            return withVirtualCircuitDisplayName(itemStack, circuit);
        }
        return null;
    }

    private static ItemStack withVirtualCircuitDisplayName(ItemStack displayStack, int circuit) {
        var namedDisplayStack = displayStack.copy();
        namedDisplayStack.set(DataComponents.CUSTOM_NAME,
                GuiText.PatternVirtualCircuit.text(namedDisplayStack.getHoverName(), circuit));
        return namedDisplayStack;
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

    private static List<GenericStack> getNonConsumableInputsFromRecipe(@Nullable Object recipeLike, int depth) {
        if (recipeLike == null || depth > 4) {
            return List.of();
        }

        var gtRecipeClass = classOrNull(GT_RECIPE_CLASS);
        if (gtRecipeClass != null && gtRecipeClass.isInstance(recipeLike)) {
            var inputs = readField(recipeLike, "inputs");
            if (!(inputs instanceof java.util.Map<?, ?> inputCapabilities)) {
                return List.of();
            }

            var result = new ArrayList<GenericStack>();
            for (var inputCapability : inputCapabilities.entrySet()) {
                var isItemCapability = isInstanceOf(inputCapability.getKey(), ITEM_RECIPE_CAPABILITY_CLASS);
                var isFluidCapability = isInstanceOf(inputCapability.getKey(), FLUID_RECIPE_CAPABILITY_CLASS);
                if (!isItemCapability && !isFluidCapability) {
                    continue;
                }

                var contents = inputCapability.getValue();
                if (!(contents instanceof Iterable<?> entries)) {
                    continue;
                }
                for (var entry : entries) {
                    var chance = readField(entry, "chance");
                    if (!(chance instanceof Integer value) || value != 0) {
                        continue;
                    }
                    var ingredient = readField(entry, "content");
                    var genericStack = isItemCapability
                            ? getSingleItemInput(ingredient)
                            : getSingleFluidInput(ingredient);
                    if (genericStack != null) {
                        result.add(genericStack);
                    }
                }
            }
            return result;
        }

        for (var methodName : List.of("gtceu$getRecipe", "getRecipe", "recipe", "value")) {
            var result = getNonConsumableInputsFromRecipe(invokeNoArg(recipeLike, methodName), depth + 1);
            if (!result.isEmpty()) {
                return result;
            }
        }
        return List.of();
    }

    @Nullable
    private static GenericStack getSingleItemInput(@Nullable Object ingredient) {
        var candidates = invokeNoArg(ingredient, "getItems");
        if (!(candidates instanceof ItemStack[])) {
            candidates = invokeNoArg(invokeNoArg(ingredient, "ingredient"), "getItems");
        }
        if (!(candidates instanceof ItemStack[] stacks) || stacks.length != 1 || stacks[0].isEmpty()) {
            return null;
        }

        var amount = invokeNoArg(ingredient, "amount");
        var displayStack = amount instanceof Integer count ? stacks[0].copyWithCount(count) : stacks[0];
        return GenericStack.fromItemStack(displayStack);
    }

    @Nullable
    private static GenericStack getSingleFluidInput(@Nullable Object ingredient) {
        var candidates = invokeNoArg(ingredient, "getFluids");
        if (!(candidates instanceof FluidStack[] stacks) || stacks.length != 1 || stacks[0].isEmpty()) {
            return null;
        }

        return GenericStack.fromFluidStack(stacks[0]);
    }

    private static boolean isInstanceOf(@Nullable Object value, String className) {
        var clazz = classOrNull(className);
        return clazz != null && clazz.isInstance(value);
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
