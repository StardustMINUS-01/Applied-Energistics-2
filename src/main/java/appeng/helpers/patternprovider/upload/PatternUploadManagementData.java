package appeng.helpers.patternprovider.upload;

import net.minecraft.network.RegistryFriendlyByteBuf;

import appeng.menu.guisync.PacketWritable;

public record PatternUploadManagementData(
        PatternUploadTargetGroups targetGroups) implements PacketWritable {
    public static final PatternUploadManagementData EMPTY = new PatternUploadManagementData(
            PatternUploadTargetGroups.EMPTY);

    public PatternUploadManagementData(RegistryFriendlyByteBuf data) {
        this(new PatternUploadTargetGroups(data));
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        targetGroups.writeToPacket(data);
    }
}
