package appeng.parts.networking;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.networking.IGridNodeListener;
import appeng.api.parts.IPartHost;
import appeng.items.parts.ColoredPartItem;

class SmartCablePartTest {

    @Test
    void smartCableSkipsClientUpdateDuringGridBoot() {
        var host = mock(IPartHost.class);
        var part = new SmartCablePart(partItem());
        part.setPartHostInfo(null, host, mock(BlockEntity.class));

        part.onMainNodeStateChanged(IGridNodeListener.State.GRID_BOOT);

        verify(host, never()).markForUpdate();
    }

    @Test
    void smartDenseCableSkipsClientUpdateDuringGridBoot() {
        var host = mock(IPartHost.class);
        var part = new SmartDenseCablePart(partItem());
        part.setPartHostInfo(null, host, mock(BlockEntity.class));

        part.onMainNodeStateChanged(IGridNodeListener.State.GRID_BOOT);

        verify(host, never()).markForUpdate();
    }

    @SuppressWarnings("unchecked")
    private static ColoredPartItem<?> partItem() {
        var partItem = mock(ColoredPartItem.class);
        when(partItem.asItem()).thenReturn(Items.STONE);
        return partItem;
    }
}
