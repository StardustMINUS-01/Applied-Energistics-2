package appeng.integration.modules.jei;

import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeTransferRegistration;

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
    }
}
