package appeng.helpers.patternprovider.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.BootstrapMinecraft;
import appeng.util.inv.AppEngInternalInventory;

@BootstrapMinecraft
class PatternUploadRecallServiceTest {
    private static final UUID PLAYER = UUID.fromString("5ccded43-a4d5-487f-922e-49ee383a573b");

    private PatternUploadRecallHistory history;
    private AppEngInternalInventory resultInventory;
    private AppEngInternalInventory playerInventory;
    private AppEngInternalInventory providerInventory;
    private ItemStack pattern;

    @BeforeEach
    void setUp() {
        history = new PatternUploadRecallHistory();
        resultInventory = new AppEngInternalInventory(1);
        playerInventory = new AppEngInternalInventory(36);
        providerInventory = new AppEngInternalInventory(1);
        pattern = encodedPattern(Items.IRON_INGOT);
    }

    @Test
    void recallsNewestMatchingPatternIntoTheEmptyResultSlot() {
        history.recordUploadedPattern(PLAYER, "assembler", pattern, 64);
        providerInventory.setItemDirect(0, pattern);

        var result = recall(List.of(provider("assembler", providerInventory)));

        assertEquals(PatternUploadRecallStatus.RECALLED_TO_RESULT_SLOT, result.status());
        assertThat(resultInventory.getStackInSlot(0))
                .matches(stack -> ItemStack.isSameItemSameComponents(stack, pattern));
        assertThat(providerInventory.getStackInSlot(0)).matches(ItemStack::isEmpty);
        assertThat(history.getRecords(PLAYER)).isEmpty();
    }

    @Test
    void keepsTheRecordAndProviderPatternWhenThereIsNoDestinationSpace() {
        history.recordUploadedPattern(PLAYER, "assembler", pattern, 64);
        providerInventory.setItemDirect(0, pattern);
        resultInventory.setItemDirect(0, new ItemStack(Items.STICK));
        for (int slot = 0; slot < playerInventory.size(); slot++) {
            playerInventory.setItemDirect(slot, new ItemStack(Items.DIRT, Items.DIRT.getDefaultMaxStackSize()));
        }

        var result = recall(List.of(provider("assembler", providerInventory)));

        assertEquals(PatternUploadRecallStatus.NO_DESTINATION_SPACE, result.status());
        assertThat(providerInventory.getStackInSlot(0))
                .matches(stack -> ItemStack.isSameItemSameComponents(stack, pattern));
        assertThat(history.getRecords(PLAYER)).hasSize(1);
    }

    @Test
    void returnsToPlayerInventoryWhenTheResultSlotIsOccupied() {
        history.recordUploadedPattern(PLAYER, "assembler", pattern, 64);
        providerInventory.setItemDirect(0, pattern);
        resultInventory.setItemDirect(0, new ItemStack(Items.STICK));

        var result = recall(List.of(provider("assembler", providerInventory)));

        assertEquals(PatternUploadRecallStatus.RECALLED_TO_PLAYER_INVENTORY, result.status());
        assertThat(playerInventory.getStackInSlot(0))
                .matches(stack -> ItemStack.isSameItemSameComponents(stack, pattern));
        assertThat(resultInventory.getStackInSlot(0)).matches(stack -> Items.STICK.equals(stack.getItem()));
    }

    @Test
    void removesStaleRecordsAndContinuesToAnOlderAvailablePattern() {
        var stale = encodedPattern(Items.GOLD_INGOT);
        history.recordUploadedPattern(PLAYER, "assembler", pattern, 64);
        history.recordUploadedPattern(PLAYER, "assembler", stale, 64);
        providerInventory.setItemDirect(0, pattern);

        var result = recall(List.of(provider("assembler", providerInventory)));

        assertEquals(PatternUploadRecallStatus.RECALLED_TO_RESULT_SLOT, result.status());
        assertThat(resultInventory.getStackInSlot(0))
                .matches(stack -> ItemStack.isSameItemSameComponents(stack, pattern));
        assertThat(history.getRecords(PLAYER)).isEmpty();
    }

    @Test
    void clearsAllStaleRecordsWithoutAnError() {
        history.recordUploadedPattern(PLAYER, "assembler", pattern, 64);

        var result = recall(List.of(provider("assembler", providerInventory)));

        assertEquals(PatternUploadRecallStatus.NO_RECALLABLE_PATTERN, result.status());
        assertThat(history.getRecords(PLAYER)).isEmpty();
        assertThat(resultInventory.getStackInSlot(0)).matches(ItemStack::isEmpty);
    }

    @Test
    void restoresTheProviderSlotWhenTheActualDestinationInsertFails() {
        history.recordUploadedPattern(PLAYER, "assembler", pattern, 64);
        providerInventory.setItemDirect(0, pattern);
        var rejectingResultInventory = new SimulateOnlyInventory();

        var result = PatternUploadRecallService.recallLastUploadedPattern(
                PLAYER, List.of(provider("assembler", providerInventory)), rejectingResultInventory, playerInventory,
                history, 64);

        assertEquals(PatternUploadRecallStatus.INSERT_FAILED, result.status());
        assertThat(providerInventory.getStackInSlot(0))
                .matches(stack -> ItemStack.isSameItemSameComponents(stack, pattern));
        assertThat(history.getRecords(PLAYER)).hasSize(1);
    }

    private PatternUploadRecallResult recall(List<PatternContainer> providers) {
        return PatternUploadRecallService.recallLastUploadedPattern(
                PLAYER, providers, resultInventory, playerInventory, history, 64);
    }

    private static PatternContainer provider(String groupName, InternalInventory inventory) {
        return new PatternContainer() {
            private final PatternContainerGroup group = new PatternContainerGroup(null, Component.literal(groupName),
                    List.of());

            @Override
            public appeng.api.networking.IGrid getGrid() {
                return null;
            }

            @Override
            public InternalInventory getTerminalPatternInventory() {
                return inventory;
            }

            @Override
            public PatternContainerGroup getTerminalGroup() {
                return group;
            }
        };
    }

    private static ItemStack encodedPattern(net.minecraft.world.item.Item output) {
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(GenericStack.fromItemStack(new ItemStack(Items.STICK))),
                List.of(GenericStack.fromItemStack(new ItemStack(output))));
    }

    private static final class SimulateOnlyInventory extends AppEngInternalInventory {
        private SimulateOnlyInventory() {
            super(1);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return simulate ? ItemStack.EMPTY : stack.copy();
        }
    }
}
