package net.rose.elementSMPRefined.commands.element.base;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.data.PassiveToggle;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * /toggle [passive] [on|off] - one command for every passive a player can switch off.
 * <ul>
 *     <li>{@code /toggle} lists the toggles that apply to the player's element and their state.</li>
 *     <li>{@code /toggle firesmelt} flips Fire Smelt (Fire / Lava), {@code /toggle windburst}
 *     flips Wind Burst (Air / Storm); add {@code on} or {@code off} to set it explicitly.</li>
 * </ul>
 * The choices are stored on {@link PlayerData} so they survive restarts. New toggles are added in
 * {@link PassiveToggle} and show up here automatically.
 */
public class ToggleCommand implements CommandExecutor, TabCompleter {

    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;

    public ToggleCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
        this.elementManager = plugin.getElementManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.TOGGLE_PLAYERS_ONLY);
            return true;
        }

        PlayerData data = elementManager.data(player.getUniqueId());
        ElementType current = data == null ? null : data.getCurrentElement();
        if (current == null) {
            player.sendMessage(Lang.TOGGLE_NO_ELEMENT);
            return true;
        }

        if (args.length == 0) {
            sendList(player, data, current);
            return true;
        }

        Optional<PassiveToggle> parsed = PassiveToggle.parse(args[0]);
        if (parsed.isEmpty()) {
            player.sendMessage(Lang.toggleUnknown(args[0]));
            return true;
        }
        PassiveToggle toggle = parsed.get();

        if (!toggle.appliesTo(current)) {
            player.sendMessage(Lang.toggleWrongElement(toggle.displayName(), toggle.elementNames()));
            return true;
        }

        boolean enable;
        if (args.length == 1) {
            enable = !data.isToggleEnabled(toggle);
        } else {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "on", "enable", "true" -> enable = true;
                case "off", "disable", "false" -> enable = false;
                default -> {
                    player.sendMessage(Lang.TOGGLE_USAGE);
                    return true;
                }
            }
        }

        data.setToggleEnabled(toggle, enable);
        plugin.getDataStore().saveAsync(data);
        player.sendMessage(Lang.toggleChanged(toggle.displayName(), enable));
        return true;
    }

    private void sendList(Player player, PlayerData data, ElementType current) {
        List<PassiveToggle> available = availableFor(current);
        if (available.isEmpty()) {
            player.sendMessage(Lang.TOGGLE_NONE_AVAILABLE);
            return;
        }
        player.sendMessage(Lang.TOGGLE_LIST_HEADER);
        for (PassiveToggle toggle : available) {
            player.sendMessage(Lang.toggleListEntry(toggle.id(), toggle.displayName(), data.isToggleEnabled(toggle)));
        }
    }

    private static List<PassiveToggle> availableFor(ElementType element) {
        return Arrays.stream(PassiveToggle.values()).filter(t -> t.appliesTo(element)).toList();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            PlayerData data = elementManager.data(player.getUniqueId());
            ElementType current = data == null ? null : data.getCurrentElement();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return availableFor(current).stream()
                    .map(PassiveToggle::id)
                    .filter(id -> id.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("on", "off").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
