package appeng.integration.modules.jei;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;

import appeng.integration.modules.gtceu.GTCEuPatternMetadataBridge;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;

final class GTCEuJeiRecipeTransfer<T extends PatternEncodingTermMenu> implements IRecipeTransferHandler<T, Object> {
    private static final String GT_REGISTRIES_CLASS = "com.gregtechceu.gtceu.api.registry.GTRegistries";
    private static final String GT_JEI_CATEGORY_CLASS = "com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeJEICategory";

    private final Class<? extends T> containerClass;
    private final MenuType<T> menuType;
    private final RecipeType<Object> recipeType;
    private final IRecipeTransferHandlerHelper transferHelper;

    private GTCEuJeiRecipeTransfer(Class<? extends T> containerClass,
            MenuType<T> menuType,
            RecipeType<Object> recipeType,
            IRecipeTransferHandlerHelper transferHelper) {
        this.containerClass = containerClass;
        this.menuType = menuType;
        this.recipeType = recipeType;
        this.transferHelper = transferHelper;
    }

    static void register(IRecipeTransferRegistration registration) {
        var transferHelper = registration.getTransferHelper();
        for (var recipeType : getGTCEuRecipeTypes()) {
            register(registration, PatternEncodingTermMenu.class, PatternEncodingTermMenu.TYPE, recipeType,
                    transferHelper);
            GTCEuJeiMenuCompatibility.findPatternEncodingMenu(
                    GTCEuJeiMenuCompatibility.AE2WT_PATTERN_ENCODING_MENU_CLASS)
                    .ifPresent(menu -> register(registration, menu, recipeType, transferHelper));
        }
    }

    @Override
    public Class<? extends T> getContainerClass() {
        return containerClass;
    }

    @Override
    public Optional<MenuType<T>> getMenuType() {
        return Optional.of(menuType);
    }

    @Override
    public RecipeType<Object> getRecipeType() {
        return recipeType;
    }

    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(T menu, Object recipe,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        var inputs = JeiStackHelper.ofInputs(recipeSlots);
        var outputs = JeiStackHelper.ofOutputs(recipeSlots);
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return transferHelper.createInternalError();
        }

        if (doTransfer) {
            EncodingHelper.encodeProcessingRecipe(
                    menu,
                    inputs,
                    outputs,
                    GTCEuPatternMetadataBridge.getVirtualCircuitFromRecipe(recipe),
                    GTCEuPatternMetadataBridge.getNonConsumableInputsFromRecipe(recipe));
        }

        return null;
    }

    private static <T extends PatternEncodingTermMenu> void register(IRecipeTransferRegistration registration,
            Class<? extends T> containerClass,
            MenuType<T> menuType,
            RecipeType<Object> recipeType,
            IRecipeTransferHandlerHelper transferHelper) {
        registration.addRecipeTransferHandler(
                new GTCEuJeiRecipeTransfer<>(containerClass, menuType, recipeType, transferHelper),
                recipeType);
    }

    private static void register(IRecipeTransferRegistration registration,
            GTCEuJeiMenuCompatibility.PatternEncodingMenu menu,
            RecipeType<Object> recipeType,
            IRecipeTransferHandlerHelper transferHelper) {
        registerUnchecked(registration, menu.containerClass(), menu.menuType(), recipeType, transferHelper);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void registerUnchecked(IRecipeTransferRegistration registration,
            Class<? extends PatternEncodingTermMenu> containerClass,
            MenuType<? extends PatternEncodingTermMenu> menuType,
            RecipeType<Object> recipeType,
            IRecipeTransferHandlerHelper transferHelper) {
        register(registration, (Class) containerClass, (MenuType) menuType, recipeType, transferHelper);
    }

    private static List<RecipeType<Object>> getGTCEuRecipeTypes() {
        var categories = readStaticField(GT_REGISTRIES_CLASS, "RECIPE_CATEGORIES");
        var types = readStaticField(GT_JEI_CATEGORY_CLASS, "TYPES");
        if (!(categories instanceof Iterable<?> iterable) || !(types instanceof Function<?, ?>)) {
            return List.of();
        }

        var function = castFunction(types);
        var recipeTypes = new ArrayList<RecipeType<Object>>();
        for (var category : iterable) {
            recipeTypes.add(castRecipeType(function.apply(category)));
        }
        return recipeTypes;
    }

    @SuppressWarnings("unchecked")
    private static Function<Object, Object> castFunction(Object function) {
        return (Function<Object, Object>) function;
    }

    @SuppressWarnings("unchecked")
    private static RecipeType<Object> castRecipeType(Object recipeType) {
        return (RecipeType<Object>) recipeType;
    }

    @Nullable
    private static Object readStaticField(String className, String fieldName) {
        try {
            Class<?> clazz = Class.forName(className);
            Field field = clazz.getField(fieldName);
            return field.get(null);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            return null;
        }
    }
}
