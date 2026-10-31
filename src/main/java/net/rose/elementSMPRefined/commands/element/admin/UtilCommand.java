package net.rose.elementSMPRefined.commands.element.admin;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.items.recipes.ForgedUpgraderItem;
import net.rose.elementSMPRefined.items.recipes.RerollerItem;
import net.rose.elementSMPRefined.items.recipes.UpgraderItem;
import net.rose.elementSMPRefined.lang.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class UtilCommand implements CommandExecutor {
    private final ElementSMPRefined plugin;

    public UtilCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.UTIL_THIS_COMMAND_CAN_ONLY_BE);
            return true;
        }

        if (!player.hasPermission("element.admin")) {
            player.sendMessage(Lang.UTIL_YOU_DON_T_HAVE_PERMISSION);
            return true;
        }

        ItemStack upgrader1Stack = UpgraderItem.make(plugin, 1);
        upgrader1Stack.setAmount(64);

        ItemStack upgrader2Stack = UpgraderItem.make(plugin, 2);
        upgrader2Stack.setAmount(64);

        ItemStack rerollerStack = RerollerItem.make(plugin);
        rerollerStack.setAmount(64);

        ItemStack forgedUpgraderStack = ForgedUpgraderItem.make(plugin);
        forgedUpgraderStack.setAmount(64);

        player.getInventory().addItem(upgrader1Stack, upgrader2Stack, rerollerStack, forgedUpgraderStack);

        player.sendMessage(Lang.UTIL_YOU_HAVE_BEEN_GIVEN_UTILITY);
        player.sendMessage(Lang.UTIL_64X_UPGRADER_I);
        player.sendMessage(Lang.UTIL_64X_UPGRADER_II);
        player.sendMessage(Lang.UTIL_64X_REROLLER);
        player.sendMessage(Lang.UTIL_64X_FORGED_UPGRADER);

        return true;
    }
}