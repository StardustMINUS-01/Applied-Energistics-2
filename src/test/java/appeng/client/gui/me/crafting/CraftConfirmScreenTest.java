package appeng.client.gui.me.crafting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CraftConfirmScreenTest {
    @Test
    void onlyShowsPartialPlanStatusForNonForcedSimulationPlans() {
        assertThat(CraftConfirmScreen.shouldShowPartialPlanStatus(true, false)).isTrue();
        assertThat(CraftConfirmScreen.shouldShowPartialPlanStatus(true, true)).isFalse();
        assertThat(CraftConfirmScreen.shouldShowPartialPlanStatus(false, false)).isFalse();
    }
}
