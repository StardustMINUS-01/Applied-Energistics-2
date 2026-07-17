/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.me.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.EmptyingAction;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.TabButton;
import appeng.core.AEConfig;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.core.network.ServerboundPacket;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;

public class PatternEncodingTermScreen<C extends PatternEncodingTermMenu> extends MEStorageScreen<C> {
    private final Map<EncodingMode, EncodingModePanel> modePanels = new EnumMap<>(EncodingMode.class);
    private final Map<EncodingMode, TabButton> modeTabButtons = new EnumMap<>(EncodingMode.class);
    private final EncodePatternButton encodeButton;

    public PatternEncodingTermScreen(C menu, Inventory playerInventory,
            Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        for (var mode : EncodingMode.values()) {
            var panel = switch (mode) {
                case CRAFTING -> new CraftingEncodingPanel(this, widgets);
                case PROCESSING -> new ProcessingEncodingPanel(this, widgets);
                case SMITHING_TABLE -> new SmithingTableEncodingPanel(this, widgets);
                case STONECUTTING -> new StonecuttingEncodingPanel(this, widgets);
            };
            var tabButton = new TabButton(
                    panel.getIcon(),
                    panel.getTabTooltip(),
                    btn -> getMenu().setMode(mode));
            tabButton.setStyle(TabButton.Style.HORIZONTAL);

            var modeIndex = modeTabButtons.size();
            widgets.add("modePanel" + modeIndex, panel);
            widgets.add("modeTabButton" + modeIndex, tabButton);
            modeTabButtons.put(mode, tabButton);
            modePanels.put(mode, panel);
        }

        this.encodeButton = new EncodePatternButton(btn -> {
            if (Screen.hasShiftDown()) {
                menu.encodeAndUpload();
            } else {
                menu.encode();
            }
        });
        widgets.add("encodePattern", encodeButton);

        addToLeftToolbar(new IconButton(btn -> menu.openPatternUploadManagement()) {
            @Override
            protected Icon getIcon() {
                return Icon.ARROW_UP;
            }

            @Override
            public List<Component> getTooltipMessage() {
                return List.of(GuiText.PatternUpload.text());
            }
        });
    }

    private static class EncodePatternButton extends IconButton {
        private static final Component ENCODE_MESSAGE = buildMessage(
                ButtonToolTips.Encode,
                ButtonToolTips.EncodeDescription);
        private static final Component ENCODE_AND_UPLOAD_MESSAGE = buildMessage(
                ButtonToolTips.EncodeAndUpload,
                ButtonToolTips.EncodeAndUploadDescription,
                ButtonToolTips.RecallLastUploadedPatternDescription);

        EncodePatternButton(Button.OnPress onPress) {
            super(onPress);
        }

        @Override
        protected Icon getIcon() {
            return Screen.hasShiftDown() ? Icon.ARROW_UP : Icon.WHITE_ARROW_DOWN;
        }

        @Override
        public List<Component> getTooltipMessage() {
            return Collections.singletonList(Screen.hasShiftDown() ? ENCODE_AND_UPLOAD_MESSAGE : ENCODE_MESSAGE);
        }

        private static Component buildMessage(ButtonToolTips displayName, ButtonToolTips... displayValues) {
            var message = new StringBuilder(displayName.text().getString());
            for (var displayValue : displayValues) {
                message.append('\n').append(displayValue.text().getString());
            }
            return Component.literal(message.toString());
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        for (var mode : EncodingMode.values()) {
            var selected = menu.getMode() == mode;
            modeTabButtons.get(mode).setSelected(selected);
            modePanels.get(mode).setVisible(selected);
        }
    }

    @Override
    public boolean mouseClicked(double xCoord, double yCoord, int btn) {
        return mouseClickedTerminal(toTerminalMouseX(xCoord), toTerminalMouseY(yCoord), btn);
    }

