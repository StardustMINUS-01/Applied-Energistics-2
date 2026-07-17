package appeng.crafting.pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import appeng.api.stacks.GenericStack;

public record PatternCatalyst(int sourceSlot, GenericStack stack) {
    public static final Codec<PatternCatalyst> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Codec.INT.fieldOf("sourceSlot").forGetter(PatternCatalyst::sourceSlot),
            GenericStack.CODEC.fieldOf("stack").forGetter(PatternCatalyst::stack))
            .apply(builder, PatternCatalyst::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PatternCatalyst> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            PatternCatalyst::sourceSlot,
            GenericStack.STREAM_CODEC,
            PatternCatalyst::stack,
            PatternCatalyst::new);
}
