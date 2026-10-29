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

    private static final NamespacedKey MODEL = NamespacedKey.fromString("elementsmp:upgrader_2");

    public static ItemStack make(JavaPlugin plugin) {
        return ItemBuilder.of(Material.AMETHYST_SHARD)
                .name("&bUpgrader II")
                .itemModel(MODEL)
                .lore("Unlocks Ability 2 and Passive 2")
                .data(ItemKeys.upgraderLevel(plugin), 2)
                .build();
    }

    public static void registerRecipe(JavaPlugin plugin) {
        try {
            ItemStack result = make(plugin);
            NamespacedKey key = new NamespacedKey(plugin, KEY);
            
            plugin.getServer().removeRecipe(key);
            
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape("GOG", "TWT", "GOG");

           recipe.setIngredient('G', Material.GOLD_BLOCK);
           recipe.setIngredient('O', Material.OMINOUS_TRIAL_KEY);
           recipe.setIngredient('T', Material.TOTEM_OF_UNDYING);
           recipe.setIngredient('W', Material.WITHER_SKELETON_SKULL);

            plugin.getServer().addRecipe(recipe);
        } catch (Exception e) {
            plugin.getLogger().severe("Error registering Upgrader II recipe: " + e.getMessage());
        }
    }
}
