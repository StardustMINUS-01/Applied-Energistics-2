package appeng.helpers.patternprovider.upload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;

public final class PatternUploadService {
    private PatternUploadService() {
    }

    public static PatternUploadResult uploadPattern(ServerPlayer player, PatternEncodingTermMenu menu,
            PatternUploadSource source, PatternUploadTargetHint targetHint) {
        var sourceStack = source.getStack();
        var invalidStatus = validateSource(player, sourceStack);
        if (invalidStatus != null) {
            return result(invalidStatus);
        }

        var grid = getActiveGrid(menu);
        if (grid == null) {
            return result(PatternUploadStatus.NO_GRID);
        }

        return uploadPattern(player, grid, source, targetHint);
    }

    public static PatternUploadResult uploadPattern(ServerPlayer player, IGrid grid,
            PatternUploadSource source, PatternUploadTargetHint targetHint) {
        var sourceStack = source.getStack();
        var invalidStatus = validateSource(player, sourceStack);
        if (invalidStatus != null) {
            return result(invalidStatus);
        }

        var targets = findVisibleTargets(grid);
        if (targets.isEmpty()) {
            return result(PatternUploadStatus.NO_TARGET);
        }

        var selectedTargetId = targetHint.selectedTargetId();
        var selectedGroupName = targetHint.selectedGroupName();
        var matchedSelectedTarget = selectedTargetId == null && selectedGroupName == null;
        var insertableTargets = new ArrayList<InsertableTarget>();
        for (var target : targets) {
            var uploadTarget = PatternUploadTarget.from(target);
            if (selectedTargetId != null && uploadTarget.id() != selectedTargetId.intValue()) {
                continue;
            }
            if (selectedGroupName != null && !uploadTarget.name().equals(selectedGroupName)) {
                continue;
            }
            matchedSelectedTarget = true;

            var inventory = new FilteredInternalInventory(target.getTerminalPatternInventory(),
                    PatternSlotFilter.INSTANCE);
            if (inventory.simulateAdd(sourceStack).isEmpty()) {
                insertableTargets.add(new InsertableTarget(uploadTarget, inventory));
            }
        }

        if (!matchedSelectedTarget) {
            return result(PatternUploadStatus.NO_TARGET);
        }
        if (insertableTargets.isEmpty()) {
            return result(PatternUploadStatus.TARGET_FULL);
        }
        var groupedTargets = groupByName(insertableTargets);
        if (groupedTargets.size() > 1) {
            return PatternUploadResult.of(PatternUploadStatus.MULTIPLE_TARGETS,
                    groupedTargets.values().stream().map(InsertableTarget::target).toList());
        }

        var patternToUpload = sourceStack.copy();
        var selectedTarget = groupedTargets.values().iterator().next();
        if (!selectedTarget.inventory().addItems(patternToUpload).isEmpty()) {
            return result(PatternUploadStatus.INSERT_FAILED);
        }
        if (!source.removeUploadedStack(patternToUpload)) {
            return result(PatternUploadStatus.INSERT_FAILED);
        }

        return PatternUploadResult.uploaded(selectedTarget.target().name(), patternToUpload);
    }

    public static PatternUploadBatchResult uploadPatterns(ServerPlayer player, IGrid grid,
            List<PatternUploadSource> sources, String targetGroupName) {
        var uploadedPatterns = new ArrayList<PatternUploadResult>();
        var targetFullCount = 0;
        var invalidSourceCount = 0;
        var failedCount = 0;

        for (var source : sources) {
            var result = uploadPattern(player, grid, source, PatternUploadTargetHint.selectedGroup(targetGroupName));
            switch (result.status()) {
                case UPLOADED -> uploadedPatterns.add(result);
                case TARGET_FULL -> targetFullCount++;
                case INVALID_SOURCE, INVALID_PATTERN, INVALID_PATTERN_STACK_SIZE -> invalidSourceCount++;
                default -> failedCount++;
            }
        }

        return new PatternUploadBatchResult(uploadedPatterns, targetFullCount, invalidSourceCount, failedCount);
    }

