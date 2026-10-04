package net.rose.elementSMPRefined.items.recipes;

import net.rose.elementSMPRefined.items.ItemKeys;
import net.rose.elementSMPRefined.items.builder.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class Upgrader1Item {
    private Upgrader1Item() {}

    public static final String KEY = "upgrader_1";

    public static ItemStack make(JavaPlugin plugin) {
        return ItemBuilder.of(Material.AMETHYST_SHARD)
                .name("&aUpgrader I")
                .lore("Use by crafting to unlock", "Ability 1 for your element")
                .data(ItemKeys.upgraderLevel(plugin), 1)
                .build();
    }

    public static void registerRecipe(JavaPlugin plugin) {
        try {
            ItemStack result = make(plugin);
            NamespacedKey key = new NamespacedKey(plugin, KEY);
            
            plugin.getServer().removeRecipe(key);
            
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape("GAG", "AEA", "GAG");
            recipe.setIngredient('G', Material.GOLD_BLOCK);
            recipe.setIngredient('A', Material.AMETHYST_SHARD);
            recipe.setIngredient('E', Material.ENDER_EYE);

            plugin.getServer().addRecipe(recipe);
        } catch (Exception e) {
            plugin.getLogger().severe("Error registering Upgrader I recipe: " + e.getMessage());
        }
    }
}
