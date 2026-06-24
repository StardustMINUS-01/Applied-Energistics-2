/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.encoding;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.AEItemDefinitionFilter;

public class PatternEncodingLogic implements InternalInventoryHost {

    private static final String MODE_DRAFTS_TAG = "modeDrafts";
    private static final String DRAFT_INPUTS_TAG = "inputs";
    private static final String DRAFT_OUTPUTS_TAG = "outputs";

    private static final int MAX_INPUT_SLOTS = Math.max(AECraftingPattern.CRAFTING_GRID_SLOTS,
            AEProcessingPattern.MAX_INPUT_SLOTS);
    private static final int MAX_OUTPUT_SLOTS = AEProcessingPattern.MAX_OUTPUT_SLOTS;

    private final IPatternTerminalLogicHost host;

    private final ConfigInventory encodedInputInv = ConfigInventory.configStacks(MAX_INPUT_SLOTS)
            .changeListener(this::onEncodedInputChanged).allowOverstacking(true).build();
    private final ConfigInventory encodedOutputInv = ConfigInventory.configStacks(MAX_OUTPUT_SLOTS)
            .changeListener(this::onEncodedOutputChanged).allowOverstacking(true).build();

    private final AppEngInternalInventory blankPatternInv = new AppEngInternalInventory(this, 1);
    private final AppEngInternalInventory encodedPatternInv = new AppEngInternalInventory(this, 1);
    private final Map<EncodingMode, List<GenericStack>> inputDrafts = new EnumMap<>(EncodingMode.class);
    private final Map<EncodingMode, List<GenericStack>> outputDrafts = new EnumMap<>(EncodingMode.class);

    private EncodingMode mode = EncodingMode.CRAFTING;
    private boolean substitute = false;
    private boolean substituteFluids = true;
    private boolean isLoading = false;
    @Nullable
    private ResourceLocation stonecuttingRecipeId;

    public PatternEncodingLogic(IPatternTerminalLogicHost host) {
        this.host = host;
        this.blankPatternInv.setFilter(new AEItemDefinitionFilter(AEItems.BLANK_PATTERN));
        initializeEmptyDrafts();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        // Load the encoded inputs and outputs of a pattern if it changes
        if (inv == this.encodedPatternInv) {
            loadEncodedPattern(encodedPatternInv.getStackInSlot(0));
        }

        saveChanges();
    }

    public void saveChanges() {
        // Do not re-save while we're loading since it could overwrite the NBT with incomplete data
        if (!isLoading) {
            host.markForSave();
        }
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
    }

    @Override
    public boolean isClientSide() {
        return host.getLevel().isClientSide();
    }

    private void onEncodedInputChanged() {
        fixCraftingRecipes();
        if (!isClientSide()) {
            saveVisibleInputDraft(mode);
        }
        saveChanges();
    }

    private void onEncodedOutputChanged() {
        if (!isClientSide()) {
            saveVisibleOutputDraft(mode);
        }
        saveChanges();
    }

    private void loadEncodedPattern(ItemStack pattern) {
        if (pattern.isEmpty()) {
            return;
        }

        var details = PatternDetailsHelper.decodePattern(pattern, host.getLevel());

        if (details instanceof AECraftingPattern craftingPattern) {
            loadCraftingPattern(craftingPattern);
        } else if (details instanceof AEProcessingPattern processingPattern) {
            loadProcessingPattern(processingPattern);
        } else if (details instanceof AESmithingTablePattern smithingTablePattern) {
            loadSmithingTablePattern(smithingTablePattern);
        } else if (details instanceof AEStonecuttingPattern stonecuttingPattern) {
            loadStonecuttingPattern(stonecuttingPattern);
        }

        saveChanges();
    }

    private void loadCraftingPattern(AECraftingPattern pattern) {
        setMode(EncodingMode.CRAFTING);
        this.substitute = pattern.canSubstitute();
        this.substituteFluids = pattern.canSubstituteFluids();

        fillInventoryFromSparseStacks(encodedInputInv, pattern.getSparseInputs());
        fillInventoryFromSparseStacks(encodedOutputInv, pattern.getSparseOutputs());
    }

    private void loadProcessingPattern(AEProcessingPattern pattern) {
        setMode(EncodingMode.PROCESSING);

        fillInventoryFromSparseStacks(encodedInputInv, pattern.getSparseInputs());
        fillInventoryFromSparseStacks(encodedOutputInv, pattern.getSparseOutputs());
    }

