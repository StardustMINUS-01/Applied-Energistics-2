package appeng.menu.me.items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ISubMenuHost;
import appeng.core.AEConfig;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.helpers.patternprovider.upload.PatternUploadBatchResult;
import appeng.helpers.patternprovider.upload.PatternUploadFavorites;
import appeng.helpers.patternprovider.upload.PatternUploadManagementData;
import appeng.helpers.patternprovider.upload.PatternUploadManagementUploadRequest;
import appeng.helpers.patternprovider.upload.PatternUploadRecallHistory;
import appeng.helpers.patternprovider.upload.PatternUploadRecallService;
import appeng.helpers.patternprovider.upload.PatternUploadRecallStatus;
import appeng.helpers.patternprovider.upload.PatternUploadService;
import appeng.helpers.patternprovider.upload.PatternUploadSource;
import appeng.helpers.patternprovider.upload.PatternUploadSourceKind;
import appeng.helpers.patternprovider.upload.PatternUploadSourceRef;
import appeng.helpers.patternprovider.upload.PatternUploadTargetSettings;
import appeng.menu.AEBaseMenu;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.inv.CarriedItemInventory;
import appeng.util.inv.PlayerInternalInventory;

public class PatternUploadManagementMenu extends AEBaseMenu implements ISubMenu {
    private static final String ACTION_BACK = "back";
    private static final String ACTION_TOGGLE_FAVORITE = "toggleFavorite";
    private static final String ACTION_OPEN_TARGET_SETTINGS = "openTargetSettings";
    private static final String ACTION_UPLOAD = "upload";
    private static final String ACTION_UPLOAD_CARRIED = "uploadCarried";
    private static final String ACTION_RECALL_TO_CURSOR = "recallToCursor";
    private static final String ACTION_RECALL_TO_SOURCES = "recallToSources";

    public static final MenuType<PatternUploadManagementMenu> TYPE = MenuTypeBuilder
            .create(PatternUploadManagementMenu::new, IPatternTerminalMenuHost.class)
            .build("patternuploadmanagement");

    private final IPatternTerminalMenuHost host;
    private final RestrictedInputSlot encodedPatternSlot;

    @GuiSync(1)
    public PatternUploadManagementData data = PatternUploadManagementData.EMPTY;

    public PatternUploadManagementMenu(int id, Inventory playerInventory, IPatternTerminalMenuHost host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;

        this.encodedPatternSlot = new RawEncodedPatternSlot(RestrictedInputSlot.PlacableItemType.ENCODED_PATTERN,
                host.getLogic().getEncodedPatternInv(), 0);
        this.addSlot(this.encodedPatternSlot, SlotSemantics.ENCODED_PATTERN);
        this.encodedPatternSlot.setStackLimit(1);
        this.createPlayerInventorySlots(playerInventory);

        registerClientAction(ACTION_BACK, this::goBack);
        registerClientAction(ACTION_TOGGLE_FAVORITE, String.class, this::toggleFavorite);
        registerClientAction(ACTION_OPEN_TARGET_SETTINGS, String.class, this::openTargetSettings);
        registerClientAction(ACTION_UPLOAD, PatternUploadManagementUploadRequest.class, this::upload);
        registerClientAction(ACTION_UPLOAD_CARRIED, String.class, this::uploadCarried);
        registerClientAction(ACTION_RECALL_TO_CURSOR, String.class, this::recallToCursor);
        registerClientAction(ACTION_RECALL_TO_SOURCES, String.class, this::recallToSources);
    }

    public static void open(ServerPlayer player, MenuHostLocator locator) {
        MenuOpener.open(TYPE, player, locator, true);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && getPlayer() instanceof ServerPlayer player) {
            var grid = getGrid();
            if (grid == null) {
                setValidMenu(false);
                return;
            }

            data = new PatternUploadManagementData(
                    PatternUploadService.findAllTargetGroups(
                            player,
                            grid,
                            PatternUploadRecallHistory.get(player),
                            AEConfig.instance().getPatternUploadRecallHistoryLimit()));
        }

