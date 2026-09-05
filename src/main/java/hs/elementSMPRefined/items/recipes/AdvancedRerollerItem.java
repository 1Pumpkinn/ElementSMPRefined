package hs.elementSMPRefined.items.recipes;

import hs.elementSMPRefined.items.ItemKeys;
import hs.elementSMPRefined.items.builder.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdvancedRerollerItem {
    private AdvancedRerollerItem() {}

    public static final String KEY = "advanced_reroller";

    public static ItemStack make(JavaPlugin plugin) {
        return ItemBuilder.of(Material.RECOVERY_COMPASS)
                .name("&5Advanced Reroller")
                .lore(
                        "&7Unlocks advanced elements",
                        "&eRight-click to reroll"
                )
                .data(ItemKeys.advancedReroller(plugin), (byte) 1)
                .build();
    }

    public static void registerRecipe(JavaPlugin plugin) {
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
