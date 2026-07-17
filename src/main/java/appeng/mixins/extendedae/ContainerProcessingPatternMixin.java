package appeng.mixins.extendedae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.helpers.pattern.PatternPreviewVirtualInputHelper;

/** Restores virtual inputs as display-only catalyst slots in ExtendedAE's pattern preview menu. */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.container.pattern.ContainerProcessingPattern", remap = false)
public class ContainerProcessingPatternMixin {
    @Inject(method = "analyse", at = @At("TAIL"), remap = false)
    private void ae2$restoreVirtualInputs(CallbackInfo ci) {
        var container = (ContainerPatternAccessor) (Object) this;
        PatternPreviewVirtualInputHelper.restoreVirtualInputs(container.ae2$getPatternStack(),
                container.ae2$getInputs());
    }
}
