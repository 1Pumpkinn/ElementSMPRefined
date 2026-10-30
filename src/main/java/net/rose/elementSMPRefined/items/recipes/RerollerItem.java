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

    private static final NamespacedKey MODEL = NamespacedKey.fromString("elementsmp:element_reroller");

    public static ItemStack make(JavaPlugin plugin) {
        return ItemBuilder.of(Material.HEART_OF_THE_SEA)
                .name("&dElement Reroller")
                .itemModel(MODEL)
                .lore("&eRight-click to randomly reroll your element"
                )
                .data(ItemKeys.reroller(plugin), (byte) 1)
                .build();
    }

    public static void registerRecipe(JavaPlugin plugin) {
        try {
            ItemStack result = make(plugin);
            NamespacedKey key = new NamespacedKey(plugin, KEY);
            
            plugin.getServer().removeRecipe(key);
            
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape("GWG", "FPB", "GRG");
            recipe.setIngredient('W', Material.WIND_CHARGE);
            recipe.setIngredient('F', Material.FIRE_CHARGE);
            recipe.setIngredient('P', Material.PAPER);
            recipe.setIngredient('B', Material.WATER_BUCKET);
            recipe.setIngredient('R', Material.GRASS_BLOCK);
            recipe.setIngredient('G', Material.GOLD_BLOCK);

            plugin.getServer().addRecipe(recipe);
        } catch (Exception e) {
            plugin.getLogger().severe("Error registering Element Reroller recipe: " + e.getMessage());
        }
    }
}
