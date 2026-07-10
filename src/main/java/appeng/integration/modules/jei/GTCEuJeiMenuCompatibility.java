package appeng.integration.modules.jei;

import java.lang.reflect.Field;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.inventory.MenuType;

import appeng.menu.me.items.PatternEncodingTermMenu;

final class GTCEuJeiMenuCompatibility {
    static final String AE2WT_PATTERN_ENCODING_MENU_CLASS = "de.mari_023.ae2wtlib.wet.WETMenu";

    private GTCEuJeiMenuCompatibility() {
    }

    static Optional<PatternEncodingMenu> findPatternEncodingMenu(String className) {
        var menuClass = classOrNull(className);
        if (menuClass == null || !PatternEncodingTermMenu.class.isAssignableFrom(menuClass)) {
            return Optional.empty();
        }

        var menuType = readMenuType(menuClass, "TYPE");
        if (menuType == null) {
            return Optional.empty();
        }

        return Optional.of(new PatternEncodingMenu(castMenuClass(menuClass), menuType));
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends PatternEncodingTermMenu> castMenuClass(Class<?> menuClass) {
        return (Class<? extends PatternEncodingTermMenu>) menuClass;
    }

    @Nullable
    private static Class<?> classOrNull(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    @Nullable
    private static MenuType<? extends PatternEncodingTermMenu> readMenuType(Class<?> menuClass, String fieldName) {
        try {
            Field field = menuClass.getField(fieldName);
            var value = field.get(null);
            if (value instanceof MenuType<?> menuType) {
                return castMenuType(menuType);
            }
        } catch (ReflectiveOperationException | SecurityException | LinkageError | ClassCastException e) {
            return null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static MenuType<? extends PatternEncodingTermMenu> castMenuType(MenuType<?> menuType) {
        return (MenuType<? extends PatternEncodingTermMenu>) menuType;
    }

    record PatternEncodingMenu(
            Class<? extends PatternEncodingTermMenu> containerClass,
            MenuType<? extends PatternEncodingTermMenu> menuType) {
    }
}
