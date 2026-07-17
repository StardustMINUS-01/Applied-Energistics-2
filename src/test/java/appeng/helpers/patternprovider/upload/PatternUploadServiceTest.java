package appeng.helpers.patternprovider.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.google.gson.stream.JsonWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.events.GridEvent;
import appeng.api.networking.pathing.IPathingService;
import appeng.api.networking.spatial.ISpatialService;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.util.BootstrapMinecraft;
import appeng.util.inv.AppEngInternalInventory;

@BootstrapMinecraft
class PatternUploadServiceTest {
    private ServerPlayer player;
    private Level level;
    private PatternEncodingTermMenu menu;
    private IGridNode gridNode;
    private TestGrid grid;
    private AppEngInternalInventory sourceInventory;

    @BeforeEach
    void setUp() {
        player = mock(ServerPlayer.class);
        level = mock(Level.class);
        menu = mock(PatternEncodingTermMenu.class);
        gridNode = mock(IGridNode.class);
        grid = new TestGrid();
        sourceInventory = new AppEngInternalInventory(1);

        when(player.level()).thenReturn(level);
        when(menu.getGridNode()).thenReturn(gridNode);
        when(gridNode.isActive()).thenReturn(true);
        when(gridNode.getGrid()).thenReturn(grid);
    }

    @Test
    void rejectsEmptySource() {
        var result = upload();

        assertEquals(PatternUploadStatus.INVALID_SOURCE, result.status());
    }

    @Test
    void rejectsNonEncodedPattern() {
        sourceInventory.setItemDirect(0, new ItemStack(Items.STICK));

        var result = upload();

        assertEquals(PatternUploadStatus.INVALID_PATTERN, result.status());
        assertThat(sourceInventory.getStackInSlot(0).isEmpty()).isFalse();
    }

    @Test
    void rejectsStackedEncodedPattern() {
        sourceInventory.setItemDirect(0, encodedPattern().copyWithCount(2));

        var result = upload();

        assertEquals(PatternUploadStatus.INVALID_PATTERN_STACK_SIZE, result.status());
        assertEquals(2, sourceInventory.getStackInSlot(0).getCount());
    }

    @Test
    void rejectsInactiveGrid() {
        sourceInventory.setItemDirect(0, encodedPattern());
        when(gridNode.isActive()).thenReturn(false);

        var result = upload();

        assertEquals(PatternUploadStatus.NO_GRID, result.status());
        assertThat(sourceInventory.getStackInSlot(0).isEmpty()).isFalse();
    }

    @Test
    void reportsNoTargetWhenGridHasNoVisiblePatternContainers() {
        sourceInventory.setItemDirect(0, encodedPattern());

        var result = upload();

        assertEquals(PatternUploadStatus.NO_TARGET, result.status());
        assertThat(sourceInventory.getStackInSlot(0).isEmpty()).isFalse();
    }

    @Test
    void reportsTargetFullWhenTargetsExistButNoneCanAcceptPattern() {
        sourceInventory.setItemDirect(0, encodedPattern());
        var fullTarget = new TestPatternContainer(grid, new AppEngInternalInventory(1));
        fullTarget.inventory.setItemDirect(0, encodedPattern());
        grid.containers = Set.of(fullTarget);

        var result = upload();

        assertEquals(PatternUploadStatus.TARGET_FULL, result.status());
        assertThat(sourceInventory.getStackInSlot(0).isEmpty()).isFalse();
    }

