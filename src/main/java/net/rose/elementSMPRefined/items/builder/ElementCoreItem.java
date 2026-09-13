package net.rose.elementSMPRefined.items.builder;

import net.rose.elementSMPRefined.API.element.ElementType;
import net.rose.elementSMPRefined.API.element.ElementId;
import net.rose.elementSMPRefined.items.ItemKeys;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ElementCoreItem {
    private static final Map<ElementId, ElementCoreProperties> CORE_PROPERTIES = new HashMap<>();

    private ElementCoreItem() {}

    // Helper: define display properties for each element
    private record ElementCoreProperties(Material material, ChatColor color, String displayName, List<String> lore) {
        ElementCoreProperties(Material material, ChatColor color, String displayName) {
            this(material, color, displayName, null);
        }
    }

    /**
     * Registers the item definition for an element core.
     * Add future core items in one place during plugin startup.
     */
    public static void registerCore(ElementType type, Material material, ChatColor color,
                                    String displayName, List<String> lore) {
        if (type == null || material == null || color == null || displayName == null) {
            throw new IllegalArgumentException("Core definition values cannot be null");
        }
        registerCore(ElementId.builtin(type), material, color, displayName, lore);
    }

    public static void registerCore(ElementId id, Material material, ChatColor color,
                                    String displayName, List<String> lore) {
        if (id == null || material == null || color == null || displayName == null) {
            throw new IllegalArgumentException("Core definition values cannot be null");
        }
        CORE_PROPERTIES.put(id, new ElementCoreProperties(material, color, displayName, lore));
    }

    public static boolean hasCore(ElementType type) {
        return type != null && hasCore(ElementId.builtin(type));
    }

    public static boolean hasCore(ElementId id) {
        return id != null && CORE_PROPERTIES.containsKey(id);
    }

    private static ElementCoreProperties properties(ElementType type) {
        return type == null ? null : properties(ElementId.builtin(type));
    }

    private static ElementCoreProperties properties(ElementId id) {
        return id == null ? null : CORE_PROPERTIES.get(id);
    }

    public static ItemStack createCore(JavaPlugin plugin, ElementType type) {
        ElementCoreProperties props = properties(type);
        if (props == null) return null;

        return buildCore(plugin, props, type.name());
    }

    public static ItemStack createCore(JavaPlugin plugin, ElementId id) {
        ElementCoreProperties props = properties(id);
        if (props == null) return null;

        return buildCore(plugin, props, id.toString());
    }

    private static ItemStack buildCore(JavaPlugin plugin, ElementCoreProperties props, String elementTypeValue) {
        ItemBuilder builder = ItemBuilder.of(props.material())
                // Name already carries a resolved ChatColor, not a raw '&' code -
                // rawName() skips the color-code translation ItemBuilder#name() does.
                .rawName(props.color() + props.displayName())
                .data(ItemKeys.elementType(plugin), elementTypeValue)
                .data(ItemKeys.elementItem(plugin), (byte) 1);

        if (props.lore() != null) {
            builder.rawLore(props.lore());
        }

        return builder.build();
    }

    public static String getDisplayName(ElementType type) {
        ElementCoreProperties props = properties(type);
        if (props != null) {
            return props.color() + props.displayName();
        } else {
            return type.name();
        }
    }

    public static List<String> getLore(ElementType type) {
        ElementCoreProperties props = properties(type);
        if (props != null && props.lore() != null) {
            return props.lore();
        } else {
            return List.of();
        }
    }

    public static String getDisplayName(ElementId id) {
        ElementCoreProperties props = properties(id);
        return props == null ? id.toString() : props.color() + props.displayName();
    }

    public static List<String> getLore(ElementId id) {
        ElementCoreProperties props = properties(id);
        return props == null || props.lore() == null ? List.of() : props.lore();
    }
}