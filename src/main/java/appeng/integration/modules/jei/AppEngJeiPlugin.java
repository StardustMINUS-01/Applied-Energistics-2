package appeng.integration.modules.jei;

import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import appeng.core.AppEng;

@JeiPlugin
public class AppEngJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return AppEng.makeId("jei");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        GTCEuJeiRecipeTransfer.register(registration);
        JeiPatternEncodingRecipeTransfer.register(registration);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        var searchTextHandler = new JeiSearchTextGhostIngredientHandler();
        registration.addGhostIngredientHandler(MEStorageScreen.class, searchTextHandler);
        registration.addGhostIngredientHandler(PatternAccessTermScreen.class, searchTextHandler);
    }
}
