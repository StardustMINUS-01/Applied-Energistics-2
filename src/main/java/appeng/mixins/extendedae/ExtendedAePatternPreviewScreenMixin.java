package appeng.mixins.extendedae;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import appeng.api.stacks.GenericStack;
import appeng.helpers.pattern.PatternPreviewVirtualInputHelper;

/** Draws catalyst markers without changing ExtendedAE preview item names or tooltips. */
@Pseudo
@Mixin(AbstractContainerScreen.class)
public abstract class ExtendedAePatternPreviewScreenMixin {
    @Shadow
    @Final
    protected AbstractContainerMenu menu;

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void ae2$renderPatternPreviewCatalystMarker(GuiGraphics guiGraphics, Slot slot, CallbackInfo ci) {
        if (!((Object) menu instanceof ContainerPatternAccessor container)) {
            return;
        }

        var displayStack = GenericStack.fromItemStack(slot.getItem());
        var previewSlotIndex = menu.slots.indexOf(slot);
        if (displayStack == null || !PatternPreviewVirtualInputHelper.isCatalystPreviewSlot(
                container.ae2$getPatternStack(), container.ae2$getInputs(), previewSlotIndex, displayStack)) {
            return;
        }

        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 500);
        guiGraphics.drawString(Minecraft.getInstance().font, "C", slot.x + 11, slot.y - 1, 0xFFFFFF00, false);
        poseStack.popPose();
    }

}
