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

        if (toggle == PassiveToggle.WIND_BURST) {
            handleWindBurst(player, data, args);
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

    /**
     * Wind Burst has three strengths. {@code /toggle windburst} steps off > I > II > III > off;
     * {@code /toggle windburst 1|2|3} picks one, {@code on}/{@code off} keep the chosen level.
     */
    private void handleWindBurst(Player player, PlayerData data, String[] args) {
        if (args.length == 1) {
            if (!data.isToggleEnabled(PassiveToggle.WIND_BURST)) {
                data.setToggleEnabled(PassiveToggle.WIND_BURST, true);
                data.setWindBurstLevel(1);
            } else if (data.getWindBurstLevel() < PlayerData.MAX_WIND_BURST_LEVEL) {
                data.setWindBurstLevel(data.getWindBurstLevel() + 1);
            } else {
                data.setToggleEnabled(PassiveToggle.WIND_BURST, false);
            }
        } else {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "1", "i" -> setWindBurstLevel(data, 1);
                case "2", "ii" -> setWindBurstLevel(data, 2);
                case "3", "iii" -> setWindBurstLevel(data, 3);
                case "on", "enable", "true" -> data.setToggleEnabled(PassiveToggle.WIND_BURST, true);
                case "off", "disable", "false" -> data.setToggleEnabled(PassiveToggle.WIND_BURST, false);
                default -> {
                    player.sendMessage(Lang.TOGGLE_WINDBURST_USAGE);
                    return;
                }
            }
        }

        plugin.getDataStore().saveAsync(data);
        if (data.isToggleEnabled(PassiveToggle.WIND_BURST)) {
            player.sendMessage(Lang.windBurstLevelChanged(data.getWindBurstLevel()));
        } else {
            player.sendMessage(Lang.toggleChanged(PassiveToggle.WIND_BURST.displayName(), false));
        }
    }

    private static void setWindBurstLevel(PlayerData data, int level) {
        data.setToggleEnabled(PassiveToggle.WIND_BURST, true);
        data.setWindBurstLevel(level);
    }

    private void sendList(Player player, PlayerData data, ElementType current) {
        List<PassiveToggle> available = availableFor(current);
        if (available.isEmpty()) {
            player.sendMessage(Lang.TOGGLE_NONE_AVAILABLE);
            return;
        }
        player.sendMessage(Lang.TOGGLE_LIST_HEADER);
        for (PassiveToggle toggle : available) {
            if (toggle == PassiveToggle.WIND_BURST) {
                player.sendMessage(Lang.toggleListEntryLevel(toggle.id(), toggle.displayName(),
                        data.isToggleEnabled(toggle), data.getWindBurstLevel()));
            } else {
                player.sendMessage(Lang.toggleListEntry(toggle.id(), toggle.displayName(), data.isToggleEnabled(toggle)));
            }
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
            List<String> options = PassiveToggle.parse(args[0]).filter(t -> t == PassiveToggle.WIND_BURST).isPresent()
                    ? List.of("1", "2", "3", "on", "off")
                    : List.of("on", "off");
            return options.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
