package appeng.helpers.patternprovider.upload;

import net.minecraft.server.level.ServerPlayer;

import appeng.api.networking.IGrid;

/**
 * Server-side hand-off point for a future per-target-group settings menu.
 */
public final class PatternUploadTargetSettings {
    private PatternUploadTargetSettings() {
    }

    public static void open(ServerPlayer player, IGrid grid, String groupName) {
        // A settings screen will be opened here once target-group settings have been designed.
    }
}
