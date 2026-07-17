package appeng.integration.modules.jei;

import java.util.List;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import appeng.client.gui.SearchTextDropTarget;

final class JeiSearchTextGhostIngredientHandler<T extends Screen & SearchTextDropTarget>
        implements IGhostIngredientHandler<T> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
        var searchText = JeiSearchText.fromTypedIngredient(ingredient);
        if (searchText.isEmpty()) {
            return List.of();
        }

        return gui.getSearchTextDropArea()
                .<List<Target<I>>>map(area -> List.of(new SearchTextTarget<>(area, gui, searchText.orElseThrow())))
                .orElseGet(List::of);
    }

    @Override
    public void onComplete() {
    }

    private record SearchTextTarget<I>(
            Rect2i area,
            SearchTextDropTarget screen,
            String searchText) implements Target<I> {
        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            screen.setSearchTextFromDrop(searchText);
        }
    }
}
