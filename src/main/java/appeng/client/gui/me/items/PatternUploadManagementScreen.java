package appeng.client.gui.me.items;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.api.client.AEKeyRendering;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.AESubScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.localization.GuiText;
import appeng.helpers.patternprovider.upload.PatternUploadManagementUploadRequest;
import appeng.helpers.patternprovider.upload.PatternUploadSourceRef;
import appeng.helpers.patternprovider.upload.PatternUploadTargetGroup;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternUploadManagementMenu;

public class PatternUploadManagementScreen extends AEBaseScreen<PatternUploadManagementMenu> {
    private static final int GROUP_X = 9;
    private static final int GROUP_Y = 19;
    private static final int GROUP_COLUMNS = 1;
    private static final int GROUP_ROWS = 7;
    private static final int GROUP_WIDTH = 206;
    private static final int GROUP_HEIGHT = 20;
    private static final int GROUP_GAP = 0;
    private static final int SCROLLBAR_X = 218;
    private static final int SCROLLBAR_Y = 19;
    private static final int SCROLLBAR_HEIGHT = GROUP_ROWS * GROUP_HEIGHT;
    private static final int INVENTORY_PANEL_X = 9;
    private static final int INVENTORY_PANEL_Y = 167;
    private static final int INVENTORY_PANEL_SHORT_WIDTH = 162;
    private static final int INVENTORY_PANEL_FULL_WIDTH = 180;
    private static final int INVENTORY_PANEL_TOP_HEIGHT = 54;
    private static final int INVENTORY_PANEL_HEIGHT = 72;
    private static final int INVENTORY_PANEL_BORDER = 0xfff2f2f2;
    private static final int INVENTORY_PANEL_FILL = 0xffcbccd4;
    private static final int MAIN_BUTTON_X = 18;
    private static final int MAIN_BUTTON_WIDTH = 152;
    private static final int RECENT_SLOT_X = 170;
    private static final int INPUT_SLOT_X = 188;
    private static final int SLOT_SIZE = 18;
    private static final int RECESSED_PANEL_FILL = 0xff9a9fb4;
    private static final int RECESSED_PANEL_BORDER = 0xff696d88;
    private static final int FAVORITE_MARKER_COLOR = 0xff55ff55;
    private static final int MULTI_SELECT_UNSELECTED_OVERLAY = 0x55000000;
    private static final int MULTI_SELECT_SELECTED_OVERLAY = 0x5540ff80;

    private final Scrollbar scrollbar;
    private final AETextField searchField;
    private final LinkedHashSet<PatternUploadSourceRef> selectedSources = new LinkedHashSet<>();
    private final LinkedHashSet<PatternUploadSourceRef> dragVisitedSources = new LinkedHashSet<>();
    private List<PatternUploadTargetGroup> filteredGroups = List.of();
    private boolean multiSelect;

    public PatternUploadManagementScreen(PatternUploadManagementMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);

        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        searchField = widgets.addTextField("search");
        searchField.setPlaceholder(GuiText.SearchPlaceholder.text());
        searchField.setTooltipMessage(List.of(GuiText.SearchTooltip.text()));
        AESubScreen.addBackButton(menu, "back", widgets);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        setTextContent(TEXT_ID_DIALOG_TITLE, GuiText.PatternUpload.text());

