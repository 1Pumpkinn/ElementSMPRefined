package net.rose.elementSMPRefined.commands.element.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * {@code /event <args>} - shortcut for {@code /element event <args>}. It hands the arguments to
 * {@link EventCommand} unchanged, so both forms behave and tab complete identically.
 */
public class EventAliasCommand implements CommandExecutor, TabCompleter {
    private static final String SUB = "event";

    private final EventCommand delegate;

    public EventAliasCommand(ElementSMPRefined plugin) {
        this.delegate = new EventCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("element.admin")) {
            sender.sendMessage(
                    Component.text(
                            "You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        return delegate.execute(sender, withSub(args));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("element.admin")) {
            return List.of();
        }
        return delegate.tabComplete(sender, withSub(args));
    }

    private static String[] withSub(String[] args) {
        String[] full = new String[args.length + 1];
        full[0] = SUB;
        System.arraycopy(args, 0, full, 1, args.length);
        return full;
    }
}
