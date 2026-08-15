package hs.elementSMPRefined.initializers;

import hs.elementSMPRefined.ElementSMPRefined;
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
            hs.elementSMPRefined.recipes.UtilRecipes.registerRecipes(plugin);
        }, 1);
    }
}