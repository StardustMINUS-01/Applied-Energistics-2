/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 TeamAppliedEnergistics
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package appeng.api.networking.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

/**
 * Optional provider capability for accepting a batch of inputs for the same pattern through the generic external
 * inventory path.
 */
public interface IBulkCraftingProvider {
    /**
     * Pushes inputs for multiple executions of one pattern into an external inventory.
     * <p>
     * Implementations must not route this call through dedicated {@code ICraftingMachine} targets. This method is only
     * for patterns that allow {@link IPatternDetails#supportsPushInputsToExternalInventory()}.
     *
     * @return true if the batch was accepted.
     */
    boolean pushPatternBatchToExternalInventory(IPatternDetails patternDetails, KeyCounter[] inputHolder);
}