    @Override
    protected boolean mouseClickedTerminal(double xCoord, double yCoord, int btn) {
        if (Screen.hasControlDown() && btn == InputConstants.MOUSE_BUTTON_LEFT && menu.getCarried().isEmpty()) {
            var slot = this.findSlot(xCoord, yCoord);
            var catalystIndex = menu.getProcessingInputSlotIndex(slot);
            if (catalystIndex >= 0 && slot.hasItem()) {
                menu.toggleCatalyst(catalystIndex);
                return true;
            }
        }

        if (Screen.hasShiftDown() && btn == InputConstants.MOUSE_BUTTON_RIGHT
                && encodeButton.isMouseOver(xCoord, yCoord)) {
            menu.recallLastUploadedPattern();
            return true;
        }

        // handler for middle mouse button crafting in survival mode
        if (this.minecraft.options.keyPickItem.matchesMouse(btn)) {
            var slot = this.findSlot(xCoord, yCoord);
            if (menu.canModifyAmountForSlot(slot)) {
                var currentStack = GenericStack.fromItemStack(slot.getItem());
                if (currentStack != null) {
                    var screen = new SetProcessingPatternAmountScreen<>(
                            this,
                            currentStack,
                            newStack -> {
                                ServerboundPacket message = new InventoryActionPacket(
                                        InventoryAction.SET_FILTER, slot.index,
                                        GenericStack.wrapInItemStack(newStack));
                                PacketDistributor.sendToServer(message);
                            });
                    switchToScreen(screen);
                    return true;
                }
            }
        }

        return super.mouseClickedTerminal(xCoord, yCoord, btn);
    }

    /**
     * When in processing mode, show a hint in the tooltip that middle-click will open the amount entry dialog.
     */
    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int x, int y) {
        if (this.menu.getCarried().isEmpty() && menu.canModifyAmountForSlot(this.hoveredSlot)) {
            var itemTooltip = new ArrayList<>(getTooltipFromContainerItem(this.hoveredSlot.getItem()));
            var unwrapped = GenericStack.fromItemStack(this.hoveredSlot.getItem());
            if (unwrapped != null) {
                itemTooltip.add(Tooltips.getAmountTooltip(ButtonToolTips.Amount, unwrapped));
            }
            itemTooltip.add(Tooltips.getSetAmountTooltip());
            drawTooltip(guiGraphics, x, y, itemTooltip);
        } else {
            super.renderTooltip(guiGraphics, x, y);
        }
    }

    @Override
    protected EmptyingAction getEmptyingAction(Slot slot, ItemStack carried) {
        // Since the crafting matrix and output slot are not backed by a config inventory, the default behavior
        // does not work out of the box.
        if (menu.isProcessingPatternSlot(slot)) {
            // See if we should offer the left-/right-click differentiation for setting a different filter
            var emptyingAction = ContainerItemStrategies.getEmptyingAction(carried);
            if (emptyingAction != null) {
                return emptyingAction;
            }
        }

        return super.getEmptyingAction(slot, carried);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot s) {
        super.renderSlot(guiGraphics, s);

        if (menu.isCatalystSlot(s)) {
            var poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(0, 0, 500);
            guiGraphics.drawString(this.font, "C", s.x + 11, s.y - 1, 0xFFFFFF00, false);
            poseStack.popPose();
        }

        if (shouldShowCraftableIndicatorForSlot(s)) {
            var poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(0, 0, 100); // Items are rendered with offset of 100, offset text too.
            StackSizeRenderer.renderSizeLabel(guiGraphics, this.font, s.x - 11, s.y - 11, "+", false);
            poseStack.popPose();
        }
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var lines = super.getTooltipFromContainerItem(stack);

        // Append an indication to the tooltip that the item is craftable
        if (hoveredSlot != null && shouldShowCraftableIndicatorForSlot(hoveredSlot)) {
            lines = new ArrayList<>(lines); // Ensures we're not modifying a cached copy
            lines.add(ButtonToolTips.Craftable.text().withStyle(ChatFormatting.DARK_GRAY));
        }

        return lines;
    }

    private boolean shouldShowCraftableIndicatorForSlot(Slot s) {
        // Mark inputs for patterns for which the grid already has a pattern
        var semantic = menu.getSlotSemantic(s);
        if (semantic == SlotSemantics.CRAFTING_GRID
                || semantic == SlotSemantics.PROCESSING_INPUTS
                || semantic == SlotSemantics.SMITHING_TABLE_ADDITION
                || semantic == SlotSemantics.SMITHING_TABLE_BASE
                || semantic == SlotSemantics.SMITHING_TABLE_TEMPLATE
                || semantic == SlotSemantics.STONECUTTING_INPUT) {
            var slotContent = GenericStack.fromItemStack(s.getItem());
            if (slotContent == null) {
                return false;
            }

            return repo.isCraftable(slotContent.what());
        }
        return false;
    }

    @Override
    public void onClose() {
        if (AEConfig.instance().isClearGridOnClose()) {
            this.getMenu().clear();
        }
        super.onClose();
    }
}
