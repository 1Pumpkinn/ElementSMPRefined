package hs.elementSMPRefined.items;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemKeys {
    private ItemKeys() {}

    public static final String KEY_UPGRADER_LEVEL = "upgrader_level";

    public static final String KEY_ELEMENT_ITEM = "element_item";
    public static final String KEY_ELEMENT_TYPE = "element_type";

    public static final String KEY_REROLLER = "element_reroller";
    public static final String KEY_ADVANCED_REROLLER = "advanced_reroller";

    public static NamespacedKey namespaced(JavaPlugin plugin, String key) {
        return new NamespacedKey(plugin, key);
    }

    public static NamespacedKey upgraderLevel(JavaPlugin plugin) { return namespaced(plugin, KEY_UPGRADER_LEVEL); }
    public static NamespacedKey elementItem(JavaPlugin plugin) { return namespaced(plugin, KEY_ELEMENT_ITEM); }
    public static NamespacedKey elementType(JavaPlugin plugin) { return namespaced(plugin, KEY_ELEMENT_TYPE); }
    public static NamespacedKey reroller(JavaPlugin plugin) { return namespaced(plugin, KEY_REROLLER); }
    public static NamespacedKey advancedReroller(JavaPlugin plugin) { return namespaced(plugin, KEY_ADVANCED_REROLLER); }
}