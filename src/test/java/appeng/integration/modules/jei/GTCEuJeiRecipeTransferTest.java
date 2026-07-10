package appeng.integration.modules.jei;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import appeng.menu.me.items.PatternEncodingTermMenu;

class GTCEuJeiRecipeTransferTest {
    @Test
    void resolvesOptionalPatternEncodingMenuByClassName() {
        var registration = GTCEuJeiMenuCompatibility.findPatternEncodingMenu(PatternEncodingTermMenu.class.getName());

        assertThat(registration).isPresent();
        assertThat(registration.orElseThrow().containerClass()).isEqualTo(PatternEncodingTermMenu.class);
        assertThat(registration.orElseThrow().menuType()).isEqualTo(PatternEncodingTermMenu.TYPE);
    }

    @Test
    void skipsMissingOptionalPatternEncodingMenu() {
        assertThat(GTCEuJeiMenuCompatibility.findPatternEncodingMenu("missing.ae2wtlib.wet.WETMenu")).isEmpty();
    }

    @Test
    void skipsClassesThatAreNotPatternEncodingMenus() {
        assertThat(GTCEuJeiMenuCompatibility.findPatternEncodingMenu(String.class.getName())).isEmpty();
    }
}
