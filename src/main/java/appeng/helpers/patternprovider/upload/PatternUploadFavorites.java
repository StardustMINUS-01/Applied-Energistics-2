package appeng.helpers.patternprovider.upload;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import appeng.core.AppEng;
import appeng.core.worlddata.AESavedData;

public final class PatternUploadFavorites extends AESavedData {
    private static final String NAME = AppEng.MOD_ID + "_pattern_upload_favorites";
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_PLAYER = "player";
    private static final String TAG_GROUPS = "groups";

    private final HashMap<UUID, Set<String>> favorites = new HashMap<>();

    private PatternUploadFavorites() {
    }

    private PatternUploadFavorites(CompoundTag tag) {
        var players = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            var playerTag = players.getCompound(i);
            var playerId = playerTag.getUUID(TAG_PLAYER);
            var groups = new HashSet<String>();
            var groupTags = playerTag.getList(TAG_GROUPS, Tag.TAG_STRING);
            for (int groupIndex = 0; groupIndex < groupTags.size(); groupIndex++) {
                groups.add(groupTags.getString(groupIndex));
            }
            favorites.put(playerId, groups);
        }
    }

    public static PatternUploadFavorites get(ServerPlayer player) {
        return get(player.server);
    }

    public static PatternUploadFavorites get(MinecraftServer server) {
        var overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Cannot retrieve upload favorites for a server that has no overworld.");
        }
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        PatternUploadFavorites::new,
                        (tag, provider) -> new PatternUploadFavorites(tag),
                        null),
                NAME);
    }

    public boolean isFavorite(ServerPlayer player, String groupName) {
        return favorites.getOrDefault(player.getUUID(), Set.of()).contains(groupName);
    }

    public void toggleFavorite(ServerPlayer player, String groupName) {
        var playerFavorites = favorites.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>());
        if (!playerFavorites.remove(groupName)) {
            playerFavorites.add(groupName);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var players = new ListTag();
        for (var entry : favorites.entrySet()) {
            var playerTag = new CompoundTag();
            playerTag.putUUID(TAG_PLAYER, entry.getKey());

            var groups = new ListTag();
            for (var groupName : entry.getValue()) {
                groups.add(StringTag.valueOf(groupName));
            }
            playerTag.put(TAG_GROUPS, groups);
            players.add(playerTag);
        }
        tag.put(TAG_PLAYERS, players);
        return tag;
    }
}
