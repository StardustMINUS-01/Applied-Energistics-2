package appeng.menu.me.items;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ISubMenuHost;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.helpers.patternprovider.upload.PatternUploadFavorites;
import appeng.helpers.patternprovider.upload.PatternUploadService;
import appeng.helpers.patternprovider.upload.PatternUploadSource;
import appeng.helpers.patternprovider.upload.PatternUploadStatus;
import appeng.helpers.patternprovider.upload.PatternUploadTargetGroups;
import appeng.helpers.patternprovider.upload.PatternUploadTargetHint;
import appeng.helpers.patternprovider.upload.PatternUploadTargetSettings;
import appeng.menu.AEBaseMenu;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.locator.MenuHostLocator;

public class PatternUploadSelectMenu extends AEBaseMenu implements ISubMenu {
    private static final String ACTION_BACK = "back";
    private static final String ACTION_SELECT_GROUP = "selectGroup";
    private static final String ACTION_TOGGLE_FAVORITE = "toggleFavorite";
    private static final String ACTION_OPEN_TARGET_SETTINGS = "openTargetSettings";

    public static final MenuType<PatternUploadSelectMenu> TYPE = MenuTypeBuilder
            .create(PatternUploadSelectMenu::new, IPatternTerminalMenuHost.class)
            .build("patternuploadselect");

    private final IPatternTerminalMenuHost host;

    @GuiSync(1)
    public PatternUploadTargetGroups targetGroups = PatternUploadTargetGroups.EMPTY;

    public PatternUploadSelectMenu(int id, Inventory ip, IPatternTerminalMenuHost host) {
        super(TYPE, id, ip, host);
        this.host = host;

        registerClientAction(ACTION_BACK, this::goBack);
        registerClientAction(ACTION_SELECT_GROUP, String.class, this::selectGroup);
        registerClientAction(ACTION_TOGGLE_FAVORITE, String.class, this::toggleFavorite);
        registerClientAction(ACTION_OPEN_TARGET_SETTINGS, String.class, this::openTargetSettings);
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
            targetGroups = PatternUploadService.findTargetGroups(player, grid,
                    PatternUploadSource.singleSlot(host.getLogic().getEncodedPatternInv(), 0));
        }

        super.broadcastChanges();
    }

    public void selectGroup(String groupName) {
        if (isClientSide()) {
            sendClientAction(ACTION_SELECT_GROUP, groupName);
            return;
        }

        if (!(getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        var grid = getGrid();
        if (grid == null) {
            player.displayClientMessage(PlayerMessages.PatternUploadFailedNoGrid.text(), true);
            return;
        }

        var result = PatternUploadService.uploadPattern(
                player,
                grid,
                PatternUploadSource.singleSlot(host.getLogic().getEncodedPatternInv(), 0),
                PatternUploadTargetHint.selectedGroup(groupName));

        if (result.status() == PatternUploadStatus.UPLOADED) {
            PatternEncodingTermMenu.recordUploadedPattern(player, result);
            host.returnToMainMenu(player, this);
        } else {
            player.displayClientMessage(PatternEncodingTermMenu.uploadStatusMessage(result.status()), true);
            broadcastChanges();
        }
    }

    public void toggleFavorite(String groupName) {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_FAVORITE, groupName);
            return;
        }

        if (getPlayer() instanceof ServerPlayer player) {
            PatternUploadFavorites.get(player).toggleFavorite(player, groupName);
            broadcastChanges();
        }
    }

    /**
     * Server-owned entry point for the future per-target settings submenu.
     */
    public void openTargetSettings(String groupName) {
        if (isClientSide()) {
            sendClientAction(ACTION_OPEN_TARGET_SETTINGS, groupName);
            return;
        }

        if (!(getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        var grid = getGrid();
        if (grid == null) {
            return;
        }
        var source = PatternUploadSource.singleSlot(host.getLogic().getEncodedPatternInv(), 0);
        var exists = PatternUploadService.findTargetGroups(player, grid, source).groups().stream()
                .anyMatch(group -> group.name().equals(groupName));
        if (exists) {
            PatternUploadTargetSettings.open(player, grid, groupName);
        }
    }

    public void goBack() {
        if (isClientSide()) {
            sendClientAction(ACTION_BACK);
        } else {
            host.returnToMainMenu(getPlayer(), this);
        }
    }

    @Override
    public ISubMenuHost getHost() {
        return host;
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

    @Override
    public void removed(Player player) {
        super.removed(player);
    }
}
