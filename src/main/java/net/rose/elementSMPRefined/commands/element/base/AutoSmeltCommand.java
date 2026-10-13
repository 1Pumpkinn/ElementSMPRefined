package net.rose.elementSMPRefined.commands.element.base;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * /autosmelt [on|off] - toggles the Fire / Lava auto smelt passive for the
 * player running it. With no argument it flips the current state. The choice
 * is stored on {@link PlayerData} so it survives restarts.
 */
public class AutoSmeltCommand implements CommandExecutor, TabCompleter {

    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;

    public AutoSmeltCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
        this.elementManager = plugin.getElementManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.AUTOSMELT_PLAYERS_ONLY);
            return true;
        }

        PlayerData data = elementManager.data(player.getUniqueId());
        ElementType current = data == null ? null : data.getCurrentElement();
        if (current != ElementType.FIRE && current != ElementType.LAVA) {
            player.sendMessage(Lang.AUTOSMELT_WRONG_ELEMENT);
            return true;
        }

        boolean enable;
        if (args.length == 0) {
            enable = !data.isAutoSmeltEnabled();
        } else {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "on", "enable", "true" -> enable = true;
                case "off", "disable", "false" -> enable = false;
                default -> {
                    player.sendMessage(Lang.AUTOSMELT_USAGE);
                    return true;
                }
            }
        }

        data.setAutoSmeltEnabled(enable);
        plugin.getDataStore().saveAsync(data);
        player.sendMessage(enable ? Lang.AUTOSMELT_ENABLED : Lang.AUTOSMELT_DISABLED);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("on", "off").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
