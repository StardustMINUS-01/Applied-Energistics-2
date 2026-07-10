package appeng.integration.jei.patternencoding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.GenericStack;

public record PatternEncodeRequest(
        ResourceLocation recipeTypeUid,
        ResourceLocation recipeUid,
        PatternEncodeMode mode,
        List<@Nullable GenericStack> sparseInputs,
        List<@Nullable GenericStack> sparseOutputs,
        @Nullable ResourceLocation vanillaRecipeId,
        boolean allowSubstitution,
        boolean allowFluidSubstitution) {

    public static final int MAX_STACKS_PER_SIDE = 128;
    public static final int MAX_REQUESTS = 256;

    public static final StreamCodec<RegistryFriendlyByteBuf, PatternEncodeRequest> STREAM_CODEC = StreamCodec
            .ofMember(
                    PatternEncodeRequest::write,
                    PatternEncodeRequest::decode);

    public PatternEncodeRequest {
        Objects.requireNonNull(recipeTypeUid, "recipeTypeUid");
        Objects.requireNonNull(recipeUid, "recipeUid");
        Objects.requireNonNull(mode, "mode");
        sparseInputs = copySparseList(sparseInputs);
        sparseOutputs = copySparseList(sparseOutputs);
    }

    public static List<PatternEncodeRequest> readRequestList(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_REQUESTS) {
            throw new IllegalArgumentException("Invalid recipe-chain pattern request count: " + size);
        }

        var requests = new ArrayList<PatternEncodeRequest>(size);
        for (int i = 0; i < size; i++) {
            requests.add(STREAM_CODEC.decode(buffer));
        }
        return List.copyOf(requests);
    }

    public static void writeRequestList(List<PatternEncodeRequest> requests, RegistryFriendlyByteBuf buffer) {
        if (requests.size() > MAX_REQUESTS) {
            throw new IllegalArgumentException("Too many recipe-chain pattern requests: " + requests.size());
        }

        buffer.writeVarInt(requests.size());
        for (var request : requests) {
            STREAM_CODEC.encode(buffer, request);
        }
    }

    private static PatternEncodeRequest decode(RegistryFriendlyByteBuf buffer) {
        var recipeTypeUid = buffer.readResourceLocation();
        var recipeUid = buffer.readResourceLocation();
        var mode = buffer.readEnum(PatternEncodeMode.class);
        var sparseInputs = readGenericStackList(buffer);
        var sparseOutputs = readGenericStackList(buffer);
        var vanillaRecipeId = buffer.readBoolean() ? buffer.readResourceLocation() : null;
        var allowSubstitution = buffer.readBoolean();
        var allowFluidSubstitution = buffer.readBoolean();
        return new PatternEncodeRequest(
                recipeTypeUid,
                recipeUid,
                mode,
                sparseInputs,
                sparseOutputs,
                vanillaRecipeId,
                allowSubstitution,
                allowFluidSubstitution);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(recipeTypeUid);
        buffer.writeResourceLocation(recipeUid);
        buffer.writeEnum(mode);
        writeGenericStackList(sparseInputs, buffer);
        writeGenericStackList(sparseOutputs, buffer);
        buffer.writeBoolean(vanillaRecipeId != null);
        if (vanillaRecipeId != null) {
            buffer.writeResourceLocation(vanillaRecipeId);
        }
        buffer.writeBoolean(allowSubstitution);
        buffer.writeBoolean(allowFluidSubstitution);
    }

    private static List<@Nullable GenericStack> readGenericStackList(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_STACKS_PER_SIDE) {
            throw new IllegalArgumentException("Invalid generic stack list size: " + size);
        }

        var stacks = new ArrayList<@Nullable GenericStack>(size);
        for (int i = 0; i < size; i++) {
            stacks.add(GenericStack.readBuffer(buffer));
        }
        return Collections.unmodifiableList(stacks);
    }

    private static void writeGenericStackList(List<@Nullable GenericStack> stacks, RegistryFriendlyByteBuf buffer) {
        if (stacks.size() > MAX_STACKS_PER_SIDE) {
            throw new IllegalArgumentException("Too many generic stacks: " + stacks.size());
        }

        buffer.writeVarInt(stacks.size());
        for (var stack : stacks) {
            GenericStack.writeBuffer(stack, buffer);
        }
    }

    private static List<@Nullable GenericStack> copySparseList(List<@Nullable GenericStack> stacks) {
        return Collections.unmodifiableList(new ArrayList<>(stacks));
    }
}
