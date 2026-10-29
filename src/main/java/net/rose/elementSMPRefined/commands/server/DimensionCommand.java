package net.rose.elementSMPRefined.commands.server;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.commands.supporters.CommandSupport;
import net.rose.elementSMPRefined.commands.supporters.ConfigOption;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.util.server.ParseUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <pre>
 * /dimension enable|disable|toggle &lt;nether|end|all&gt; [time]
 * /dimension status
 * /dimension settings
 * /dimension set &lt;option&gt; &lt;value&gt;
 * </pre>
 * Flips the {@code dimensions.nether_disabled} / {@code dimensions.end_disabled}
 * config flags that {@link net.rose.elementSMPRefined.util.server.DimensionDisable}
 * reads on every portal event. No listener reference needed here since that
 * class checks {@link ConfigManager} directly rather than caching state.
 * <p>
 * With the optional {@code [time]} (e.g. {@code /dimension enable end 30m}) the
 * change is temporary: once the time is up the dimension flips back and the
 * server is told. Pending timers live in memory only - they're dropped on a
 * restart/reload, leaving whatever state is saved in config.yml.
 * <p>
 * {@code set} edits the other {@code dimensions.*} options (warnings, pushback,
 * entity blocking, admin bypass); see {@link #OPTIONS}.
 */
public class DimensionCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ACTIONS = List.of("enable", "disable", "toggle", "status", "settings", "set");
    private static final List<String> TARGETS = List.of("nether", "end", "all");

    private static final List<ConfigOption> OPTIONS = List.of(
            ConfigOption.bool("warn_players", "dimensions.warn_players"),
            ConfigOption.durationOrZero("warning_cooldown", "dimensions.warning_cooldown_seconds"),
            ConfigOption.bool("pushback", "dimensions.pushback_enabled"),
            ConfigOption.decimal("pushback_strength", "dimensions.pushback_strength", 0.0, 5.0),
            ConfigOption.bool("block_entities", "dimensions.block_entities"),
            ConfigOption.bool("admin_bypass", "dimensions.admin_bypass")
    );

    private record PendingFlip(BukkitTask task, long endsAtMs) {}

    private final ElementSMPRefined plugin;
    private final Map<String, PendingFlip> pendingFlips = new HashMap<>();

    public DimensionCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("element.admin")) {
            sender.sendMessage(Lang.DIMENSION_CMD_USAGE);
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Lang.DIMENSION_CMD_USAGE);
            return true;
        }

        String action = args[0].toLowerCase();
        ConfigManager configManager = plugin.getConfigManager();

        switch (action) {
            case "status" -> sendStatus(sender, configManager);
            case "settings" -> sendSettings(sender, configManager);
            case "set" -> handleSet(sender, configManager, args);
            case "enable", "disable", "toggle" -> handleToggle(sender, configManager, action, args);
            default -> sender.sendMessage(Lang.DIMENSION_CMD_USAGE);
        }

        return true;
    }

    private void handleToggle(CommandSender sender, ConfigManager configManager, String action, String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(Lang.DIMENSION_CMD_USAGE);
            return;
        }

        String target = args[1].toLowerCase();
        if (!TARGETS.contains(target)) {
            sender.sendMessage(Lang.DIMENSION_CMD_INVALID_TARGET);
            return;
        }

        Integer durationSeconds = null;
        if (args.length == 3) {
            durationSeconds = ParseUtil.parseSeconds(args[2], false);
            if (durationSeconds == null) {
                sender.sendMessage(Lang.DIMENSION_CMD_INVALID_TIME);
                return;
            }
        }

        if (target.equals("all")) {
            // "toggle all" flips each dimension on its own so mixed states don't get stuck together.
            apply(sender, configManager, "nether", desiredState(action, configManager.isNetherDisabled()), durationSeconds);
            apply(sender, configManager, "end", desiredState(action, configManager.isEndDisabled()), durationSeconds);
        } else {
            boolean current = target.equals("nether") ? configManager.isNetherDisabled() : configManager.isEndDisabled();
            apply(sender, configManager, target, desiredState(action, current), durationSeconds);
        }
    }

    private static boolean desiredState(String action, boolean currentlyDisabled) {
        return switch (action) {
            case "disable" -> true;
            case "enable" -> false;
            default -> !currentlyDisabled;
        };
    }

    private void apply(CommandSender sender, ConfigManager configManager, String dimension, boolean disabled, Integer durationSeconds) {
        // Any manual change replaces whatever timer was pending for this dimension.
        cancelPending(dimension);
        setDisabled(configManager, dimension, disabled);

        String name = displayName(dimension);
        if (durationSeconds == null) {
            sender.sendMessage(Lang.dimensionCmdToggled(name, disabled));
            return;
        }

        BukkitTask task = plugin.getTaskScheduler().runLater(() -> {
            pendingFlips.remove(dimension);
            setDisabled(configManager, dimension, !disabled);
            plugin.getServer().broadcast(Lang.dimensionTimerEnded(name, !disabled));
        }, durationSeconds * 20L);
        pendingFlips.put(dimension, new PendingFlip(task, System.currentTimeMillis() + durationSeconds * 1000L));

        sender.sendMessage(Lang.dimensionCmdToggledTimed(name, disabled, ParseUtil.formatCompact(durationSeconds)));
    }

    private void cancelPending(String dimension) {
        PendingFlip pending = pendingFlips.remove(dimension);
        if (pending != null) {
            pending.task().cancel();
        }
    }

    private static void setDisabled(ConfigManager configManager, String dimension, boolean disabled) {
        if (dimension.equals("nether")) {
            configManager.setNetherDisabled(disabled);
        } else {
            configManager.setEndDisabled(disabled);
        }
    }

    private static String displayName(String dimension) {
        return dimension.equals("nether") ? "Nether" : "End";
    }

    private void sendStatus(CommandSender sender, ConfigManager configManager) {
        sender.sendMessage(Lang.dimensionStatusLine("Nether", configManager.isNetherDisabled(), timeLeft("nether")));
        sender.sendMessage(Lang.dimensionStatusLine("End", configManager.isEndDisabled(), timeLeft("end")));
    }

    private String timeLeft(String dimension) {
        PendingFlip pending = pendingFlips.get(dimension);
        if (pending == null) {
            return null;
        }
        long secondsLeft = Math.max(0, (pending.endsAtMs() - System.currentTimeMillis() + 999) / 1000);
        return ParseUtil.formatCompact(secondsLeft);
    }

    private void sendSettings(CommandSender sender, ConfigManager configManager) {
        sender.sendMessage(Lang.settingsHeader("Dimension settings"));
        sendStatus(sender, configManager);
        for (ConfigOption option : OPTIONS) {
            sender.sendMessage(Lang.settingLine(option.name(), option.display(configManager.getValue(option.path()))));
        }
    }

    private void handleSet(CommandSender sender, ConfigManager configManager, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Lang.optionUsage("dimension"));
            return;
        }
        ConfigOption option = ConfigOption.find(OPTIONS, args[1]);
        if (option == null) {
            sender.sendMessage(Lang.optionUnknown(args[1], String.join(", ", ConfigOption.names(OPTIONS))));
            return;
        }
        Object value = option.parse(args[2]);
        if (value == null) {
            sender.sendMessage(Lang.optionInvalidValue(option.name(), option.expected()));
            return;
        }
        configManager.setValue(option.path(), value);
        sender.sendMessage(Lang.optionSet(option.name(), option.display(value)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return CommandSupport.filterStartingWith(ACTIONS, args[0]);
        }

        String action = args[0].toLowerCase();
        boolean isToggle = action.equals("enable") || action.equals("disable") || action.equals("toggle");

        if (args.length == 2) {
            if (isToggle) {
                return CommandSupport.filterStartingWith(TARGETS, args[1]);
            }
            if (action.equals("set")) {
                return CommandSupport.filterStartingWith(ConfigOption.names(OPTIONS), args[1]);
            }
        }

        if (args.length == 3) {
            if (isToggle) {
                return CommandSupport.filterStartingWith(List.of("5m", "30m", "1h", "1d"), args[2]);
            }
            if (action.equals("set")) {
                ConfigOption option = ConfigOption.find(OPTIONS, args[1]);
                if (option != null) {
                    return CommandSupport.filterStartingWith(option.suggestions(), args[2]);
                }
            }
        }
        return new ArrayList<>();
    }
}