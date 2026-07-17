package appeng.crafting.pattern;

import java.util.Collections;
import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record PatternVirtualInputs(List<PatternCatalyst> entries) {
    public static final PatternVirtualInputs EMPTY = new PatternVirtualInputs(List.of());

    public static final Codec<PatternVirtualInputs> CODEC = PatternCatalyst.CODEC.listOf()
            .xmap(PatternVirtualInputs::new, PatternVirtualInputs::entries);

    public static final StreamCodec<RegistryFriendlyByteBuf, PatternVirtualInputs> STREAM_CODEC = PatternCatalyst.STREAM_CODEC
            .apply(ByteBufCodecs.list())
            .map(PatternVirtualInputs::new, PatternVirtualInputs::entries);

    public PatternVirtualInputs {
        entries = Collections.unmodifiableList(entries);
    }
}
