package appeng.crafting.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class CraftingRequestTest {
    @Test
    void tracksFulfilledAndRemainingAmount() {
        var request = new CraftingRequest(AEItemKey.of(Items.STONE), 100);
        var contribution = new RecordingContribution();

        request.fulfill(40, contribution);

        assertThat(request.amount()).isEqualTo(100);
        assertThat(request.fulfilledAmount()).isEqualTo(40);
        assertThat(request.remainingAmount()).isEqualTo(60);
        assertThat(contribution.refunded()).isZero();
    }

    @Test
    void partialRefundRollsBackLatestContribution() {
        var request = new CraftingRequest(AEItemKey.of(Items.STONE), 100);
        var first = new RecordingContribution();
        var second = new RecordingContribution();

        request.fulfill(40, first);
        request.fulfill(30, second);

        request.refund(25);

        assertThat(request.fulfilledAmount()).isEqualTo(45);
        assertThat(request.remainingAmount()).isEqualTo(55);
        assertThat(first.refunded()).isZero();
        assertThat(second.refunded()).isEqualTo(25);
    }

    @Test
    void fullRefundRollsBackAllContributionsInReverseOrder() {
        var request = new CraftingRequest(AEItemKey.of(Items.STONE), 100);
        var first = new RecordingContribution();
        var second = new RecordingContribution();

        request.fulfill(40, first);
        request.fulfill(30, second);

        request.fullRefund();

        assertThat(request.fulfilledAmount()).isZero();
        assertThat(request.remainingAmount()).isEqualTo(100);
        assertThat(first.refunded()).isEqualTo(40);
        assertThat(second.refunded()).isEqualTo(30);
        assertThat(first.refundOrder()).isGreaterThan(second.refundOrder());
    }

    private static final class RecordingContribution implements CraftingContribution {
        private static long nextRefundOrder;

        private long refunded;
        private long refundOrder;

        @Override
        public void refund(long amount) {
            this.refunded += amount;
            this.refundOrder = ++nextRefundOrder;
        }

        long refunded() {
            return refunded;
        }

        long refundOrder() {
            return refundOrder;
        }
    }
}
