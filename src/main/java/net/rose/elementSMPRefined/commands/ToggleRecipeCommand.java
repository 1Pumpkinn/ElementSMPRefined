package net.rose.elementSMPRefined.commands;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.items.recipes.AdvancedRerollerItem;
import net.rose.elementSMPRefined.items.recipes.RerollerItem;
import net.rose.elementSMPRefined.items.recipes.Upgrader1Item;
import net.rose.elementSMPRefined.items.recipes.Upgrader2Item;
import net.rose.elementSMPRefined.lang.Lang;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ToggleRecipeCommand implements CommandExecutor, TabCompleter {
    private final ElementSMPRefined plugin;

    public ToggleRecipeCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Lang.TOGGLE_RECIPE_PLAYERS_ONLY);
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("element.admin")) {
            player.sendMessage(Lang.TOGGLE_RECIPE_NO_PERMISSION);
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(Lang.TOGGLE_RECIPE_USAGE);
            return true;
        }

        String recipeType = args[0].toLowerCase();
        NamespacedKey recipeKey;
        boolean newState;

        switch (recipeType) {
            case "upgrader1":
                recipeKey = new NamespacedKey(plugin, Upgrader1Item.KEY);
                newState = toggleRecipe(recipeKey, () -> Upgrader1Item.registerRecipe(plugin));
                player.sendMessage(Lang.recipeToggled("Upgrader I", newState));
                break;

            case "upgrader2":
                recipeKey = new NamespacedKey(plugin, Upgrader2Item.KEY);
                newState = toggleRecipe(recipeKey, () -> Upgrader2Item.registerRecipe(plugin));
                player.sendMessage(Lang.recipeToggled("Upgrader II", newState));
                break;

            case "reroller":
                recipeKey = new NamespacedKey(plugin, RerollerItem.KEY);
                newState = toggleRecipe(recipeKey, () -> RerollerItem.registerRecipe(plugin));
                player.sendMessage(Lang.recipeToggled("Reroller", newState));
                break;

            case "advancedreroller":
                recipeKey = new NamespacedKey(plugin, AdvancedRerollerItem.KEY);
                boolean currentState = plugin.getConfigManager().isAdvancedRerollerRecipeEnabled();
                newState = !currentState;

                plugin.getConfigManager().setAdvancedRerollerRecipeEnabled(newState);

                if (newState) {
                    AdvancedRerollerItem.registerRecipe(plugin);
                    player.sendMessage(Lang.recipeToggled("Advanced Reroller", true));
                } else {
                    plugin.getServer().removeRecipe(recipeKey);
                    player.sendMessage(Lang.recipeToggled("Advanced Reroller", false));
                }
                break;

            default:
                player.sendMessage(Lang.TOGGLE_RECIPE_INVALID_TYPE);
                return true;
        }

        return true;
    }

    /**
     * Toggle a recipe on/off
     * @param key The recipe key
     * @param registerAction Action to register the recipe
     * @return true if recipe is now enabled, false if disabled
     */
    private boolean toggleRecipe(NamespacedKey key, Runnable registerAction) {
        // Check if recipe exists
        boolean exists = plugin.getServer().getRecipe(key) != null;

        if (exists) {
            // Recipe exists, remove it
            plugin.getServer().removeRecipe(key);
            return false;
        } else {
            // Recipe doesn't exist, register it
            registerAction.run();
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> recipeTypes = Arrays.asList("upgrader1", "upgrader2", "reroller", "advancedreroller");
            String input = args[0].toLowerCase();

            completions = recipeTypes.stream()
                    .filter(type -> type.startsWith(input))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}