    public static PatternUploadTargetGroups findTargetGroups(ServerPlayer player, IGrid grid,
            PatternUploadSource source) {
        if (validateSource(player, source.getStack()) != null) {
            return PatternUploadTargetGroups.EMPTY;
        }

        var favorites = PatternUploadFavorites.get(player);
        var targets = findVisibleTargets(grid);
        var groups = new LinkedHashMap<String, TargetGroupBuilder>();
        for (var target : targets) {
            var uploadTarget = PatternUploadTarget.from(target);
            var builder = groups.computeIfAbsent(uploadTarget.name(), ignored -> new TargetGroupBuilder(uploadTarget));
            builder.add(uploadTarget);

            var inventory = new FilteredInternalInventory(target.getTerminalPatternInventory(),
                    PatternSlotFilter.INSTANCE);
            if (inventory.simulateAdd(source.getStack()).isEmpty()) {
                builder.insertable = true;
            }
        }

        var result = groups.values()
                .stream()
                .filter(group -> group.insertable)
                .map(group -> group.build(favorites.isFavorite(player, group.name()), ItemStack.EMPTY))
                .sorted(Comparator.comparing(PatternUploadTargetGroup::favorite).reversed()
                        .thenComparing(PatternUploadTargetGroup::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new PatternUploadTargetGroups(result);
    }

    public static PatternUploadTargetGroups findAllTargetGroups(ServerPlayer player, IGrid grid,
            PatternUploadRecallHistory recallHistory, int historyLimit) {
        var favorites = PatternUploadFavorites.get(player);
        var groups = new LinkedHashMap<String, TargetGroupBuilder>();
        for (var target : findVisibleTargets(grid)) {
            var uploadTarget = PatternUploadTarget.from(target);
            groups.computeIfAbsent(uploadTarget.name(), ignored -> new TargetGroupBuilder(uploadTarget))
                    .add(uploadTarget);
        }

        var result = groups.values()
                .stream()
                .map(group -> group.build(
                        favorites.isFavorite(player, group.name()),
                        recallHistory.getMostRecentPatternForGroup(player.getUUID(), group.name(), historyLimit)))
                .sorted(Comparator.comparing(PatternUploadTargetGroup::favorite).reversed()
                        .thenComparing(PatternUploadTargetGroup::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new PatternUploadTargetGroups(result);
    }

    @Nullable
    private static PatternUploadStatus validateSource(ServerPlayer player, ItemStack sourceStack) {
        if (sourceStack.isEmpty()) {
            return PatternUploadStatus.INVALID_SOURCE;
        }
        if (!PatternDetailsHelper.isEncodedPattern(sourceStack)) {
            return PatternUploadStatus.INVALID_PATTERN;
        }
        if (sourceStack.getCount() != 1) {
            return PatternUploadStatus.INVALID_PATTERN_STACK_SIZE;
        }
        if (PatternDetailsHelper.decodePattern(sourceStack, player.level()) == null) {
            return PatternUploadStatus.INVALID_PATTERN;
        }
        return null;
    }

    @Nullable
    private static IGrid getActiveGrid(PatternEncodingTermMenu menu) {
        var gridNode = menu.getGridNode();
        if (gridNode == null || !gridNode.isActive()) {
            return null;
        }
        return gridNode.getGrid();
    }

    static List<PatternContainer> findVisibleTargets(IGrid grid) {
        var targets = new ArrayList<PatternContainer>();
        for (var machineClass : grid.getMachineClasses()) {
            if (!PatternContainer.class.isAssignableFrom(machineClass)) {
                continue;
            }

            visitContainers(grid, machineClass.asSubclass(PatternContainer.class), targets);
        }
        return targets;
    }

    private static <T extends PatternContainer> void visitContainers(IGrid grid, Class<T> machineClass,
            List<PatternContainer> targets) {
        for (var container : grid.getActiveMachines(machineClass)) {
            if (container.isVisibleInTerminal() && container.getGrid() == grid) {
                targets.add(container);
            }
        }
    }

    private static PatternUploadResult result(PatternUploadStatus status) {
        return PatternUploadResult.of(status);
    }

    private static Map<String, InsertableTarget> groupByName(List<InsertableTarget> targets) {
        var result = new LinkedHashMap<String, InsertableTarget>();
        for (var target : targets) {
            result.putIfAbsent(target.target().name(), target);
        }
        return result;
    }

    private record InsertableTarget(PatternUploadTarget target, InternalInventory inventory) {
    }

    private static final class TargetGroupBuilder {
        private final PatternUploadTarget firstTarget;
        private int usedSlots;
        private int totalSlots;
        private boolean insertable;

        private TargetGroupBuilder(PatternUploadTarget firstTarget) {
            this.firstTarget = firstTarget;
        }

        private String name() {
            return firstTarget.name();
        }

        private void add(PatternUploadTarget target) {
            usedSlots += target.totalSlots() - target.emptySlots();
            totalSlots += target.totalSlots();
        }

        private PatternUploadTargetGroup build(boolean favorite, ItemStack recentUploadedPattern) {
            return new PatternUploadTargetGroup(name(), firstTarget.group(), usedSlots, totalSlots, favorite,
                    insertable || usedSlots < totalSlots, recentUploadedPattern);
        }
    }

    private enum PatternSlotFilter implements IAEItemFilter {
        INSTANCE;

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return !stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack);
        }
    }
}
