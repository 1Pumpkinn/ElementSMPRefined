package net.rose.elementSMPRefined.commands.element;

import net.rose.elementSMPRefined.core.API.element.ElementId;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.data.DataStore;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/** /element debug <player> */
public class DebugCommand implements ElementSubCommand {
    private final DataStore dataStore;
    private final ElementManager elementManager;

    public DebugCommand(DataStore dataStore, ElementManager elementManager) {
        this.dataStore = dataStore;
        this.elementManager = elementManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Lang.DEBUG_USAGE_ELEMENT_DEBUG_PLAYER);
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Lang.debugPlayer(args[1]));
            return true;
        }

        sender.sendMessage(Lang.debugElementDebug(target.getName()));

        ElementType managerElement = elementManager.getPlayerElement(target);
        sender.sendMessage(Lang.debugElementmanagerReportsBuiltinType((managerElement != null ? managerElement.name() : "null")));

        ElementId managerElementId = elementManager.getPlayerElementId(target);
        sender.sendMessage(Lang.debugElementmanagerReportsElementId((managerElementId != null ? managerElementId.toString() : "null")));

        dataStore.invalidateCache(target.getUniqueId());
        ElementType reloadedElement = elementManager.getPlayerElement(target);
        sender.sendMessage(Lang.debugAfterCacheInvalidation((reloadedElement != null ? reloadedElement.name() : "null")));

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return CommandSupport.getOnlinePlayerNames(args[1]);
        }
        return Collections.emptyList();
    }
}
