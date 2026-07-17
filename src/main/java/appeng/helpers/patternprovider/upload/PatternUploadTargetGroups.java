package appeng.helpers.patternprovider.upload;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;

import appeng.menu.guisync.PacketWritable;

public record PatternUploadTargetGroups(List<PatternUploadTargetGroup> groups) implements PacketWritable {
    public static final PatternUploadTargetGroups EMPTY = new PatternUploadTargetGroups(List.of());

    public PatternUploadTargetGroups {
        groups = List.copyOf(groups);
    }

    public PatternUploadTargetGroups(RegistryFriendlyByteBuf data) {
        this(readGroups(data));
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        data.writeVarInt(groups.size());
        for (var group : groups) {
            group.writeToPacket(data);
        }
    }

    private static List<PatternUploadTargetGroup> readGroups(RegistryFriendlyByteBuf data) {
        var size = data.readVarInt();
        var groups = new ArrayList<PatternUploadTargetGroup>(size);
        for (int i = 0; i < size; i++) {
            groups.add(new PatternUploadTargetGroup(data));
        }
        return groups;
    }
}
