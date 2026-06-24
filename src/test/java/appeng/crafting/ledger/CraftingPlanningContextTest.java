package appeng.crafting.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class CraftingPlanningContextTest {
    @Test
    void networkExtractionCanBePartiallyRefunded() {
        var stone = AEItemKey.of(Items.STONE);
        var inventory = new KeyCounter();
        inventory.add(stone, 6);
        var context = new CraftingPlanningContext(inventory);
        var request = new CraftingRequest(stone, 10);

        context.extractFromNetwork(request);

        assertThat(request.fulfilledAmount()).isEqualTo(6);
        assertThat(request.remainingAmount()).isEqualTo(4);
        assertThat(context.availableAmount(stone)).isZero();
        assertThat(context.usedItems().get(stone)).isEqualTo(6);

        request.refund(2);

        assertThat(request.fulfilledAmount()).isEqualTo(4);
        assertThat(request.remainingAmount()).isEqualTo(6);
        assertThat(context.availableAmount(stone)).isEqualTo(2);
        assertThat(context.usedItems().get(stone)).isEqualTo(4);
    }

    @Test
    void networkExtractionFullRefundRestoresAvailableInventory() {
        var stone = AEItemKey.of(Items.STONE);
        var inventory = new KeyCounter();
        inventory.add(stone, 6);
        var context = new CraftingPlanningContext(inventory);
        var request = new CraftingRequest(stone, 10);

        context.extractFromNetwork(request);
        request.fullRefund();

        assertThat(request.fulfilledAmount()).isZero();
        assertThat(request.remainingAmount()).isEqualTo(10);
        assertThat(context.availableAmount(stone)).isEqualTo(6);
        assertThat(context.usedItems().get(stone)).isZero();
    }
}
