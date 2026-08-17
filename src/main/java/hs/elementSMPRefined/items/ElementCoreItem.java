package hs.elementSMPRefined.items;

import hs.elementSMPRefined.API.element.ElementType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Collections;
import java.util.List;

public final class ElementCoreItem {
    private ElementCoreItem() {}

    // Helper: define display properties for each element
    private record ElementCoreProperties(Material material, ChatColor color, String displayName, List<String> lore) {
        ElementCoreProperties(Material material, ChatColor color, String displayName) {
            this(material, color, displayName, null);
        }
    }

    private static ElementCoreProperties properties(ElementType type) {
        switch (type) {
            default:
                return null;
        }
    }

    public static ItemStack createCore(JavaPlugin plugin, ElementType type) {
        ElementCoreProperties props = properties(type);
        if (props == null) return null;

        ItemStack item = new ItemStack(props.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(props.color() + props.displayName());
            if (props.lore() != null) {
                meta.setLore(props.lore());
            }
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ItemKeys.elementType(plugin), PersistentDataType.STRING, type.name());
            pdc.set(ItemKeys.elementItem(plugin), PersistentDataType.BYTE, (byte) 1);

            item.setItemMeta(meta);
        }
        return item;
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
            return Collections.emptyList();
        }
    }
}