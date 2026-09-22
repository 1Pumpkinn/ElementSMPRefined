package net.rose.elementSMPRefined.commands.element;

import net.rose.elementSMPRefined.core.API.element.ElementId;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** /element set <player> <element> */
public class SetCommand implements ElementSubCommand {
    private final ElementManager elementManager;

    public SetCommand(ElementManager elementManager) {
        this.elementManager = elementManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Lang.SET_USAGE_ELEMENT_SET_PLAYER_ELEMENT);
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Lang.setPlayer(args[1]));
            return true;
        }

        Optional<ElementId> elementId = CommandSupport.parseElementId(elementManager, args[2]);
        if (elementId.isEmpty()) {
            sender.sendMessage(Lang.setInvalidElementValid(String.join(", ", CommandSupport.getAllElementNames(elementManager))));
            return true;
        }

        ElementId id = elementId.get();
        elementManager.setElement(target, id);

        var element = elementManager.getElementRegistry().get(id);
        String displayName = element != null ? element.getDisplayName() : id.toString();

        sender.sendMessage(Lang.setSet(target.getName(), displayName));
        target.sendMessage(Lang.setYourElementHasBeenSet(displayName));

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return CommandSupport.getOnlinePlayerNames(args[1]);
        }
        if (args.length == 3) {
            return CommandSupport.filterStartingWith(CommandSupport.getAllElementNames(elementManager), args[2]);
        }
        return Collections.emptyList();
    }
}
