package rose.elementSMPRefined.recipes;

import rose.elementSMPRefined.items.recipes.AdvancedRerollerItem;
import rose.elementSMPRefined.items.recipes.RerollerItem;
import rose.elementSMPRefined.items.recipes.Upgrader1Item;
import rose.elementSMPRefined.items.recipes.Upgrader2Item;
import rose.elementSMPRefined.ElementSMPRefined;

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

