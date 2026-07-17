package appeng.integration.jei.patternencoding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.PatternCatalyst;
import appeng.crafting.pattern.PatternVirtualInputHelper;
import appeng.integration.modules.gtceu.GTCEuPatternMetadataBridge;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
@MockitoSettings(strictness = Strictness.LENIENT)
class RecipeChainPatternEncodingServiceTest {
    private ServerPlayer player;
    private Inventory inventory;
    private Level level;
    private PatternEncodingTermMenu menu;
    private int terminalBlankPatterns;

    @BeforeEach
    void setUp() {
        player = mock(ServerPlayer.class);
        inventory = new Inventory(player);
        level = mock(Level.class);
        menu = mock(PatternEncodingTermMenu.class);

        when(player.getInventory()).thenReturn(inventory);
        when(player.getAbilities()).thenReturn(new Abilities());
        when(player.level()).thenReturn(level);
        when(menu.getAvailableBlankPatternCount()).thenAnswer(invocation -> terminalBlankPatterns);
        when(menu.hasBlankPatterns(anyInt())).thenAnswer(invocation -> {
            int amount = invocation.getArgument(0);
            return player.getAbilities().instabuild || terminalBlankPatterns >= amount;
        });
        when(menu.consumeBlankPatterns(anyInt())).thenAnswer(invocation -> {
            int amount = invocation.getArgument(0);
            if (player.getAbilities().instabuild) {
                return true;
            }
            if (terminalBlankPatterns < amount) {
                return false;
            }
            terminalBlankPatterns -= amount;
            return true;
        });
    }

    @Test
    void rejectsWhenCurrentMenuIsNotPatternEncodingTerminal() {
        var result = RecipeChainPatternEncodingService.encodeRecipeChainPatterns(player, List.of(validRequest(0)));

        assertEquals(PatternEncodeStopReason.NOT_PATTERN_ENCODING_TERMINAL, result.stopReason());
        assertEquals(1, result.remainingUnprocessedCount());
        assertEquals(PatternEncodeEntryStatus.NOT_PROCESSED, result.entries().get(0).status());
    }

    @Test
    void encodesProcessingRequestsAndConsumesBlankPatterns() {
        setTerminalBlankPatterns(3);

        var result = RecipeChainPatternEncodingService.encodeRecipeChainPatterns(player, menu,
                List.of(validRequest(0), validRequest(1), validRequest(2)));

        assertEquals(PatternEncodeStopReason.NONE, result.stopReason());
        assertEquals(3, result.encodedCount());
        assertEquals(0, terminalBlankPatterns);
        assertEquals(3, countEncodedPatterns());
    }

    @Test
    void ignoresPlayerInventoryBlankPatternsWhenTerminalBlankSlotIsEmpty() {
        inventory.setItem(0, AEItems.BLANK_PATTERN.stack(3));

        var result = RecipeChainPatternEncodingService.encodeRecipeChainPatterns(player, menu,
                List.of(validRequest(0)));

        assertEquals(PatternEncodeStopReason.NO_BLANK_PATTERN, result.stopReason());
        assertEquals(0, result.encodedCount());
        assertEquals(3, inventory.countItem(AEItems.BLANK_PATTERN.asItem()));
        assertEquals(0, countEncodedPatterns());
    }

    @Test
    void skipsExistingPrimaryOutputWithoutConsumingBlankPattern() {
        inventory.setItem(0, existingPatternFor(validRequest(0)));
        setTerminalBlankPatterns(1);

        var result = RecipeChainPatternEncodingService.encodeRecipeChainPatterns(player, menu,
                List.of(validRequest(0)));

        assertEquals(PatternEncodeStopReason.NONE, result.stopReason());
        assertEquals(0, result.encodedCount());
        assertEquals(1, result.skippedExistingCount());
        assertEquals(1, terminalBlankPatterns);
    }

    @Test
    void writesNothingWhenTerminalBlankPatternsAreInsufficient() {
        setTerminalBlankPatterns(1);

        var result = RecipeChainPatternEncodingService.encodeRecipeChainPatterns(player, menu,
                List.of(validRequest(0), validRequest(1), validRequest(2)));

        assertEquals(PatternEncodeStopReason.NO_BLANK_PATTERN, result.stopReason());
        assertEquals(0, result.encodedCount());
        assertEquals(3, result.remainingUnprocessedCount());
        assertEquals(1, terminalBlankPatterns);
        assertEquals(0, countEncodedPatterns());
        assertEquals(PatternEncodeEntryStatus.NOT_PROCESSED, result.entries().get(0).status());
        assertEquals(PatternEncodeEntryStatus.NOT_PROCESSED, result.entries().get(1).status());
        assertEquals(PatternEncodeEntryStatus.NOT_PROCESSED, result.entries().get(2).status());
    }

