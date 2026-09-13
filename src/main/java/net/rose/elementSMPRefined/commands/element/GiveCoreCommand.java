package net.rose.elementSMPRefined.commands.element;

import net.rose.elementSMPRefined.API.element.ElementType;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.items.builder.ElementCoreItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** /element givecore <player> <element> */
public class GiveCoreCommand implements ElementSubCommand {
    private final ElementSMPRefined plugin;

    public GiveCoreCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /element givecore <player> <element>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found.");
            return true;
        }

        Optional<ElementType> elementType = CommandSupport.parseElementType(args[2]);
        if (elementType.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Invalid element. Valid: " + String.join(", ", CommandSupport.getElementNames()));
            return true;
        }

        ItemStack core = ElementCoreItem.createCore(plugin, elementType.get());
        if (core == null) {
            sender.sendMessage(ChatColor.RED + elementType.get().name() + " doesn't have a core item.");
            return true;
        }

        target.getInventory().addItem(core);

        sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " a " +
                ChatColor.AQUA + elementType.get().name() + ChatColor.GREEN + " core.");
        target.sendMessage(ChatColor.GREEN + "You received a " +
                ChatColor.AQUA + elementType.get().name() + ChatColor.GREEN + " core from an admin.");

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return CommandSupport.getOnlinePlayerNames(args[1]);
        }
        if (args.length == 3) {
            return CommandSupport.getElementNames(args[2]);
        }
        return Collections.emptyList();
    }
}
