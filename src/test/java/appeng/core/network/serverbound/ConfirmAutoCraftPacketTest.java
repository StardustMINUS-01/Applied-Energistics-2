package appeng.core.network.serverbound;

import org.junit.jupiter.api.Test;

import appeng.util.CodecTestUtil;

class ConfirmAutoCraftPacketTest {

    @Test
    void roundTripsLargeCraftAmount() {
        CodecTestUtil.testRoundtrip(ConfirmAutoCraftPacket.STREAM_CODEC,
                new ConfirmAutoCraftPacket(80_000_000_000L, false, true));
    }
}