    @Test
    void stopsWhenInventoryHasNoSpaceAndKeepsBlankPattern() {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        setTerminalBlankPatterns(1);

        var result = RecipeChainPatternEncodingService.encodeRecipeChainPatterns(player, menu,
                List.of(validRequest(0)));

        assertEquals(PatternEncodeStopReason.NO_INVENTORY_SPACE, result.stopReason());
        assertEquals(0, result.encodedCount());
        assertEquals(1, terminalBlankPatterns);
        assertEquals(0, countEncodedPatterns());
    }

    @Test
    void skipsInvalidRequestAndContinues() {
        setTerminalBlankPatterns(2);

        var result = RecipeChainPatternEncodingService.encodeRecipeChainPatterns(player, menu,
                List.of(validRequest(0), invalidProcessingRequest(), validRequest(1)));

        assertEquals(PatternEncodeStopReason.NONE, result.stopReason());
        assertEquals(2, result.encodedCount());
        assertEquals(1, result.skippedInvalidCount());
        assertEquals(0, terminalBlankPatterns);
        assertEquals(PatternEncodeEntryStatus.SKIPPED_INVALID_RECIPE, result.entries().get(1).status());
    }

    @Test
    void invalidAndDuplicateRequestsDoNotCountAgainstTerminalBlankAvailability() {
        inventory.setItem(0, existingPatternFor(validRequest(0)));
        setTerminalBlankPatterns(1);

        var result = RecipeChainPatternEncodingService.encodeRecipeChainPatterns(player, menu,
                List.of(validRequest(0), invalidProcessingRequest(), validRequest(1)));

        assertEquals(PatternEncodeStopReason.NONE, result.stopReason());
        assertEquals(1, result.encodedCount());
        assertEquals(1, result.skippedExistingCount());
        assertEquals(1, result.skippedInvalidCount());
        assertEquals(0, terminalBlankPatterns);
        assertEquals(PatternEncodeEntryStatus.SKIPPED_EXISTING_PRIMARY_OUTPUT, result.entries().get(0).status());
        assertEquals(PatternEncodeEntryStatus.SKIPPED_INVALID_RECIPE, result.entries().get(1).status());
        assertEquals(PatternEncodeEntryStatus.ENCODED, result.entries().get(2).status());
    }

    @Test
    void creativePlayerCanEncodeWithoutTerminalBlankPatterns() {
        player.getAbilities().instabuild = true;

        var result = RecipeChainPatternEncodingService.encodeRecipeChainPatterns(player, menu,
                List.of(validRequest(0), validRequest(1)));

        assertEquals(PatternEncodeStopReason.NONE, result.stopReason());
        assertEquals(2, result.encodedCount());
        assertEquals(0, terminalBlankPatterns);
        assertEquals(2, countEncodedPatterns());
    }

    @Test
    void rejectsProcessingRequestWithoutPrimaryOutput() {
        assertNull(RecipeChainPatternEncodingService.createProcessingPattern(invalidProcessingRequest()));
    }

    @Test
    void createsValidProcessingPattern() {
        var encoded = RecipeChainPatternEncodingService.createProcessingPattern(validRequest(0));

        assertFalse(encoded.isEmpty());
        assertTrue(PatternDetailsHelper.isEncodedPattern(encoded));
    }

    @Test
    void processingRequestVirtualCircuitBecomesMetadataNotInput() {
        var realInput = GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        var circuitInput = GenericStack.fromItemStack(IntCircuitBehaviour.stack(24));
        var output = GenericStack.fromItemStack(new ItemStack(Items.IRON_BLOCK));
        var request = new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("jei", "processing"),
                ResourceLocation.fromNamespaceAndPath("test", "virtual_circuit"),
                PatternEncodeMode.PROCESSING,
                List.of(realInput, circuitInput),
                List.of(output),
                List.of(new PatternCatalyst(1, circuitInput)),
                List.of(),
                null,
                false,
                false);

