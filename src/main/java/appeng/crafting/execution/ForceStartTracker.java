package appeng.crafting.execution;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;

final class ForceStartTracker {
    private final KeyCounter waitingFor = new KeyCounter();

    void set(KeyCounter missingItems) {
        clear();
        addAll(missingItems);
    }

    void addAll(KeyCounter missingItems) {
        for (var entry : missingItems) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                waitingFor.add(entry.getKey(), entry.getLongValue());
            }
        }
    }

    long get(AEKey what) {
        if (what == null) {
            return 0;
        }
        return waitingFor.get(what);
    }

    long insert(AEKey what, long amount, Actionable mode) {
        if (what == null || amount <= 0) {
            return 0;
        }

        var waiting = waitingFor.get(what);
        if (waiting <= 0) {
            return 0;
        }

        var inserted = Math.min(waiting, amount);
        if (mode == Actionable.MODULATE) {
            waitingFor.remove(what, inserted);
            waitingFor.removeZeros();
        }
        return inserted;
    }

    boolean isEmpty() {
        return waitingFor.isEmpty();
    }

    void clear() {
        waitingFor.clear();
        waitingFor.removeEmptySubmaps();
    }

    KeyCounter snapshotCounter() {
        var snapshot = new KeyCounter();
        snapshot.addAll(waitingFor);
        return snapshot;
    }

    Map<AEKey, Long> snapshot() {
        var snapshot = new LinkedHashMap<AEKey, Long>();
        for (var entry : waitingFor) {
            if (entry.getLongValue() > 0) {
                snapshot.put(entry.getKey(), entry.getLongValue());
            }
        }
        return snapshot;
    }

    ListTag writeToNBT(HolderLookup.Provider registries) {
        var tag = new ListTag();
        for (var entry : waitingFor) {
            if (entry.getLongValue() > 0) {
                tag.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getLongValue())));
            }
        }
        return tag;
    }

    void readFromNBT(ListTag tag, HolderLookup.Provider registries) {
        clear();
        for (var element : tag) {
            if (element.getId() != Tag.TAG_COMPOUND) {
                continue;
            }
            GenericStack stack;
            try {
                stack = GenericStack.readTag(registries, (net.minecraft.nbt.CompoundTag) element);
            } catch (RuntimeException e) {
                AELog.warn("Failed to read force start entry from NBT.", e);
                continue;
            }
            if (stack != null && stack.amount() > 0) {
                waitingFor.add(stack.what(), stack.amount());
            }
        }
    }
}
