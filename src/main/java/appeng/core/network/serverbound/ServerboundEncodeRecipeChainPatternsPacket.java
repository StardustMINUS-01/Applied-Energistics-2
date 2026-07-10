package appeng.core.network.serverbound;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import appeng.core.network.CustomAppEngPayload;
import appeng.core.network.ServerboundPacket;
import appeng.integration.jei.patternencoding.PatternEncodeRequest;
import appeng.integration.jei.patternencoding.PatternEncodeStopReason;
import appeng.integration.jei.patternencoding.RecipeChainPatternEncodingService;

public record ServerboundEncodeRecipeChainPatternsPacket(
        List<PatternEncodeRequest> requests) implements ServerboundPacket {

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundEncodeRecipeChainPatternsPacket> STREAM_CODEC = StreamCodec
            .ofMember(
                    ServerboundEncodeRecipeChainPatternsPacket::write,
                    ServerboundEncodeRecipeChainPatternsPacket::decode);

    public static final Type<ServerboundEncodeRecipeChainPatternsPacket> TYPE = CustomAppEngPayload
            .createType("encode_recipe_chain_patterns");

    public ServerboundEncodeRecipeChainPatternsPacket {
        requests = List.copyOf(requests);
    }

    @Override
    public Type<ServerboundEncodeRecipeChainPatternsPacket> type() {
        return TYPE;
    }

    public static ServerboundEncodeRecipeChainPatternsPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ServerboundEncodeRecipeChainPatternsPacket(PatternEncodeRequest.readRequestList(buffer));
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        PatternEncodeRequest.writeRequestList(requests, buffer);
    }

    @Override
    public void handleOnServer(ServerPlayer player) {
        var result = RecipeChainPatternEncodingService.encodeRecipeChainPatterns(player, requests);
        player.sendSystemMessage(formatResult(
                result.encodedCount(),
                result.skippedExistingCount(),
                result.skippedInvalidCount(),
                result.stopReason()));
    }

    private static Component formatResult(int encodedCount, int skippedExistingCount, int skippedInvalidCount,
            PatternEncodeStopReason stopReason) {
        var message = "Encoded " + encodedCount + " pattern(s), skipped " + skippedExistingCount
                + " existing, " + skippedInvalidCount + " invalid";
        if (stopReason != PatternEncodeStopReason.NONE) {
            message += ", stopped: " + stopReason.name();
        }
        return Component.literal(message);
    }
}