        super.broadcastChanges();
    }

    public void toggleFavorite(String groupName) {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_FAVORITE, groupName);
            return;
        }
        if (getPlayer() instanceof ServerPlayer player && containsGroup(groupName)) {
            PatternUploadFavorites.get(player).toggleFavorite(player, groupName);
            broadcastChanges();
        }
    }

    public void openTargetSettings(String groupName) {
        if (isClientSide()) {
            sendClientAction(ACTION_OPEN_TARGET_SETTINGS, groupName);
            return;
        }
        if (!(getPlayer() instanceof ServerPlayer player) || !containsGroup(groupName)) {
            return;
        }
        var grid = getGrid();
        if (grid != null) {
            PatternUploadTargetSettings.open(player, grid, groupName);
        }
    }

    public void upload(PatternUploadManagementUploadRequest request) {
        if (isClientSide()) {
            sendClientAction(ACTION_UPLOAD, request);
            return;
        }
        if (!(getPlayer() instanceof ServerPlayer player) || !containsGroup(request.targetGroupName())) {
            return;
        }

        var sources = resolveSources(request.sources());
        if (sources.isEmpty()) {
            return;
        }
        uploadSources(player, request.targetGroupName(), sources);
    }

    public void uploadCarried(String groupName) {
        if (isClientSide()) {
            sendClientAction(ACTION_UPLOAD_CARRIED, groupName);
            return;
        }
        if (!(getPlayer() instanceof ServerPlayer player) || !containsGroup(groupName)
                || !PatternDetailsHelper.isEncodedPattern(getCarried()) || getCarried().getCount() != 1) {
            return;
        }
        uploadSources(player, groupName, List.of(PatternUploadSource.singleSlot(new CarriedItemInventory(this), 0)));
    }

    public void recallToCursor(String groupName) {
        if (isClientSide()) {
            sendClientAction(ACTION_RECALL_TO_CURSOR, groupName);
            return;
        }
        if (!getCarried().isEmpty()) {
            return;
        }
        if (!(getPlayer() instanceof ServerPlayer player) || !containsGroup(groupName)) {
            return;
        }
        var grid = getGrid();
        if (grid == null) {
            player.displayClientMessage(PlayerMessages.PatternUploadFailedNoGrid.text(), true);
            return;
        }
        var result = PatternUploadRecallService.recallLastUploadedPatternToCursor(
                player.getUUID(),
                grid,
                new CarriedItemInventory(this),
                PatternUploadRecallHistory.get(player),
                AEConfig.instance().getPatternUploadRecallHistoryLimit(),
                groupName);
        if (result.status() != PatternUploadRecallStatus.NO_RECALLABLE_PATTERN) {
            player.displayClientMessage(PatternEncodingTermMenu.recallStatusMessage(result.status()), true);
        }
        broadcastChanges();
    }

    public void recallToSources(String groupName) {
        if (isClientSide()) {
            sendClientAction(ACTION_RECALL_TO_SOURCES, groupName);
            return;
        }
        recall(groupName, host.getLogic().getEncodedPatternInv(), new PlayerInternalInventory(getPlayerInventory()));
    }

    public void goBack() {
        if (isClientSide()) {
            sendClientAction(ACTION_BACK);
        } else {
            host.returnToMainMenu(getPlayer(), this);
        }
    }

    public ItemStack getSourceStack(PatternUploadSourceRef sourceRef) {
        if (sourceRef == null || sourceRef.kind() == null) {
            return ItemStack.EMPTY;
        }
        if (sourceRef.kind() == PatternUploadSourceKind.PLAYER_INVENTORY) {
            var slot = sourceRef.slot();
            return slot >= 0 && slot < Inventory.INVENTORY_SIZE ? getPlayerInventory().getItem(slot) : ItemStack.EMPTY;
        }
        if (sourceRef.kind() == PatternUploadSourceKind.ENCODED_PATTERN_SLOT && sourceRef.slot() == 0) {
            return encodedPatternSlot.getItem();
        }
        return ItemStack.EMPTY;
    }

    public boolean isEncodedPatternSource(PatternUploadSourceRef sourceRef) {
        return PatternDetailsHelper.isEncodedPattern(getSourceStack(sourceRef));
    }

    @Override
    public ISubMenuHost getHost() {
        return host;
    }

    private void recall(String groupName, InternalInventory primaryDestination, InternalInventory fallbackDestination) {
        if (!(getPlayer() instanceof ServerPlayer player) || !containsGroup(groupName)) {
            return;
        }
        var grid = getGrid();
        if (grid == null) {
            player.displayClientMessage(PlayerMessages.PatternUploadFailedNoGrid.text(), true);
            return;
        }

        var result = PatternUploadRecallService.recallLastUploadedPattern(
                player.getUUID(),
                grid,
                primaryDestination,
                fallbackDestination,
                PatternUploadRecallHistory.get(player),
                AEConfig.instance().getPatternUploadRecallHistoryLimit(),
                groupName);
        if (result.status() != PatternUploadRecallStatus.NO_RECALLABLE_PATTERN) {
            player.displayClientMessage(PatternEncodingTermMenu.recallStatusMessage(result.status()), true);
        }
        broadcastChanges();
    }

    private void uploadSources(ServerPlayer player, String groupName, List<PatternUploadSource> sources) {
        var grid = getGrid();
        if (grid == null) {
            player.displayClientMessage(PlayerMessages.PatternUploadFailedNoGrid.text(), true);
            return;
        }

        var result = PatternUploadService.uploadPatterns(player, grid, sources, groupName);
        for (var uploaded : result.uploadedPatterns()) {
            PatternEncodingTermMenu.recordUploadedPattern(player, uploaded);
        }
        player.displayClientMessage(batchSummary(result), true);
        broadcastChanges();
    }

    private List<PatternUploadSource> resolveSources(List<PatternUploadSourceRef> sourceRefs) {
        if (sourceRefs.isEmpty() || sourceRefs.size() > Inventory.INVENTORY_SIZE + 1) {
            return List.of();
        }

        var seenSources = new HashSet<PatternUploadSourceRef>();
        var result = new ArrayList<PatternUploadSource>(sourceRefs.size());
        for (var sourceRef : sourceRefs) {
            if (sourceRef == null || !seenSources.add(sourceRef)) {
                return List.of();
            }
            var source = resolveSource(sourceRef);
            if (source == null) {
                return List.of();
            }
            result.add(source);
        }
        return result;
    }

    @Nullable
    private PatternUploadSource resolveSource(PatternUploadSourceRef sourceRef) {
        var inventory = resolveInventory(sourceRef);
        return inventory == null ? null : PatternUploadSource.singleSlot(inventory, sourceRef.slot());
    }

    @Nullable
    private InternalInventory resolveInventory(PatternUploadSourceRef sourceRef) {
        if (sourceRef == null || sourceRef.kind() == null) {
            return null;
        }
        if (sourceRef.kind() == PatternUploadSourceKind.PLAYER_INVENTORY) {
            if (sourceRef.slot() < 0 || sourceRef.slot() >= Inventory.INVENTORY_SIZE) {
                return null;
            }
            return new PlayerInternalInventory(getPlayerInventory());
        }
        if (sourceRef.kind() == PatternUploadSourceKind.ENCODED_PATTERN_SLOT && sourceRef.slot() == 0) {
            return host.getLogic().getEncodedPatternInv();
        }
        return null;
    }

    private boolean containsGroup(String groupName) {
        return groupName != null
                && data.targetGroups().groups().stream().anyMatch(group -> group.name().equals(groupName));
    }

    @Nullable
    private IGrid getGrid() {
        if (host instanceof IActionHost actionHost) {
            var node = actionHost.getActionableNode();
            if (node != null && node.isActive()) {
                return node.getGrid();
            }
        }
        return null;
    }

    private static Component batchSummary(PatternUploadBatchResult result) {
        return PlayerMessages.PatternUploadBatchSummary.text(
                result.uploadedCount(),
                result.targetFullCount(),
                result.invalidSourceCount(),
                result.failedCount());
    }

    private static final class RawEncodedPatternSlot extends RestrictedInputSlot {
        private RawEncodedPatternSlot(PlacableItemType valid, InternalInventory inv, int invSlot) {
            super(valid, inv, invSlot);
        }

        @Override
        public ItemStack getDisplayStack() {
            return getItem();
        }
    }
}
