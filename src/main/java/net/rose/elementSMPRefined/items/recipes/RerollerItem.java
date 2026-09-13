package net.rose.elementSMPRefined.items.recipes;

import net.rose.elementSMPRefined.items.ItemKeys;
import net.rose.elementSMPRefined.items.builder.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class RerollerItem {
    private RerollerItem() {}

    public static final String KEY = "element_reroller";

    public static ItemStack make(JavaPlugin plugin) {
        return ItemBuilder.of(Material.HEART_OF_THE_SEA)
                .name("&dElement Reroller")
                .lore(
                        "&7Allows you to change your element",
                        "&eRight-click to randomly reroll your element"
                )
                .data(ItemKeys.reroller(plugin), (byte) 1)
                .build();
    }

    public static void registerRecipe(JavaPlugin plugin) {
        try {
            ItemStack result = make(plugin);
            NamespacedKey key = new NamespacedKey(plugin, KEY);
            
            // Remove existing recipe if it exists
            plugin.getServer().removeRecipe(key);
            
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape("IEG", "ETE", "DEM");
            recipe.setIngredient('I', Material.IRON_BLOCK);
            recipe.setIngredient('G', Material.GOLD_BLOCK);
            recipe.setIngredient('D', Material.DIAMOND_BLOCK);
            recipe.setIngredient('M', Material.EMERALD_BLOCK);
            recipe.setIngredient('E', Material.NETHERITE_SCRAP);
            recipe.setIngredient('T', Material.TOTEM_OF_UNDYING);
            
            plugin.getServer().addRecipe(recipe);
        } catch (Exception e) {
            plugin.getLogger().severe("Error registering Element Reroller recipe: " + e.getMessage());
        }
    }
}
