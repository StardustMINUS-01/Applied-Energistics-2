package appeng.integration.modules.jei;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeHolder;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.registration.IRecipeTransferRegistration;

import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;

final class JeiPatternEncodingRecipeTransfer<T extends PatternEncodingTermMenu>
        implements IUniversalRecipeTransferHandler<T> {
    private final Class<? extends T> containerClass;
    private final MenuType<T> menuType;
    private final IRecipeTransferHandlerHelper transferHelper;

    JeiPatternEncodingRecipeTransfer(Class<? extends T> containerClass, MenuType<T> menuType,
            IRecipeTransferHandlerHelper transferHelper) {
        this.containerClass = containerClass;
        this.menuType = menuType;
        this.transferHelper = transferHelper;
    }

    static void register(IRecipeTransferRegistration registration) {
        var transferHelper = registration.getTransferHelper();
        register(registration, PatternEncodingTermMenu.class, PatternEncodingTermMenu.TYPE, transferHelper);
        GTCEuJeiMenuCompatibility.findPatternEncodingMenu(
                GTCEuJeiMenuCompatibility.AE2WT_PATTERN_ENCODING_MENU_CLASS)
                .ifPresent(menu -> register(registration, menu, transferHelper));
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
    @Nullable
    public IRecipeTransferError transferRecipe(T menu, Object recipe,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (recipe instanceof RecipeHolder<?> holder && EncodingHelper.isSupportedCraftingRecipe(holder.value())) {
            if (doTransfer) {
                EncodingHelper.encodeCraftingRecipe(menu, holder, JeiStackHelper.ofCraftingInputs(recipeSlots),
                        stack -> true);
            }
            return null;
        }

        var inputs = JeiStackHelper.ofInputs(recipeSlots);
        var outputs = JeiStackHelper.ofOutputs(recipeSlots);
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return transferHelper.createInternalError();
        }
        if (doTransfer) {
            EncodingHelper.encodeProcessingRecipe(menu, inputs, outputs);
        }
        return null;
    }

    private static <T extends PatternEncodingTermMenu> void register(IRecipeTransferRegistration registration,
            Class<? extends T> containerClass, MenuType<T> menuType, IRecipeTransferHandlerHelper transferHelper) {
        registration.addUniversalRecipeTransferHandler(
                new JeiPatternEncodingRecipeTransfer<>(containerClass, menuType, transferHelper));
    }

    private static void register(IRecipeTransferRegistration registration,
            GTCEuJeiMenuCompatibility.PatternEncodingMenu menu, IRecipeTransferHandlerHelper transferHelper) {
        registerUnchecked(registration, menu.containerClass(), menu.menuType(), transferHelper);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void registerUnchecked(IRecipeTransferRegistration registration,
            Class<? extends PatternEncodingTermMenu> containerClass,
            MenuType<? extends PatternEncodingTermMenu> menuType,
            IRecipeTransferHandlerHelper transferHelper) {
        register(registration, (Class) containerClass, (MenuType) menuType, transferHelper);
    }
}
