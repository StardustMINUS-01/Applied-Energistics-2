package appeng.integration.jei.patternencoding;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record PatternEncodeEntryResult(
        ResourceLocation recipeUid,
        PatternEncodeEntryStatus status,
        @Nullable Component message) {
}
