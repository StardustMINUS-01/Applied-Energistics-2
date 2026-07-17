package appeng.client.gui;

import java.util.Optional;

import net.minecraft.client.renderer.Rect2i;

public interface SearchTextDropTarget {
    Optional<Rect2i> getSearchTextDropArea();

    void setSearchTextFromDrop(String searchText);
}
