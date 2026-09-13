package net.rose.elementSMPRefined.core.initializers;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.items.recipes.AdvancedRerollerItem;
import net.rose.elementSMPRefined.items.recipes.RerollerItem;
import net.rose.elementSMPRefined.items.recipes.Upgrader1Item;
import net.rose.elementSMPRefined.items.recipes.Upgrader2Item;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles registration of all plugin recipes.
 * Centralizes recipe registration logic away from the main class.
 */
public class RecipeInitializer {
    private final ElementSMPRefined plugin;

    public RecipeInitializer(JavaPlugin plugin) {
        this.plugin = (ElementSMPRefined) plugin;
    }

    public void registerRecipes() {
        // Delay recipe registration to ensure all items are registered first
        plugin.getTaskScheduler().runLaterSeconds(() -> {
            Upgrader1Item.registerRecipe(plugin);
            Upgrader2Item.registerRecipe(plugin);
            RerollerItem.registerRecipe(plugin);

            if (plugin.getConfigManager().isAdvancedRerollerRecipeEnabled()) {
                AdvancedRerollerItem.registerRecipe(plugin);
            }
        }, 1);
    }
}