    @Test
    void reportsMultipleTargetsWhenMoreThanOneVisibleTargetCanAcceptPattern() {
        sourceInventory.setItemDirect(0, encodedPattern());
        var first = new TestPatternContainer(grid, new AppEngInternalInventory(1), "first");
        var second = new TestPatternContainer(grid, new AppEngInternalInventory(1), "second");
        grid.containers = Set.of(first, second);

        var result = upload();

        assertEquals(PatternUploadStatus.MULTIPLE_TARGETS, result.status());
        assertThat(result.candidates()).hasSize(2)
                .extracting(candidate -> candidate.group().name().getString())
                .containsExactlyInAnyOrder("first", "second");
        assertThat(sourceInventory.getStackInSlot(0).isEmpty()).isFalse();
        assertThat(first.inventory.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(second.inventory.getStackInSlot(0).isEmpty()).isTrue();
    }

    @Test
    void uploadsToSelectedTargetWhenTargetHintIsProvided() {
        var pattern = encodedPattern();
        sourceInventory.setItemDirect(0, pattern);
        var first = new TestPatternContainer(grid, new AppEngInternalInventory(1), "first");
        var second = new TestPatternContainer(grid, new AppEngInternalInventory(1), "second");
        grid.containers = Set.of(first, second);

        var selectedTarget = PatternUploadTarget.from(second);
        var result = PatternUploadService.uploadPattern(player, menu,
                PatternUploadSource.singleSlot(sourceInventory, 0),
                PatternUploadTargetHint.selected(selectedTarget.id()));

        assertEquals(PatternUploadStatus.UPLOADED, result.status());
        assertEquals("second", result.uploadedGroupName());
        assertThat(result.uploadedPattern()).matches(stack -> ItemStack.isSameItemSameComponents(stack, pattern));
        assertThat(sourceInventory.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(first.inventory.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(second.inventory.getStackInSlot(0)).matches(PatternDetailsHelper::isEncodedPattern);
    }

    @Test
    void treatsSameNamedProvidersAsOneTargetGroup() {
        var pattern = encodedPattern();
        sourceInventory.setItemDirect(0, pattern);
        var first = new TestPatternContainer(grid, new AppEngInternalInventory(1), "same");
        var second = new TestPatternContainer(grid, new AppEngInternalInventory(1), "same");
        grid.containers = Set.of(first, second);

        var result = upload();

        assertEquals(PatternUploadStatus.UPLOADED, result.status());
        assertThat(sourceInventory.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(first.inventory.getStackInSlot(0).isEmpty()
                || second.inventory.getStackInSlot(0).isEmpty()).isTrue();
    }

    @Test
    void uploadsToSelectedTargetGroup() {
        var pattern = encodedPattern();
        sourceInventory.setItemDirect(0, pattern);
        var first = new TestPatternContainer(grid, new AppEngInternalInventory(1), "first");
        var second = new TestPatternContainer(grid, new AppEngInternalInventory(1), "second");
        grid.containers = Set.of(first, second);

        var result = PatternUploadService.uploadPattern(player, menu,
                PatternUploadSource.singleSlot(sourceInventory, 0),
                PatternUploadTargetHint.selectedGroup("second"));

        assertEquals(PatternUploadStatus.UPLOADED, result.status());
        assertThat(sourceInventory.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(first.inventory.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(second.inventory.getStackInSlot(0)).matches(PatternDetailsHelper::isEncodedPattern);
    }

    @Test
    void reportsNoTargetWhenSelectedTargetIdIsNotVisibleAnymore() {
        sourceInventory.setItemDirect(0, encodedPattern());
        grid.containers = Set.of(new TestPatternContainer(grid, new AppEngInternalInventory(1)));

        var result = PatternUploadService.uploadPattern(player, menu,
                PatternUploadSource.singleSlot(sourceInventory, 0),
                PatternUploadTargetHint.selected(Integer.MAX_VALUE));

        assertEquals(PatternUploadStatus.NO_TARGET, result.status());
        assertThat(sourceInventory.getStackInSlot(0).isEmpty()).isFalse();
    }

    @Test
    void uploadsToUniqueInsertableTargetAndRemovesSource() {
        var pattern = encodedPattern();
        sourceInventory.setItemDirect(0, pattern);
        var target = new TestPatternContainer(grid, new AppEngInternalInventory(1));
        grid.containers = Set.of(target);

        var result = upload();

        assertEquals(PatternUploadStatus.UPLOADED, result.status());
        assertThat(sourceInventory.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(target.inventory.getStackInSlot(0)).matches(PatternDetailsHelper::isEncodedPattern);
    }

    @Test
    void ignoresFullTargetsWhenExactlyOneOtherTargetCanAcceptPattern() {
        var pattern = encodedPattern();
        sourceInventory.setItemDirect(0, pattern);
        var fullTarget = new TestPatternContainer(grid, new AppEngInternalInventory(1));
        fullTarget.inventory.setItemDirect(0, encodedPattern());
        var insertableTarget = new TestPatternContainer(grid, new AppEngInternalInventory(1));
        grid.containers = Set.of(fullTarget, insertableTarget);

        var result = upload();

        assertEquals(PatternUploadStatus.UPLOADED, result.status());
        assertThat(sourceInventory.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(insertableTarget.inventory.getStackInSlot(0)).matches(PatternDetailsHelper::isEncodedPattern);
    }

    @Test
    void uploadsBatchInSourceOrderAndContinuesAfterAnInvalidSource() {
        var firstSource = new AppEngInternalInventory(1);
        var invalidSource = new AppEngInternalInventory(1);
        var lastSource = new AppEngInternalInventory(1);
        var firstPattern = encodedPattern();
        var lastPattern = PatternDetailsHelper.encodeProcessingPattern(
                List.of(GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT))),
                List.of(GenericStack.fromItemStack(new ItemStack(Items.GOLD_BLOCK))));
        firstSource.setItemDirect(0, firstPattern);
        invalidSource.setItemDirect(0, new ItemStack(Items.STICK));
        lastSource.setItemDirect(0, lastPattern);
        var target = new TestPatternContainer(grid, new AppEngInternalInventory(2), "target");
        grid.containers = Set.of(target);

        var result = PatternUploadService.uploadPatterns(player, grid,
                List.of(
                        PatternUploadSource.singleSlot(firstSource, 0),
                        PatternUploadSource.singleSlot(invalidSource, 0),
                        PatternUploadSource.singleSlot(lastSource, 0)),
                "target");

        assertEquals(2, result.uploadedCount());
        assertEquals(0, result.targetFullCount());
        assertEquals(1, result.invalidSourceCount());
        assertEquals(0, result.failedCount());
        assertThat(firstSource.getStackInSlot(0)).matches(ItemStack::isEmpty);
        assertThat(invalidSource.getStackInSlot(0)).matches(stack -> Items.STICK.equals(stack.getItem()));
        assertThat(lastSource.getStackInSlot(0)).matches(ItemStack::isEmpty);
        assertThat(result.uploadedPatterns())
                .extracting(PatternUploadResult::uploadedPattern)
                .usingElementComparator((left, right) -> ItemStack.isSameItemSameComponents(left, right) ? 0 : 1)
                .containsExactly(firstPattern, lastPattern);
    }

    @Test
    void doesNotRemoveSourceWhenRealInsertFailsAfterSimulation() {
        var pattern = encodedPattern();
        sourceInventory.setItemDirect(0, pattern);
        var target = new TestPatternContainer(grid, new SimulateOnlyInventory());
        grid.containers = Set.of(target);

        var result = upload();

        assertEquals(PatternUploadStatus.INSERT_FAILED, result.status());
        assertThat(sourceInventory.getStackInSlot(0).isEmpty()).isFalse();
    }

    private PatternUploadResult upload() {
        return PatternUploadService.uploadPattern(player, menu,
                PatternUploadSource.singleSlot(sourceInventory, 0),
                PatternUploadTargetHint.auto());
    }

    private static ItemStack encodedPattern() {
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT))),
                List.of(GenericStack.fromItemStack(new ItemStack(Items.IRON_BLOCK))));
    }

    private static final class TestPatternContainer implements PatternContainer {
        private final IGrid grid;
        private final InternalInventory inventory;
        private final PatternContainerGroup group;

        private TestPatternContainer(IGrid grid, InternalInventory inventory) {
            this(grid, inventory, "target");
        }

        private TestPatternContainer(IGrid grid, InternalInventory inventory, String name) {
            this.grid = grid;
            this.inventory = inventory;
            this.group = new PatternContainerGroup(null, Component.literal(name), List.of());
        }

        @Override
        public IGrid getGrid() {
            return grid;
        }

        @Override
        public InternalInventory getTerminalPatternInventory() {
            return inventory;
        }

        @Override
        public PatternContainerGroup getTerminalGroup() {
            return group;
        }
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

    private static final class TestGrid implements IGrid {
        private Set<TestPatternContainer> containers = Set.of();

        @Override
        public <C extends IGridService> C getService(Class<C> iface) {
            return null;
        }

        @Override
        public <T extends GridEvent> T postEvent(T ev) {
            return ev;
        }

        @Override
        public Iterable<Class<?>> getMachineClasses() {
            return List.of(TestPatternContainer.class);
        }

        @Override
        public Iterable<IGridNode> getMachineNodes(Class<?> machineClass) {
            return List.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Set<T> getMachines(Class<T> machineClass) {
            return TestPatternContainer.class.equals(machineClass) ? (Set<T>) containers : Set.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Set<T> getActiveMachines(Class<T> machineClass) {
            return TestPatternContainer.class.equals(machineClass) ? (Set<T>) containers : Set.of();
        }

        @Override
        public Iterable<IGridNode> getNodes() {
            return List.of();
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public IGridNode getPivot() {
            return null;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ITickManager getTickManager() {
            return null;
        }

        @Override
        public IStorageService getStorageService() {
            return null;
        }

        @Override
        public IEnergyService getEnergyService() {
            return null;
        }

        @Override
        public ICraftingService getCraftingService() {
            return null;
        }

        @Override
        public IPathingService getPathingService() {
            return null;
        }

        @Override
        public ISpatialService getSpatialService() {
            return null;
        }

        @Override
        public void export(JsonWriter jsonWriter) throws IOException {
        }
    }
}