    private void loadSmithingTablePattern(AESmithingTablePattern pattern) {
        setMode(EncodingMode.SMITHING_TABLE);
        this.substitute = pattern.canSubstitute();

        encodedInputInv.clear();
        encodedInputInv.setStack(0, new GenericStack(pattern.getTemplate(), 1));
        encodedInputInv.setStack(1, new GenericStack(pattern.getBase(), 1));
        encodedInputInv.setStack(2, new GenericStack(pattern.getAddition(), 1));
        encodedOutputInv.clear();
    }

    private void loadStonecuttingPattern(AEStonecuttingPattern pattern) {
        setMode(EncodingMode.STONECUTTING);
        stonecuttingRecipeId = pattern.getRecipeId();

        this.substitute = pattern.canSubstitute;

        encodedInputInv.clear();
        encodedInputInv.setStack(0, new GenericStack(pattern.getInput(), 1));
        encodedOutputInv.clear();
    }

    private static void fillInventoryFromSparseStacks(ConfigInventory inv, List<GenericStack> stacks) {
        inv.beginBatch();
        try {
            for (int i = 0; i < inv.size(); i++) {
                inv.setStack(i, i < stacks.size() ? stacks.get(i) : null);
            }
        } finally {
            inv.endBatch();
        }
    }

    public EncodingMode getMode() {
        return mode;
    }

    public void setMode(EncodingMode mode) {
        if (isClientSide()) {
            this.mode = mode;
            return;
        }

        if (this.mode != mode) {
            saveVisibleDraft(this.mode);
            this.mode = mode;
            restoreVisibleDraft(mode);
        } else {
            this.mode = mode;
        }

        this.fixCraftingRecipes();
        saveVisibleDraft(mode);
        this.saveChanges();
    }

    public boolean isSubstitution() {
        return this.substitute;
    }

    public void setSubstitution(boolean canSubstitute) {
        this.substitute = canSubstitute;
        this.saveChanges();
    }

    public boolean isFluidSubstitution() {
        return this.substituteFluids;
    }

    public void setFluidSubstitution(boolean canSubstitute) {
        this.substituteFluids = canSubstitute;
        this.saveChanges();
    }

    public @Nullable ResourceLocation getStonecuttingRecipeId() {
        return stonecuttingRecipeId;
    }

    public void setStonecuttingRecipeId(ResourceLocation stonecuttingRecipeId) {
        this.stonecuttingRecipeId = stonecuttingRecipeId;
        this.saveChanges();
    }

    /**
     * The inventory used to store the inputs for encoding them into a pattern. Does not contain real items.
     * <p/>
     * Used for all {@link #getMode() modes}.
     */
    public ConfigInventory getEncodedInputInv() {
        return encodedInputInv;
    }

    /**
     * The inventory used to store the outputs for encoding them into a pattern. Does not contain real items.
     * <p/>
     * Not used for crafting {@link #getMode() modes}.
     */
    public ConfigInventory getEncodedOutputInv() {
        return encodedOutputInv;
    }

    /**
     * Inventory of size 1, which contains the blank patterns for encoding.
     */
    public InternalInventory getBlankPatternInv() {
        return blankPatternInv;
    }

    /**
     * Inventory of size 1, which will receive the encoded pattern and can be used to place an already-encoded pattern
     * for re-encoding.
     */
    public InternalInventory getEncodedPatternInv() {
        return encodedPatternInv;
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        isLoading = true;
        try {
            try {
                this.mode = EncodingMode.valueOf(data.getString("mode"));
            } catch (IllegalArgumentException ignored) {
                this.mode = EncodingMode.CRAFTING;
            }
            this.setSubstitution(data.getBoolean("substitute"));
            this.setFluidSubstitution(data.getBoolean("substituteFluids"));

            if (data.contains("stonecuttingRecipeId", Tag.TAG_STRING)) {
                this.stonecuttingRecipeId = ResourceLocation.parse(data.getString("stonecuttingRecipeId"));
            } else {
                this.stonecuttingRecipeId = null;
            }

            blankPatternInv.readFromNBT(data, "blankPattern", registries);
            encodedPatternInv.readFromNBT(data, "encodedPattern", registries);

            encodedInputInv.readFromChildTag(data, "encodedInputs", registries);
            encodedOutputInv.readFromChildTag(data, "encodedOutputs", registries);

            if (data.contains(MODE_DRAFTS_TAG, Tag.TAG_COMPOUND)) {
                readModeDrafts(data.getCompound(MODE_DRAFTS_TAG), registries);
                restoreVisibleDraft(this.mode);
            } else {
                saveVisibleDraft(this.mode);
            }
        } finally {
            isLoading = false;
        }
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        saveVisibleDraft(this.mode);

        data.putString("mode", this.mode.name());
        data.putBoolean("substitute", this.substitute);
        data.putBoolean("substituteFluids", this.substituteFluids);
        if (this.stonecuttingRecipeId != null) {
            data.putString("stonecuttingRecipeId", this.stonecuttingRecipeId.toString());
        }
        blankPatternInv.writeToNBT(data, "blankPattern", registries);
        encodedPatternInv.writeToNBT(data, "encodedPattern", registries);
        encodedInputInv.writeToChildTag(data, "encodedInputs", registries);
        encodedOutputInv.writeToChildTag(data, "encodedOutputs", registries);
        writeModeDrafts(data, registries);
    }

