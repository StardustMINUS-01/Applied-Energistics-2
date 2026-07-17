package appeng.helpers.patternprovider.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class PatternUploadRecallHistoryTest {
    private static final UUID FIRST_PLAYER = UUID.fromString("1e6a0ec0-1d64-4fa9-93df-a5c1a1bc7111");
    private static final UUID SECOND_PLAYER = UUID.fromString("4f30e9c2-4fbe-4f70-a406-9d7679479869");

    private final RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    @Test
    void keepsNewestRecordsAndTrimsTheOldest() {
        var history = new PatternUploadRecallHistory();
        var first = encodedPattern(Items.IRON_INGOT);
        var second = encodedPattern(Items.GOLD_INGOT);
        var third = encodedPattern(Items.DIAMOND);

        history.recordUploadedPattern(FIRST_PLAYER, "assembler", first, 2);
        history.recordUploadedPattern(FIRST_PLAYER, "assembler", second, 2);
        history.recordUploadedPattern(FIRST_PLAYER, "assembler", third, 2);

        assertThat(history.getRecords(FIRST_PLAYER))
                .extracting(PatternUploadRecallRecord::pattern)
                .usingElementComparator((left, right) -> ItemStack.isSameItemSameComponents(left, right) ? 0 : 1)
                .containsExactly(third, second);
    }

    @Test
    void savesCompleteRecordsAndKeepsPlayersSeparate() {
        var history = new PatternUploadRecallHistory();
        var firstPattern = encodedPattern(Items.IRON_INGOT);
        var secondPattern = encodedPattern(Items.DIAMOND);
        history.recordUploadedPattern(FIRST_PLAYER, "first group", firstPattern, 64);
        history.recordUploadedPattern(SECOND_PLAYER, "second group", secondPattern, 64);

        CompoundTag tag = history.save(new CompoundTag(), registries);
        var restored = new PatternUploadRecallHistory(tag, registries);

        assertThat(restored.getRecords(FIRST_PLAYER)).singleElement().satisfies(record -> {
            assertThat(record.providerGroupName()).isEqualTo("first group");
            assertThat(ItemStack.isSameItemSameComponents(record.pattern(), firstPattern)).isTrue();
        });
        assertThat(restored.getRecords(SECOND_PLAYER)).singleElement().satisfies(record -> {
            assertThat(record.providerGroupName()).isEqualTo("second group");
            assertThat(ItemStack.isSameItemSameComponents(record.pattern(), secondPattern)).isTrue();
        });
    }

    private static ItemStack encodedPattern(net.minecraft.world.item.Item output) {
        return PatternDetailsHelper.encodeProcessingPattern(
                java.util.List.of(GenericStack.fromItemStack(new ItemStack(Items.STICK))),
                java.util.List.of(GenericStack.fromItemStack(new ItemStack(output))));
    }
}
