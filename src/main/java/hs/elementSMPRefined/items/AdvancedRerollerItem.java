package hs.elementSMPRefined.items;

import hs.elementSMPRefined.ElementSMPRefined;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class AdvancedRerollerItem {
    private AdvancedRerollerItem() {}

    public static final String KEY = "advanced_reroller";

    public static ItemStack make(ElementSMPRefined plugin) {
        ItemStack item = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "Advanced Reroller");
        meta.setLore(List.of(
                ChatColor.GRAY + "Unlocks advanced elements",
                ChatColor.YELLOW + "Right-click to reroll"
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ItemKeys.namespaced(plugin, KEY), PersistentDataType.BYTE, (byte)1);
        item.setItemMeta(meta);
        return item;
    }

    public static void registerRecipe(ElementSMPRefined plugin) {
        try {
            ItemStack result = make(plugin);
            NamespacedKey key = new NamespacedKey(plugin, KEY);

            // Remove existing recipe if it exists
            plugin.getServer().removeRecipe(key);

            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape("DED", "ETE", "DED");
            recipe.setIngredient('D', Material.DIAMOND_BLOCK);
            recipe.setIngredient('E', Material.NETHERITE_INGOT);
            recipe.setIngredient('T', Material.TOTEM_OF_UNDYING);

            plugin.getServer().addRecipe(recipe);
        } catch (Exception e) {
            plugin.getLogger().severe("Error registering Advanced Reroller recipe: " + e.getMessage());
        }
    }
}