        filteredGroups = getFilteredGroups();
        scrollbar.setRange(0, Math.max(0, (filteredGroups.size() + GROUP_COLUMNS - 1) / GROUP_COLUMNS - GROUP_ROWS),
                1);
        selectedSources.removeIf(source -> !getMenu().isEncodedPatternSource(source));
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var relativeMouseX = mouseX - getGuiLeft();
        var relativeMouseY = mouseY - getGuiTop();
        renderTargetGroups(guiGraphics, relativeMouseX, relativeMouseY);
        renderSourceOverlays(guiGraphics, relativeMouseX, relativeMouseY);
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        renderInventoryPanel(guiGraphics, offsetX + INVENTORY_PANEL_X, offsetY + INVENTORY_PANEL_Y);
        PatternUploadScrollbarRenderer.renderTrack(guiGraphics, offsetX + SCROLLBAR_X, offsetY + SCROLLBAR_Y,
                SCROLLBAR_HEIGHT);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (isSourceSlot(slot)) {
            Icon.SLOT_BACKGROUND.getBlitter().dest(slot.x - 1, slot.y - 1).blit(guiGraphics);
        }
        super.renderSlot(guiGraphics, slot);
    }

    @Override
    protected boolean mouseClickedTerminal(double xCoord, double yCoord, int button) {
        var mouseX = (int) xCoord - getGuiLeft();
        var mouseY = (int) yCoord - getGuiTop();

        var source = getSourceAt(mouseX, mouseY);
        if (source != null) {
            if (!multiSelect || !getMenu().isEncodedPatternSource(source)) {
                return super.mouseClickedTerminal(xCoord, yCoord, button);
            }
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                toggleSelectedSource(source);
                // A drag starts with a click. Mark its origin so the first drag event does not toggle it again.
                dragVisitedSources.add(source);
            }
            return true;
        }

        var group = getGroupAt(mouseX, mouseY);
        if (group != null) {
            var bounds = getGroupBounds(filteredGroups.indexOf(group));
            var localX = mouseX - bounds.x();
            if (localX < MAIN_BUTTON_X && button == InputConstants.MOUSE_BUTTON_LEFT) {
                getMenu().toggleFavorite(group.name());
                playClickSound();
            } else if (localX < MAIN_BUTTON_X && button == InputConstants.MOUSE_BUTTON_RIGHT) {
                getMenu().openTargetSettings(group.name());
                playClickSound();
            } else if (localX >= RECENT_SLOT_X && localX < INPUT_SLOT_X) {
                if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                    if (Screen.hasShiftDown()) {
                        getMenu().recallToSources(group.name());
                    } else {
                        getMenu().recallToCursor(group.name());
                    }
                }
            } else if (isUploadActionSlot(localX) && button == InputConstants.MOUSE_BUTTON_LEFT
                    && group.canUpload()) {
                uploadTo(group.name());
                playClickSound();
            }
            return true;
        }

        return super.mouseClickedTerminal(xCoord, yCoord, button);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int x, int y) {
        var group = getGroupAt(x - getGuiLeft(), y - getGuiTop());
        if (group != null) {
            var bounds = getGroupBounds(filteredGroups.indexOf(group));
            if (x - getGuiLeft() < bounds.x() + MAIN_BUTTON_X) {
                drawTooltip(guiGraphics, x, y, List.of(
                        GuiText.PatternUploadFavoriteLeftClick.text(),
                        GuiText.PatternUploadFavoriteRightClick.text()));
                return;
            }
        }
        super.renderTooltip(guiGraphics, x, y);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (multiSelect && button == InputConstants.MOUSE_BUTTON_LEFT) {
            var source = getSourceAt((int) mouseX - getGuiLeft(), (int) mouseY - getGuiTop());
            if (source != null) {
                if (getMenu().isEncodedPatternSource(source)) {
                    if (dragVisitedSources.add(source)) {
                        toggleSelectedSource(source);
                    }
                    return true;
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragVisitedSources.clear();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_TAB && !searchField.isFocused()) {
            multiSelect = !multiSelect;
            selectedSources.clear();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void uploadTo(String groupName) {
        if (!multiSelect) {
            if (!getMenu().getCarried().isEmpty()) {
                getMenu().uploadCarried(groupName);
            }
            return;
        }
        var sources = List.copyOf(selectedSources);
        if (sources.isEmpty()) {
            return;
        }
        getMenu().upload(new PatternUploadManagementUploadRequest(sources, groupName));
        selectedSources.clear();
    }

    private void toggleSelectedSource(PatternUploadSourceRef source) {
        if (!selectedSources.remove(source)) {
            selectedSources.add(source);
        }
    }

    private void renderTargetGroups(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        var textColor = getStyle().getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        var mutedTextColor = getStyle().getColor(PaletteColor.MUTED_TEXT_COLOR).toARGB();
        var firstRow = scrollbar.getCurrentScroll();
        renderRecessedTargetPanel(guiGraphics);
        for (int row = 0; row < GROUP_ROWS; row++) {
            for (int column = 0; column < GROUP_COLUMNS; column++) {
                var index = (firstRow + row) * GROUP_COLUMNS + column;
                if (index >= filteredGroups.size()) {
                    return;
                }
                var group = filteredGroups.get(index);
                var bounds = getGroupBounds(index);
                var hovered = isInside(mouseX, mouseY, bounds.x(), bounds.y(), GROUP_WIDTH, GROUP_HEIGHT);
                var iconHovered = hovered && mouseX < bounds.x() + MAIN_BUTTON_X;
                var mainHovered = hovered && mouseX >= bounds.x() + MAIN_BUTTON_X
                        && mouseX < bounds.x() + RECENT_SLOT_X;
                var recentSlotHovered = hovered && mouseX >= bounds.x() + RECENT_SLOT_X
                        && mouseX < bounds.x() + INPUT_SLOT_X;
                var inputSlotHovered = hovered && mouseX >= bounds.x() + INPUT_SLOT_X;

                if (iconHovered) {
                    guiGraphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.x() + MAIN_BUTTON_X - 1,
                            bounds.y() + GROUP_HEIGHT - 1, 0x55ffffff);
                }

                PatternUploadButtonRenderer.render(guiGraphics, PatternUploadButtonRenderer.Size.MANAGEMENT_MAIN,
                        group.canUpload(), mainHovered, bounds.x() + MAIN_BUTTON_X, bounds.y());

                if (group.displayGroup().icon() != null) {
                    AEKeyRendering.drawInGui(Minecraft.getInstance(), guiGraphics, bounds.x() + 1, bounds.y() + 2,
                            group.displayGroup().icon());
                }
                if (group.favorite()) {
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(0, 0, 200);
                    guiGraphics.drawString(font, "F", bounds.x() + 11, bounds.y() + 1, FAVORITE_MARKER_COLOR, true);
                    guiGraphics.pose().popPose();
                }
                Icon.SLOT_BACKGROUND.getBlitter().dest(bounds.x() + RECENT_SLOT_X + 1, bounds.y() + 2)
                        .blit(guiGraphics);
                Icon.BACKGROUND_ENCODED_PATTERN.getBlitter().dest(bounds.x() + INPUT_SLOT_X + 1, bounds.y() + 2)
                        .blit(guiGraphics);
                if (!group.recentUploadedPattern().isEmpty()) {
                    guiGraphics.renderItem(group.recentUploadedPattern(), bounds.x() + RECENT_SLOT_X + 1,
                            bounds.y() + 2);
                }
                if (recentSlotHovered) {
                    guiGraphics.fill(bounds.x() + RECENT_SLOT_X + 1, bounds.y() + 2,
                            bounds.x() + INPUT_SLOT_X - 1, bounds.y() + GROUP_HEIGHT - 2, 0x55ffffff);
                }
                if (inputSlotHovered) {
                    guiGraphics.fill(bounds.x() + INPUT_SLOT_X + 1, bounds.y() + 2,
                            bounds.x() + GROUP_WIDTH - 1, bounds.y() + GROUP_HEIGHT - 2, 0x55ffffff);
                }

                var textLeft = bounds.x() + MAIN_BUTTON_X + 4;
                var capacity = group.usedSlots() + "/" + group.totalSlots();
                var capacityWidth = font.width(capacity);
                var visibleName = trimToWidth(group.name(), MAIN_BUTTON_WIDTH - 8 - capacityWidth - 3);
                guiGraphics.drawString(font, visibleName, textLeft, bounds.y() + 6, textColor, false);
                guiGraphics.drawString(font, capacity, textLeft + font.width(visibleName) + 3, bounds.y() + 6,
                        group.canUpload() ? mutedTextColor : ChatFormatting.RED.getColor(), false);
            }
        }
    }

    private void renderRecessedTargetPanel(GuiGraphics guiGraphics) {
        var panelHeight = GROUP_ROWS * GROUP_HEIGHT;
        guiGraphics.fill(GROUP_X, GROUP_Y, GROUP_X + GROUP_WIDTH, GROUP_Y + panelHeight, RECESSED_PANEL_FILL);
        guiGraphics.fill(GROUP_X, GROUP_Y, GROUP_X + GROUP_WIDTH, GROUP_Y + 1, RECESSED_PANEL_BORDER);
        guiGraphics.fill(GROUP_X, GROUP_Y + panelHeight - 1, GROUP_X + GROUP_WIDTH, GROUP_Y + panelHeight,
                RECESSED_PANEL_BORDER);
        guiGraphics.fill(GROUP_X, GROUP_Y, GROUP_X + 1, GROUP_Y + panelHeight, RECESSED_PANEL_BORDER);
        guiGraphics.fill(GROUP_X + GROUP_WIDTH - 1, GROUP_Y, GROUP_X + GROUP_WIDTH, GROUP_Y + panelHeight,
                RECESSED_PANEL_BORDER);
        for (int row = 1; row < GROUP_ROWS; row++) {
            var y = GROUP_Y + row * GROUP_HEIGHT;
            guiGraphics.fill(GROUP_X, y, GROUP_X + GROUP_WIDTH, y + 1, RECESSED_PANEL_BORDER);
        }
        for (int row = 0; row < GROUP_ROWS; row++) {
            var y = GROUP_Y + row * GROUP_HEIGHT;
            drawPanelSeparator(guiGraphics, GROUP_X + MAIN_BUTTON_X, y, y + GROUP_HEIGHT);
            drawPanelSeparator(guiGraphics, GROUP_X + RECENT_SLOT_X, y, y + GROUP_HEIGHT);
            drawPanelSeparator(guiGraphics, GROUP_X + INPUT_SLOT_X, y, y + GROUP_HEIGHT);
        }
    }

    private static void renderInventoryPanel(GuiGraphics guiGraphics, int x, int y) {
        // The first three rows contain nine slots, while the fourth extends by one encoded-pattern slot.
        guiGraphics.fill(x, y, x + INVENTORY_PANEL_SHORT_WIDTH, y + INVENTORY_PANEL_TOP_HEIGHT,
                INVENTORY_PANEL_FILL);
        guiGraphics.fill(x, y + INVENTORY_PANEL_TOP_HEIGHT, x + INVENTORY_PANEL_FULL_WIDTH,
                y + INVENTORY_PANEL_HEIGHT, INVENTORY_PANEL_FILL);

        guiGraphics.fill(x, y, x + INVENTORY_PANEL_SHORT_WIDTH, y + 1, INVENTORY_PANEL_BORDER);
        guiGraphics.fill(x, y, x + 1, y + INVENTORY_PANEL_HEIGHT, INVENTORY_PANEL_BORDER);
        guiGraphics.fill(x + INVENTORY_PANEL_SHORT_WIDTH - 1, y, x + INVENTORY_PANEL_SHORT_WIDTH,
                y + INVENTORY_PANEL_TOP_HEIGHT + 1, INVENTORY_PANEL_BORDER);
        guiGraphics.fill(x + INVENTORY_PANEL_SHORT_WIDTH, y + INVENTORY_PANEL_TOP_HEIGHT,
                x + INVENTORY_PANEL_FULL_WIDTH, y + INVENTORY_PANEL_TOP_HEIGHT + 1, INVENTORY_PANEL_BORDER);
        guiGraphics.fill(x + INVENTORY_PANEL_FULL_WIDTH - 1, y + INVENTORY_PANEL_TOP_HEIGHT,
                x + INVENTORY_PANEL_FULL_WIDTH, y + INVENTORY_PANEL_HEIGHT, INVENTORY_PANEL_BORDER);
        guiGraphics.fill(x, y + INVENTORY_PANEL_HEIGHT - 1, x + INVENTORY_PANEL_FULL_WIDTH,
                y + INVENTORY_PANEL_HEIGHT, INVENTORY_PANEL_BORDER);
    }

    private static void drawPanelSeparator(GuiGraphics guiGraphics, int x, int top, int bottom) {
        guiGraphics.fill(x, top, x + 1, bottom, RECESSED_PANEL_BORDER);
    }

    private void renderSourceOverlays(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (var sourceSlot : getSourceSlots()) {
            var source = sourceSlot.source();
            var slot = sourceSlot.slot();
            var encodedPattern = getMenu().isEncodedPatternSource(source);
            if (multiSelect && encodedPattern) {
                var overlay = getMultiSelectOverlay(selectedSources.contains(source));
                guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, overlay);
            }
        }
    }

    private List<PatternUploadTargetGroup> getFilteredGroups() {
        var searchTerm = searchField.getValue().trim().toLowerCase(Locale.ROOT);
        if (searchTerm.isEmpty()) {
            return getMenu().data.targetGroups().groups();
        }
        var result = new ArrayList<PatternUploadTargetGroup>();
        for (var group : getMenu().data.targetGroups().groups()) {
            if (group.name().toLowerCase(Locale.ROOT).contains(searchTerm)) {
                result.add(group);
            }
        }
        return result;
    }

    private GroupBounds getGroupBounds(int index) {
        var row = index / GROUP_COLUMNS - scrollbar.getCurrentScroll();
        var column = index % GROUP_COLUMNS;
        return new GroupBounds(GROUP_X + column * (GROUP_WIDTH + GROUP_GAP),
                GROUP_Y + row * (GROUP_HEIGHT + GROUP_GAP));
    }

    private PatternUploadTargetGroup getGroupAt(int mouseX, int mouseY) {
        for (int index = 0; index < filteredGroups.size(); index++) {
            var bounds = getGroupBounds(index);
            if (bounds.y() >= GROUP_Y && bounds.y() < GROUP_Y + GROUP_ROWS * (GROUP_HEIGHT + GROUP_GAP)
                    && isInside(mouseX, mouseY, bounds.x(), bounds.y(), GROUP_WIDTH, GROUP_HEIGHT)) {
                return filteredGroups.get(index);
            }
        }
        return null;
    }

    private PatternUploadSourceRef getSourceAt(int mouseX, int mouseY) {
        for (var sourceSlot : getSourceSlots()) {
            var slot = sourceSlot.slot();
            if (isInside(mouseX, mouseY, slot.x, slot.y, 16, 16)) {
                return sourceSlot.source();
            }
        }
        return null;
    }

    private boolean isSourceSlot(Slot slot) {
        return getSourceSlots().stream().anyMatch(sourceSlot -> sourceSlot.slot() == slot);
    }

    private List<SourceSlot> getSourceSlots() {
        var sourceSlots = new ArrayList<SourceSlot>(Inventory.INVENTORY_SIZE + 1);
        var playerInventorySlots = getMenu().getSlots(SlotSemantics.PLAYER_INVENTORY);
        for (int i = 0; i < playerInventorySlots.size(); i++) {
            sourceSlots.add(new SourceSlot(PatternUploadSourceRef.playerInventory(i + Inventory.getSelectionSize()),
                    playerInventorySlots.get(i)));
        }
        var hotbarSlots = getMenu().getSlots(SlotSemantics.PLAYER_HOTBAR);
        for (int i = 0; i < hotbarSlots.size(); i++) {
            sourceSlots.add(new SourceSlot(PatternUploadSourceRef.playerInventory(i), hotbarSlots.get(i)));
        }
        var encodedPatternSlots = getMenu().getSlots(SlotSemantics.ENCODED_PATTERN);
        if (!encodedPatternSlots.isEmpty()) {
            sourceSlots
                    .add(new SourceSlot(PatternUploadSourceRef.encodedPatternSlot(), encodedPatternSlots.getFirst()));
        }
        return sourceSlots;
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    static int getMultiSelectOverlay(boolean selected) {
        return selected ? MULTI_SELECT_SELECTED_OVERLAY : MULTI_SELECT_UNSELECTED_OVERLAY;
    }

    static boolean isUploadActionSlot(int localX) {
        return localX >= MAIN_BUTTON_X && localX < RECENT_SLOT_X
                || localX >= INPUT_SLOT_X && localX < INPUT_SLOT_X + SLOT_SIZE;
    }

    private static String trimToWidth(String text, int width) {
        var font = Minecraft.getInstance().font;
        return font.width(text) <= width ? text
                : font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private record GroupBounds(int x, int y) {
    }

    private record SourceSlot(PatternUploadSourceRef source, Slot slot) {
    }
}
