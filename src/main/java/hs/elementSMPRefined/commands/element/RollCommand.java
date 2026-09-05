package hs.elementSMPRefined.commands.element;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.gui.ElementSelectionGUI;
import hs.elementSMPRefined.managers.ElementManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /element roll - OP-only manual reroll, mainly for testing. */
public class RollCommand implements ElementSubCommand {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;

    public RollCommand(ElementSMPRefined plugin, ElementManager elementManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (!player.isOp()) {
            sender.sendMessage(ChatColor.RED + "You must be OP to use this command.");
            return true;
        }

        if (elementManager.isCurrentlyRolling(player)) {
            player.sendMessage(ChatColor.RED + "You are already rolling for an element!");
            return true;
        }

        new ElementSelectionGUI(plugin, player, true).open();
        player.sendMessage(ChatColor.GREEN + "Rolling for a new element...");

        return true;
    }
}
