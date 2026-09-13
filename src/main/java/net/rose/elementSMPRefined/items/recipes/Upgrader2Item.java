package net.rose.elementSMPRefined.items.recipes;

import net.rose.elementSMPRefined.items.ItemKeys;
import net.rose.elementSMPRefined.items.builder.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class Upgrader2Item {
    private Upgrader2Item() {}

    public static final String KEY = "upgrader_2";

    public static ItemStack make(JavaPlugin plugin) {
        return ItemBuilder.of(Material.ECHO_SHARD)
                .name("&bUpgrader II")
                .lore("Use by crafting to unlock", "Ability 2 + Upside 2 for your element")
                .data(ItemKeys.upgraderLevel(plugin), 2)
                .build();
    }

    public static void registerRecipe(JavaPlugin plugin) {
        try {
            ItemStack result = make(plugin);
            NamespacedKey key = new NamespacedKey(plugin, KEY);
            
            // Remove existing recipe if it exists
            plugin.getServer().removeRecipe(key);
            
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape("DFD", "WNB", "DAD");
            recipe.setIngredient('D', Material.DIAMOND_BLOCK);
            recipe.setIngredient('N', Material.NETHERITE_INGOT);

            recipe.setIngredient('F', Material.FIRE_CHARGE);
            recipe.setIngredient('W', Material.WATER_BUCKET);
            recipe.setIngredient('B', Material.GRASS_BLOCK);
            recipe.setIngredient('A', Material.FEATHER);
            
            plugin.getServer().addRecipe(recipe);
        } catch (Exception e) {
            plugin.getLogger().severe("Error registering Upgrader II recipe: " + e.getMessage());
        }
    }
}
