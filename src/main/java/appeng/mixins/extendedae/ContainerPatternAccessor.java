package appeng.mixins.extendedae;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.GenericStack;

@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.container.pattern.ContainerPattern", remap = false)
public interface ContainerPatternAccessor {
    @Accessor("stack")
    ItemStack ae2$getPatternStack();

    @Accessor("inputs")
    List<GenericStack[]> ae2$getInputs();
}
