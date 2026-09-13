package net.rose.elementSMPRefined.commands.element;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.ElementSMPRefined;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** /element bot spawn <element> | /element bot stop */
public class BotCommand implements ElementSubCommand {
    private final ElementSMPRefined plugin;

    public BotCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /element bot spawn <element> or /element bot stop");
            return true;
        }
        if (args[1].equalsIgnoreCase("stop")) {
            plugin.getElementBotManager().stop(player);
            player.sendMessage(ChatColor.YELLOW + "Your elemental bot was removed.");
            return true;
        }
        if (!args[1].equalsIgnoreCase("spawn") || args.length < 3) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /element bot spawn <element> or /element bot stop");
            return true;
        }
        Optional<ElementType> element = CommandSupport.parseElementType(args[2]);
        if (element.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Unknown element. Choose: " + String.join(", ", CommandSupport.getElementNames()));
            return true;
        }
        plugin.getElementBotManager().spawn(player, element.get());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return CommandSupport.filterStartingWith(List.of("spawn", "stop"), args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("spawn")) {
            return CommandSupport.getElementNames(args[2]);
        }
        return Collections.emptyList();
    }
}
