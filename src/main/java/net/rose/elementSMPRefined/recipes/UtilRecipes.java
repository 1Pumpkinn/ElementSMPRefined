package net.rose.elementSMPRefined.recipes;

import net.rose.elementSMPRefined.items.recipes.AdvancedRerollerItem;
import net.rose.elementSMPRefined.items.recipes.RerollerItem;
import net.rose.elementSMPRefined.items.recipes.Upgrader1Item;
import net.rose.elementSMPRefined.items.recipes.Upgrader2Item;
import net.rose.elementSMPRefined.ElementSMPRefined;

public class UtilRecipes {
    public static void registerRecipes(ElementSMPRefined plugin) {
        Upgrader1Item.registerRecipe(plugin);
        Upgrader2Item.registerRecipe(plugin);
        RerollerItem.registerRecipe(plugin);
        
        if (plugin.getConfigManager().isAdvancedRerollerRecipeEnabled()) {
            AdvancedRerollerItem.registerRecipe(plugin);
        }
    }
}

