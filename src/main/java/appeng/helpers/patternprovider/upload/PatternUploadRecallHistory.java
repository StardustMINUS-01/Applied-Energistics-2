package appeng.helpers.patternprovider.upload;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import appeng.core.AppEng;
import appeng.core.worlddata.AESavedData;

public final class PatternUploadRecallHistory extends AESavedData {
    private static final String NAME = AppEng.MOD_ID + "_pattern_upload_recall_history";
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_PLAYER = "player";
    private static final String TAG_RECORDS = "records";
    private static final String TAG_GROUP = "group";
    private static final String TAG_PATTERN = "pattern";

    private final Map<UUID, ArrayDeque<PatternUploadRecallRecord>> recordsByPlayer = new HashMap<>();

    PatternUploadRecallHistory() {
    }

    PatternUploadRecallHistory(CompoundTag tag, HolderLookup.Provider registries) {
        var players = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            var playerTag = players.getCompound(playerIndex);
            if (!playerTag.hasUUID(TAG_PLAYER)) {
                continue;
            }

            var records = new ArrayDeque<PatternUploadRecallRecord>();
            var recordTags = playerTag.getList(TAG_RECORDS, Tag.TAG_COMPOUND);
            for (int recordIndex = 0; recordIndex < recordTags.size(); recordIndex++) {
                var recordTag = recordTags.getCompound(recordIndex);
                if (!recordTag.contains(TAG_GROUP, Tag.TAG_STRING)
                        || !recordTag.contains(TAG_PATTERN, Tag.TAG_COMPOUND)) {
                    continue;
                }

                var pattern = ItemStack.parseOptional(registries, recordTag.getCompound(TAG_PATTERN));
                if (!pattern.isEmpty()) {
                    records.addLast(new PatternUploadRecallRecord(recordTag.getString(TAG_GROUP), pattern));
                }
            }

            if (!records.isEmpty()) {
                recordsByPlayer.put(playerTag.getUUID(TAG_PLAYER), records);
            }
        }
    }

    public static PatternUploadRecallHistory get(ServerPlayer player) {
        return get(player.server);
    }

    public static PatternUploadRecallHistory get(MinecraftServer server) {
        var overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException(
                    "Cannot retrieve upload recall history for a server that has no overworld.");
        }
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        PatternUploadRecallHistory::new,
                        PatternUploadRecallHistory::new,
                        null),
                NAME);
    }

    public void recordUploadedPattern(UUID playerId, String providerGroupName, ItemStack pattern, int limit) {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(providerGroupName);
        Objects.requireNonNull(pattern);

        if (limit <= 0) {
            if (recordsByPlayer.remove(playerId) != null) {
                setDirty();
            }
            return;
        }

        var records = recordsByPlayer.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        records.addFirst(new PatternUploadRecallRecord(providerGroupName, pattern));
        trimToLimit(records, limit);
        setDirty();
    }

    public List<PatternUploadRecallRecord> getRecords(UUID playerId) {
        var records = recordsByPlayer.get(playerId);
        return records == null ? List.of() : List.copyOf(records);
    }

    @Nullable
    PatternUploadRecallRecord getMostRecentRecord(UUID playerId, int limit) {
        trimPlayerRecords(playerId, limit);
        var records = recordsByPlayer.get(playerId);
        return records == null ? null : records.peekFirst();
    }

    @Nullable
    PatternUploadRecallRecord getMostRecentRecord(UUID playerId, String providerGroupName, int limit) {
        trimPlayerRecords(playerId, limit);
        var records = recordsByPlayer.get(playerId);
        if (records == null) {
            return null;
        }
        for (var record : records) {
            if (record.providerGroupName().equals(providerGroupName)) {
                return record;
            }
        }
        return null;
    }

    public ItemStack getMostRecentPatternForGroup(UUID playerId, String providerGroupName, int limit) {
        var record = getMostRecentRecord(playerId, providerGroupName, limit);
        return record == null ? ItemStack.EMPTY : record.pattern();
    }

    void removeRecord(UUID playerId, PatternUploadRecallRecord record) {
        var records = recordsByPlayer.get(playerId);
        if (records == null || !records.removeFirstOccurrence(record)) {
            return;
        }

        if (records.isEmpty()) {
            recordsByPlayer.remove(playerId);
        }
        setDirty();
    }

    private void trimPlayerRecords(UUID playerId, int limit) {
        var records = recordsByPlayer.get(playerId);
        if (records == null) {
            return;
        }

        if (limit <= 0) {
            recordsByPlayer.remove(playerId);
            setDirty();
        } else if (trimToLimit(records, limit)) {
            if (records.isEmpty()) {
                recordsByPlayer.remove(playerId);
            }
            setDirty();
        }
    }

    private static boolean trimToLimit(ArrayDeque<PatternUploadRecallRecord> records, int limit) {
        var changed = false;
        while (records.size() > limit) {
            records.removeLast();
            changed = true;
        }
        return changed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var players = new ListTag();
        for (var entry : recordsByPlayer.entrySet()) {
            var playerTag = new CompoundTag();
            playerTag.putUUID(TAG_PLAYER, entry.getKey());

            var records = new ListTag();
            for (var record : entry.getValue()) {
                var recordTag = new CompoundTag();
                recordTag.putString(TAG_GROUP, record.providerGroupName());
                recordTag.put(TAG_PATTERN, record.pattern().save(registries));
                records.add(recordTag);
            }
            playerTag.put(TAG_RECORDS, records);
            players.add(playerTag);
        }
        tag.put(TAG_PLAYERS, players);
        return tag;
    }
}