        var encoded = RecipeChainPatternEncodingService.createProcessingPattern(request);
        var decoded = (AEProcessingPattern) PatternDetailsHelper.decodePattern(encoded, level);

        assertEquals(24, GTCEuPatternMetadataBridge.getVirtualCircuitFromEncodedPattern(encoded).orElseThrow());
        assertEquals(1, decoded.getInputs().length);
        assertEquals(realInput, decoded.getInputs()[0].getPossibleInputs()[0]);
        assertTrue(PatternVirtualInputHelper.getCatalysts(encoded).entries().isEmpty());
    }

    @Test
    void processingRequestStoresCatalystsOutsidePlannerInputs() {
        var realInput = GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        var catalyst = GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT, 64));
        var output = GenericStack.fromItemStack(new ItemStack(Items.IRON_BLOCK));
        var request = new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("jei", "processing"),
                ResourceLocation.fromNamespaceAndPath("test", "catalyst"),
                PatternEncodeMode.PROCESSING,
                List.of(realInput, catalyst),
                List.of(output),
                List.of(new PatternCatalyst(1, catalyst)),
                List.of(),
                null,
                false,
                false);

        var encoded = RecipeChainPatternEncodingService.createProcessingPattern(request);
        var decoded = (AEProcessingPattern) PatternDetailsHelper.decodePattern(encoded, level);

        assertEquals(1, decoded.getInputs().length);
        assertEquals(realInput, decoded.getInputs()[0].getPossibleInputs()[0]);
        assertEquals(List.of(new PatternCatalyst(1, catalyst)),
                PatternVirtualInputHelper.getCatalysts(encoded).entries());
    }

    @Test
    void processingRequestSeparatesCircuitMetadataFromMultipleCatalysts() {
        var realInput = GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        var firstCatalyst = GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT, 64));
        var secondCatalyst = GenericStack.fromItemStack(new ItemStack(Items.DIAMOND, 3));
        var circuit = GenericStack.fromItemStack(IntCircuitBehaviour.stack(24));
        var output = GenericStack.fromItemStack(new ItemStack(Items.IRON_BLOCK));
        var request = new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("jei", "processing"),
                ResourceLocation.fromNamespaceAndPath("test", "multiple_catalysts_and_circuit"),
                PatternEncodeMode.PROCESSING,
                List.of(realInput, firstCatalyst, secondCatalyst, circuit),
                List.of(output),
                List.of(
                        new PatternCatalyst(1, firstCatalyst),
                        new PatternCatalyst(2, secondCatalyst),
                        new PatternCatalyst(3, circuit)),
                List.of(),
                null,
                false,
                false);

        var encoded = RecipeChainPatternEncodingService.createProcessingPattern(request);
        var decoded = (AEProcessingPattern) PatternDetailsHelper.decodePattern(encoded, level);

        assertEquals(24, GTCEuPatternMetadataBridge.getVirtualCircuitFromEncodedPattern(encoded).orElseThrow());
        assertEquals(1, decoded.getInputs().length);
        assertEquals(realInput, decoded.getInputs()[0].getPossibleInputs()[0]);
        assertEquals(List.of(
                new PatternCatalyst(1, firstCatalyst),
                new PatternCatalyst(2, secondCatalyst)),
                PatternVirtualInputHelper.getCatalysts(encoded).entries());
    }

    @Test
    void skipsInvalidCatalystAndContinuesWithLaterRequests() {
        setTerminalBlankPatterns(1);
        var input = GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        var invalid = new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("jei", "processing"),
                ResourceLocation.fromNamespaceAndPath("test", "invalid_catalyst"),
                PatternEncodeMode.PROCESSING,
                List.of(input),
                List.of(GenericStack.fromItemStack(new ItemStack(Items.IRON_BLOCK))),
                List.of(new PatternCatalyst(1, input)),
                List.of(),
                null,
                false,
                false);

        var result = RecipeChainPatternEncodingService.encodeRecipeChainPatterns(player, menu,
                List.of(invalid, validRequest(1)));

        assertEquals(1, result.encodedCount());
        assertEquals(1, result.skippedInvalidCount());
        assertEquals(PatternEncodeEntryStatus.SKIPPED_INVALID_CATALYST, result.entries().get(0).status());
        assertEquals(PatternEncodeEntryStatus.ENCODED, result.entries().get(1).status());
    }

    @Test
    void rejectsProcessingRequestWithOnlyCatalystInputs() {
        var catalyst = GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT, 64));
        var request = new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("jei", "processing"),
                ResourceLocation.fromNamespaceAndPath("test", "only_catalyst"),
                PatternEncodeMode.PROCESSING,
                List.of(catalyst),
                List.of(GenericStack.fromItemStack(new ItemStack(Items.IRON_BLOCK))),
                List.of(new PatternCatalyst(0, catalyst)),
                List.of(),
                null,
                false,
                false);

        assertNull(RecipeChainPatternEncodingService.createProcessingPattern(request));
    }

    @Test
    void rejectsDuplicateAndMismatchedCatalystSlots() {
        var realInput = GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        var catalyst = GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT, 64));
        var inputs = List.of(realInput, catalyst);
        var output = List.of(GenericStack.fromItemStack(new ItemStack(Items.IRON_BLOCK)));
        var duplicate = new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("jei", "processing"),
                ResourceLocation.fromNamespaceAndPath("test", "duplicate_catalyst"),
                PatternEncodeMode.PROCESSING,
                inputs,
                output,
                List.of(new PatternCatalyst(1, catalyst), new PatternCatalyst(1, catalyst)),
                List.of(),
                null,
                false,
                false);
        var mismatched = new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("jei", "processing"),
                ResourceLocation.fromNamespaceAndPath("test", "mismatched_catalyst"),
                PatternEncodeMode.PROCESSING,
                inputs,
                output,
                List.of(new PatternCatalyst(1, realInput)),
                List.of(),
                null,
                false,
                false);

        assertEquals("Catalyst source slots must be unique",
                RecipeChainPatternEncodingService.validateCatalysts(duplicate));
        assertEquals("Catalyst must match its processing input",
                RecipeChainPatternEncodingService.validateCatalysts(mismatched));
    }

    @Test
    void createsCraftingPatternFromCanonicalGuide() {
        var recipeManager = mock(RecipeManager.class);
        var recipe = mock(CraftingRecipe.class);
        var recipeId = ResourceLocation.fromNamespaceAndPath("minecraft", "oak_planks");
        var recipeHolder = new RecipeHolder<>(recipeId, recipe);
        when(level.getRecipeManager()).thenReturn(recipeManager);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        when(recipeManager.byKey(recipeId)).thenReturn(Optional.of(recipeHolder));
        doReturn(RecipeType.CRAFTING).when(recipe).getType();
        when(recipe.getIngredients()).thenReturn(NonNullList.of(
                Ingredient.EMPTY,
                Ingredient.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE)));
        when(recipe.matches(org.mockito.ArgumentMatchers.any(CraftingInput.class),
                org.mockito.ArgumentMatchers.eq(level))).thenReturn(true);
        when(recipe.assemble(org.mockito.ArgumentMatchers.any(CraftingInput.class),
                org.mockito.ArgumentMatchers.any())).thenReturn(new ItemStack(Items.OAK_PLANKS, 4));

        var request = new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "oak_planks"),
                PatternEncodeMode.CRAFTING,
                List.of(),
                List.of(),
                List.of(),
                java.util.Arrays.asList(
                        GenericStack.fromItemStack(new ItemStack(Items.COBBLED_DEEPSLATE)),
                        null, null, null, null, null, null, null, null),
                recipeId,
                true,
                true);

        var encoded = RecipeChainPatternEncodingService.createCraftingPattern(level, request);

        assertNotNull(encoded);
        assertTrue(PatternDetailsHelper.isEncodedPattern(encoded));
        assertEquals(Items.COBBLED_DEEPSLATE,
                encoded.get(AEComponents.ENCODED_CRAFTING_PATTERN).inputs().getFirst().getItem());
    }

    @Test
    void createsStonecuttingPatternFromServerRecipeIdWithoutClientSlots() {
        var recipeManager = mock(RecipeManager.class);
        var recipe = mock(StonecutterRecipe.class);
        var recipeId = ResourceLocation.fromNamespaceAndPath("minecraft", "stone_slab");
        var recipeHolder = new RecipeHolder<>(recipeId, recipe);
        when(level.getRecipeManager()).thenReturn(recipeManager);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        when(recipeManager.byKey(recipeId)).thenReturn(Optional.of(recipeHolder));
        doReturn(RecipeType.STONECUTTING).when(recipe).getType();
        when(recipe.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE)));
        when(recipe.matches(org.mockito.ArgumentMatchers.any(SingleRecipeInput.class),
                org.mockito.ArgumentMatchers.eq(level))).thenReturn(true);
        when(recipe.getResultItem(org.mockito.ArgumentMatchers.any())).thenReturn(new ItemStack(Items.STONE_SLAB, 2));

        var request = new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("minecraft", "stonecutting"),
                recipeId,
                PatternEncodeMode.STONECUTTING,
                List.of(),
                List.of(),
                List.of(),
                List.of(GenericStack.fromItemStack(new ItemStack(Items.STONE))),
                recipeId,
                false,
                false);

        var encoded = RecipeChainPatternEncodingService.createEncodedPattern(level, request);

        assertNotNull(encoded);
        assertTrue(PatternDetailsHelper.isEncodedPattern(encoded));
    }

    @Test
    void createsSmithingPatternFromServerRecipeIdWithoutClientSlots() {
        var recipeManager = mock(RecipeManager.class);
        var recipe = mock(SmithingRecipe.class);
        var recipeId = ResourceLocation.fromNamespaceAndPath("minecraft", "netherite_sword_smithing");
        var recipeHolder = new RecipeHolder<>(recipeId, recipe);
        when(level.getRecipeManager()).thenReturn(recipeManager);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        when(recipeManager.byKey(recipeId)).thenReturn(Optional.of(recipeHolder));
        doReturn(RecipeType.SMITHING).when(recipe).getType();
        when(recipe.getIngredients()).thenReturn(NonNullList.of(
                Ingredient.EMPTY,
                Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                Ingredient.of(Items.DIAMOND_SWORD),
                Ingredient.of(Items.NETHERITE_INGOT)));
        when(recipe.matches(org.mockito.ArgumentMatchers.any(SmithingRecipeInput.class),
                org.mockito.ArgumentMatchers.eq(level))).thenReturn(true);
        when(recipe.assemble(org.mockito.ArgumentMatchers.any(SmithingRecipeInput.class),
                org.mockito.ArgumentMatchers.any())).thenReturn(new ItemStack(Items.NETHERITE_SWORD));

        var request = new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("minecraft", "smithing"),
                recipeId,
                PatternEncodeMode.SMITHING_TABLE,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        GenericStack.fromItemStack(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)),
                        GenericStack.fromItemStack(new ItemStack(Items.DIAMOND_SWORD)),
                        GenericStack.fromItemStack(new ItemStack(Items.NETHERITE_INGOT))),
                recipeId,
                false,
                false);

        var encoded = RecipeChainPatternEncodingService.createEncodedPattern(level, request);

        assertNotNull(encoded);
        assertTrue(PatternDetailsHelper.isEncodedPattern(encoded));
    }

    private PatternEncodeRequest validRequest(int index) {
        var input = switch (index) {
            case 1 -> Items.GOLD_INGOT;
            case 2 -> Items.DIAMOND;
            default -> Items.IRON_INGOT;
        };
        var output = switch (index) {
            case 1 -> Items.GOLD_BLOCK;
            case 2 -> Items.DIAMOND_BLOCK;
            default -> Items.IRON_BLOCK;
        };
        return new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("jei", "processing"),
                ResourceLocation.fromNamespaceAndPath("test", "recipe_" + index),
                PatternEncodeMode.PROCESSING,
                List.of(GenericStack.fromItemStack(new ItemStack(input))),
                List.of(GenericStack.fromItemStack(new ItemStack(output))),
                List.of(),
                List.of(),
                null,
                false,
                false);
    }

    private PatternEncodeRequest invalidProcessingRequest() {
        return new PatternEncodeRequest(
                ResourceLocation.fromNamespaceAndPath("jei", "processing"),
                ResourceLocation.fromNamespaceAndPath("test", "invalid"),
                PatternEncodeMode.PROCESSING,
                List.of(GenericStack.fromItemStack(new ItemStack(Items.STICK))),
                List.of(),
                List.of(),
                List.of(),
                null,
                false,
                false);
    }

    private ItemStack existingPatternFor(PatternEncodeRequest request) {
        return RecipeChainPatternEncodingService.createProcessingPattern(request);
    }

    private void setTerminalBlankPatterns(int count) {
        terminalBlankPatterns = count;
    }

    private int countEncodedPatterns() {
        var count = 0;
        for (var stack : inventory.items) {
            if (PatternDetailsHelper.isEncodedPattern(stack)) {
                count++;
            }
        }
        return count;
    }

}
