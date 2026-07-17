package appeng.client.gui.me.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.client.AEKeyRendering;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.AESubScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.localization.GuiText;
import appeng.helpers.patternprovider.upload.PatternUploadTargetGroup;
import appeng.menu.me.items.PatternUploadSelectMenu;

public class PatternUploadSelectScreen extends AEBaseScreen<PatternUploadSelectMenu> {
    private static final int TABLE_X = 9;
    private static final int TABLE_Y = 19;
    private static final int COLS = 1;
    private static final int ROWS = 9;
    private static final int CELL_WIDTH = 204;
    private static final int CELL_HEIGHT = 20;
    private static final int CELL_GAP = 0;
    private static final int TABLE_BACKGROUND_WIDTH = 204;
    private static final int SCROLLBAR_X = 218;
    private static final int RECESSED_PANEL_FILL = 0xff9a9fb4;
    private static final int RECESSED_PANEL_BORDER = 0xff696d88;
    private static final int FAVORITE_BUTTON_WIDTH = 18;
    private static final int FAVORITE_MARKER_COLOR = 0xff55ff55;

    private final Scrollbar scrollbar;
    private final AETextField searchField;
    private List<PatternUploadTargetGroup> filteredGroups = List.of();

    public PatternUploadSelectScreen(PatternUploadSelectMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        AESubScreen.addBackButton(menu, "back", widgets);

        this.searchField = widgets.addTextField("search");
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.text());
        this.searchField.setTooltipMessage(List.of(GuiText.SearchTooltip.text()));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        setTextContent(TEXT_ID_DIALOG_TITLE, GuiText.PatternUpload.text());

