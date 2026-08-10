package hs.elementSMPRefined.items;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.elements.ElementType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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
            case AIR:
                return new ElementCoreProperties(
                        Material.FEATHER,
                        ChatColor.LIGHT_PURPLE,
                        "Air Element"
                );
            case WATER:
                return new ElementCoreProperties(
                        Material.PRISMARINE_CRYSTALS,
                        ChatColor.AQUA,
                        "Water Element"
                );
            case FIRE:
                return new ElementCoreProperties(
                        Material.BLAZE_POWDER,
                        ChatColor.RED,
                        "Fire Element"
                );
            case EARTH:
                return new ElementCoreProperties(
                        Material.DIRT,
                        ChatColor.GREEN,
                        "Earth Element"
                );
            case LIFE:
                return new ElementCoreProperties(
                        Material.REDSTONE_BLOCK,
                        ChatColor.RED,
                        "Life Element"
                );
            case DEATH:
                return new ElementCoreProperties(
                        Material.WITHER_SKELETON_SKULL,
                        ChatColor.DARK_GRAY,
                        "Death Element"
                );
            case METAL:
                return new ElementCoreProperties(
                        Material.IRON_INGOT,
                        ChatColor.GRAY,
                        "Metal Element"
                );
            case FROST:
                return new ElementCoreProperties(
                        Material.SNOWBALL,
                        ChatColor.WHITE,
                        "Frost Element"
                );
            default:
                return null;
        }
    }

    public static ItemStack createCore(ElementSMPRefined plugin, ElementType type) {
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

            // Add specific core identifier based on type
            if (type == ElementType.LIFE) {
                pdc.set(ItemKeys.lifeCore(plugin), PersistentDataType.BYTE, (byte) 1);
            } else if (type == ElementType.DEATH) {
                pdc.set(ItemKeys.deathCore(plugin), PersistentDataType.BYTE, (byte) 1);
            }

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