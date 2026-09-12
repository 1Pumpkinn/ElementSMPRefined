package rose.elementSMPRefined.commands.element;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * One /element subcommand. Implementations own both execution and tab
 * completion for their own arguments, so the two can't quietly drift apart
 * the way they used to when tab-completion for every subcommand lived in one
 * big switch statement in ElementCommand, separate from the execute() logic
 * that actually defined each command's argument shape.
 */
public interface ElementSubCommand {
    boolean execute(CommandSender sender, String[] args);

    /**
     * Suggestions for the argument currently being typed. {@code args[0]} is
     * always this subcommand's own name; {@code args.length} tells you which
     * position is being completed. Default: no suggestions.
     */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