    private void initializeEmptyDrafts() {
        for (var mode : EncodingMode.values()) {
            inputDrafts.put(mode, emptyDraft(MAX_INPUT_SLOTS));
            outputDrafts.put(mode, emptyDraft(MAX_OUTPUT_SLOTS));
        }
    }

    private static List<GenericStack> emptyDraft(int size) {
        var result = new ArrayList<GenericStack>(size);
        for (int i = 0; i < size; i++) {
            result.add(null);
        }
        return result;
    }

    private void saveVisibleDraft(EncodingMode mode) {
        saveVisibleInputDraft(mode);
        saveVisibleOutputDraft(mode);
    }

    private void saveVisibleInputDraft(EncodingMode mode) {
        inputDrafts.put(mode, encodedInputInv.toList());
    }

    private void saveVisibleOutputDraft(EncodingMode mode) {
        outputDrafts.put(mode, encodedOutputInv.toList());
    }

    private void restoreVisibleDraft(EncodingMode mode) {
        fillInventoryFromSparseStacks(encodedInputInv, inputDrafts.get(mode));
        fillInventoryFromSparseStacks(encodedOutputInv, outputDrafts.get(mode));
    }

    private void readModeDrafts(CompoundTag draftsTag, HolderLookup.Provider registries) {
        initializeEmptyDrafts();

        for (var mode : EncodingMode.values()) {
            var modeTagName = modeDraftTagName(mode);
            if (!draftsTag.contains(modeTagName, Tag.TAG_COMPOUND)) {
                continue;
            }

            var modeTag = draftsTag.getCompound(modeTagName);
            inputDrafts.put(mode, readDraft(modeTag.getList(DRAFT_INPUTS_TAG, Tag.TAG_COMPOUND),
                    MAX_INPUT_SLOTS, registries));
            outputDrafts.put(mode, readDraft(modeTag.getList(DRAFT_OUTPUTS_TAG, Tag.TAG_COMPOUND),
                    MAX_OUTPUT_SLOTS, registries));
        }
    }

    private void writeModeDrafts(CompoundTag data, HolderLookup.Provider registries) {
        var draftsTag = new CompoundTag();

        for (var mode : EncodingMode.values()) {
            var modeTag = new CompoundTag();
            writeDraft(modeTag, DRAFT_INPUTS_TAG, inputDrafts.get(mode), registries);
            writeDraft(modeTag, DRAFT_OUTPUTS_TAG, outputDrafts.get(mode), registries);

            if (!modeTag.isEmpty()) {
                draftsTag.put(modeDraftTagName(mode), modeTag);
            }
        }

        if (draftsTag.isEmpty()) {
            data.remove(MODE_DRAFTS_TAG);
        } else {
            data.put(MODE_DRAFTS_TAG, draftsTag);
        }
    }

    private static List<GenericStack> readDraft(ListTag tag, int size, HolderLookup.Provider registries) {
        var inv = new GenericStackInv(null, size);
        inv.readFromTag(tag, registries);
        return inv.toList();
    }

    private static void writeDraft(CompoundTag tag, String name, List<GenericStack> draft,
            HolderLookup.Provider registries) {
        var inv = new GenericStackInv(null, draft.size());
        inv.readFromList(draft);
        inv.writeToChildTag(tag, name, registries);
    }

    private static String modeDraftTagName(EncodingMode mode) {
        return mode.name().toLowerCase(Locale.ROOT);
    }

    private void fixCraftingRecipes() {
        // Do not do this on the client since the server will always sync the correct state to us anyway
        if (host.getLevel() == null || host.getLevel().isClientSide()) {
            return;
        }

        if (getMode() != EncodingMode.PROCESSING) {
            var craftingGrid = getEncodedInputInv();
            for (int slot = 0; slot < craftingGrid.size(); slot++) {
                var stack = craftingGrid.getStack(slot);
                if (stack == null) {
                    continue;
                }

                // Crafting recipes only support real items, not wrapped fluids or similar things
                if (!AEItemKey.is(stack.what())) {
                    craftingGrid.setStack(slot, null);
                    continue;
                }

                // Clamp item count to 1 for crafting recipes
                if (stack.amount() != 1) {
                    craftingGrid.setStack(slot, new GenericStack(stack.what(), 1));
                }
            }
        }
    }
}
