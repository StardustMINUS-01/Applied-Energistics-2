package appeng.helpers.patternprovider.upload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.menu.guisync.PacketWritable;

public record PatternUploadTargetGroup(
        String name,
        PatternContainerGroup displayGroup,
        int usedSlots,
        int totalSlots,
        boolean favorite,
        boolean canUpload,
        ItemStack recentUploadedPattern) implements PacketWritable {

    public PatternUploadTargetGroup {
        recentUploadedPattern = recentUploadedPattern.copy();
    }

    public PatternUploadTargetGroup(RegistryFriendlyByteBuf data) {
        this(
                data.readUtf(),
                PatternContainerGroup.readFromPacket(data),
                data.readVarInt(),
                data.readVarInt(),
                data.readBoolean(),
                data.readBoolean(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(data));
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        data.writeUtf(name);
        displayGroup.writeToPacket(data);
        data.writeVarInt(usedSlots);
        data.writeVarInt(totalSlots);
        data.writeBoolean(favorite);
        data.writeBoolean(canUpload);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(data, recentUploadedPattern);
    }
}