        filteredGroups = getFilteredGroups();
        scrollbar.setRange(0, getScrollableRows(filteredGroups.size()), 1);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        renderTargetGroups(guiGraphics, mouseX - getGuiLeft(), mouseY - getGuiTop());
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        PatternUploadScrollbarRenderer.renderTrack(guiGraphics, offsetX + SCROLLBAR_X, offsetY + TABLE_Y,
                ROWS * CELL_HEIGHT);
    }

    @Override
    protected boolean mouseClickedTerminal(double xCoord, double yCoord, int btn) {
        var mouseX = (int) xCoord - getGuiLeft();
        var mouseY = (int) yCoord - getGuiTop();
        var index = getTargetIndexAt(mouseX, mouseY);
        if (index != -1 && index < filteredGroups.size()) {
            var group = filteredGroups.get(index);
            var x = TABLE_X + (index % COLS) * (CELL_WIDTH + CELL_GAP);
            if (btn == InputConstants.MOUSE_BUTTON_LEFT && mouseX < x + FAVORITE_BUTTON_WIDTH) {
                menu.toggleFavorite(group.name());
                playClickSound();
                return true;
            }
            if (btn == InputConstants.MOUSE_BUTTON_RIGHT && mouseX < x + FAVORITE_BUTTON_WIDTH) {
                menu.openTargetSettings(group.name());
                playClickSound();
                return true;
            }
            if (btn == InputConstants.MOUSE_BUTTON_LEFT) {
                menu.selectGroup(group.name());
                playClickSound();
                return true;
            }
        }

        return super.mouseClickedTerminal(xCoord, yCoord, btn);
    }

    private void renderTargetGroups(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderRecessedTargetPanel(guiGraphics);

        var font = Minecraft.getInstance().font;
        var textColor = getStyle().getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        var firstVisibleRow = scrollbar.getCurrentScroll();

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                var index = (firstVisibleRow + row) * COLS + col;
                if (index >= filteredGroups.size()) {
                    return;
                }

                var group = filteredGroups.get(index);
                var x = TABLE_X + col * (CELL_WIDTH + CELL_GAP);
                var y = TABLE_Y + row * (CELL_HEIGHT + CELL_GAP);
                var hovered = isMouseOverCell(mouseX, mouseY, x, y);
                var iconHovered = hovered && mouseX < x + FAVORITE_BUTTON_WIDTH;

                if (iconHovered) {
                    guiGraphics.fill(x + 1, y + 1, x + FAVORITE_BUTTON_WIDTH - 1, y + CELL_HEIGHT - 1,
                            0x55ffffff);
                }

                PatternUploadButtonRenderer.render(guiGraphics, PatternUploadButtonRenderer.Size.SELECT_MAIN, true,
                        hovered && !iconHovered, x + FAVORITE_BUTTON_WIDTH, y);

                if (group.displayGroup().icon() != null) {
                    AEKeyRendering.drawInGui(Minecraft.getInstance(), guiGraphics, x + 1, y + 2,
                            group.displayGroup().icon());
                }

                if (group.favorite()) {
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(0, 0, 200);
                    guiGraphics.drawString(font, "F", x + 11, y + 1, FAVORITE_MARKER_COLOR, true);
                    guiGraphics.pose().popPose();
                }

                var name = Component.literal(group.name());
                var capacity = Component.literal(group.usedSlots() + "/" + group.totalSlots());
                var textLeft = x + 22;
                var capacityColor = group.canUpload() ? textColor : 0xffff5555;
                var capacityWidth = font.width(capacity);
                var visibleName = trimToWidth(name, CELL_WIDTH - 26 - capacityWidth - 3);
                guiGraphics.drawString(font, visibleName, textLeft, y + 6, textColor, false);
                guiGraphics.drawString(font, capacity, textLeft + font.width(visibleName) + 3, y + 6, capacityColor,
                        false);
            }
        }
    }

    private Component trimToWidth(Component component, int width) {
        var font = Minecraft.getInstance().font;
        var text = component.getString();
        if (font.width(text) <= width) {
            return component;
        }
        return Component.literal(font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...");
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int x, int y) {
        var relativeX = x - getGuiLeft();
        var relativeY = y - getGuiTop();
        var index = getTargetIndexAt(relativeX, relativeY);
        if (index != -1 && index < filteredGroups.size()) {
            var cellX = TABLE_X + (index % COLS) * (CELL_WIDTH + CELL_GAP);
            if (relativeX < cellX + FAVORITE_BUTTON_WIDTH) {
                drawTooltip(guiGraphics, x, y, List.of(
                        GuiText.PatternUploadFavoriteLeftClick.text(),
                        GuiText.PatternUploadFavoriteRightClick.text()));
                return;
            }
        }
        super.renderTooltip(guiGraphics, x, y);
    }

    private static void renderRecessedTargetPanel(GuiGraphics guiGraphics) {
        var panelHeight = ROWS * CELL_HEIGHT;
        guiGraphics.fill(TABLE_X, TABLE_Y, TABLE_X + TABLE_BACKGROUND_WIDTH, TABLE_Y + panelHeight,
                RECESSED_PANEL_FILL);
        guiGraphics.fill(TABLE_X, TABLE_Y, TABLE_X + TABLE_BACKGROUND_WIDTH, TABLE_Y + 1, RECESSED_PANEL_BORDER);
        guiGraphics.fill(TABLE_X, TABLE_Y + panelHeight - 1, TABLE_X + TABLE_BACKGROUND_WIDTH,
                TABLE_Y + panelHeight, RECESSED_PANEL_BORDER);
        guiGraphics.fill(TABLE_X, TABLE_Y, TABLE_X + 1, TABLE_Y + panelHeight, RECESSED_PANEL_BORDER);
        guiGraphics.fill(TABLE_X + TABLE_BACKGROUND_WIDTH - 1, TABLE_Y, TABLE_X + TABLE_BACKGROUND_WIDTH,
                TABLE_Y + panelHeight, RECESSED_PANEL_BORDER);
        for (int row = 1; row < ROWS; row++) {
            var y = TABLE_Y + row * CELL_HEIGHT;
            guiGraphics.fill(TABLE_X, y, TABLE_X + TABLE_BACKGROUND_WIDTH, y + 1, RECESSED_PANEL_BORDER);
        }
        guiGraphics.fill(TABLE_X + FAVORITE_BUTTON_WIDTH, TABLE_Y, TABLE_X + FAVORITE_BUTTON_WIDTH,
                TABLE_Y + panelHeight, RECESSED_PANEL_BORDER);
    }

    private int getTargetIndexAt(int mouseX, int mouseY) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                var x = TABLE_X + col * (CELL_WIDTH + CELL_GAP);
                var y = TABLE_Y + row * (CELL_HEIGHT + CELL_GAP);
                if (isMouseOverCell(mouseX, mouseY, x, y)) {
                    return (scrollbar.getCurrentScroll() + row) * COLS + col;
                }
            }
        }
        return -1;
    }

    private boolean isMouseOverCell(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + CELL_WIDTH && mouseY >= y && mouseY < y + CELL_HEIGHT;
    }

    private List<PatternUploadTargetGroup> getFilteredGroups() {
        var searchTerm = searchField.getValue().trim().toLowerCase(Locale.ROOT);
        if (searchTerm.isEmpty()) {
            return menu.targetGroups.groups();
        }

        var result = new ArrayList<PatternUploadTargetGroup>();
        for (var group : menu.targetGroups.groups()) {
            if (group.name().toLowerCase(Locale.ROOT).contains(searchTerm)) {
                result.add(group);
            }
        }
        return result;
    }

    private int getScrollableRows(int size) {
        return Math.max(0, (size + COLS - 1) / COLS - ROWS);
    }

    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
