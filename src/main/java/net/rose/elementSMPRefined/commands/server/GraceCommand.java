package net.rose.elementSMPRefined.commands.server;

import net.kyori.adventure.bossbar.BossBar;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.commands.supporters.CommandSupport;
import net.rose.elementSMPRefined.commands.supporters.ConfigOption;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.util.server.GracePeriod;
import net.rose.elementSMPRefined.util.server.ParseUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 * /grace start [time]                  start (time defaults to config.yml)
 * /grace stop                          end it early
 * /grace status                        time left
 * /grace extend &lt;time&gt;                 add time to the running grace period
 * /grace shorten &lt;time&gt;                remove time from the running grace period
 * /grace settings                      show every option and its current value
 * /grace set &lt;option&gt; &lt;value&gt;         change an option (saved to config.yml)
 * </pre>
 * Times take plain seconds or units: {@code 90}, {@code 10m}, {@code 1h30m}.
 * <p>
 * Thin command wrapper around {@link GracePeriod}, which does all the actual
 * work (boss bar, PvP protection, ticking). Fetched fresh from
 * {@link net.rose.elementSMPRefined.core.initializers.ListenerInitializer}
 * on every invocation rather than cached at construction time, since
 * commands are registered before {@code GracePeriod} is created during
 * plugin startup.
 */
public class GraceCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS =
            List.of("start", "stop", "status", "extend", "shorten", "settings", "set");

    /** Everything {@code /grace set} can change. The boss bar colour applies to the next start. */
    private static final List<ConfigOption> OPTIONS = List.of(
            ConfigOption.duration("duration", "grace_period.duration_seconds"),
            ConfigOption.choice("bossbar_color", "grace_period.boss_bar.color", BossBar.Color.values()),
            ConfigOption.bool("disable_nether_on_start", "grace_period.on_start.disable_nether"),
            ConfigOption.bool("disable_end_on_start", "grace_period.on_start.disable_end"),
            ConfigOption.bool("open_nether_on_end", "grace_period.on_end.enable_nether"),
            ConfigOption.bool("open_end_on_end", "grace_period.on_end.enable_end")
    );

    private final ElementSMPRefined plugin;

    public GraceCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("element.admin")) {
            sender.sendMessage(Lang.GRACE_CMD_USAGE);
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Lang.GRACE_CMD_USAGE);
            return true;
        }

        GracePeriod gracePeriod = plugin.getListenerInitializer().getGracePeriod();
        if (gracePeriod == null) {
            sender.sendMessage(Lang.GRACE_CMD_USAGE);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(sender, gracePeriod, args);
            case "stop" -> handleStop(sender, gracePeriod);
            case "status" -> handleStatus(sender, gracePeriod);
            case "extend" -> handleAdjust(sender, gracePeriod, args, 1, "extended");
            case "shorten" -> handleAdjust(sender, gracePeriod, args, -1, "shortened");
            case "settings" -> handleSettings(sender);
            case "set" -> handleSet(sender, args);
            default -> sender.sendMessage(Lang.GRACE_CMD_USAGE);
        }

        return true;
    }

    private void handleStart(CommandSender sender, GracePeriod gracePeriod, String[] args) {
        ConfigManager config = plugin.getConfigManager();
        int durationSeconds = config.getGracePeriodDurationSeconds();

        if (args.length >= 2) {
            Integer parsedDuration = ParseUtil.parseSeconds(args[1], false);
            if (parsedDuration == null) {
                sender.sendMessage(Lang.GRACE_CMD_INVALID_NUMBER);
                return;
            }
            durationSeconds = parsedDuration;
        }

        boolean started = gracePeriod.start(durationSeconds);
        if (!started) {
            sender.sendMessage(Lang.GRACE_CMD_ALREADY_ACTIVE);
        }
    }

    private void handleStop(CommandSender sender, GracePeriod gracePeriod) {
        boolean stopped = gracePeriod.stop();
        if (!stopped) {
            sender.sendMessage(Lang.GRACE_CMD_NOT_ACTIVE);
        }
    }

    private void handleStatus(CommandSender sender, GracePeriod gracePeriod) {
        if (!gracePeriod.isActive()) {
            sender.sendMessage(Lang.GRACE_CMD_NOT_ACTIVE);
            return;
        }
        sender.sendMessage(Lang.graceCmdStatus(ParseUtil.formatClock(gracePeriod.getRemainingSeconds())));
    }

    private void handleAdjust(CommandSender sender, GracePeriod gracePeriod, String[] args, int sign, String verb) {
        if (!gracePeriod.isActive()) {
            sender.sendMessage(Lang.GRACE_CMD_NOT_ACTIVE);
            return;
        }
        Integer seconds = args.length >= 2 ? ParseUtil.parseSeconds(args[1], false) : null;
        if (seconds == null) {
            sender.sendMessage(Lang.GRACE_CMD_USAGE);
            return;
        }
        if (!gracePeriod.adjustTime(sign * seconds)) {
            sender.sendMessage(Lang.GRACE_CMD_SHORTEN_TOO_FAR);
            return;
        }
        sender.sendMessage(Lang.graceCmdAdjusted(verb + " by " + ParseUtil.formatCompact(seconds),
                ParseUtil.formatClock(gracePeriod.getRemainingSeconds())));
    }

    private void handleSettings(CommandSender sender) {
        ConfigManager config = plugin.getConfigManager();
        sender.sendMessage(Lang.settingsHeader("Grace period settings"));
        for (ConfigOption option : OPTIONS) {
            sender.sendMessage(Lang.settingLine(option.name(), option.display(config.getValue(option.path()))));
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Lang.optionUsage("grace"));
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
        plugin.getConfigManager().setValue(option.path(), value);
        sender.sendMessage(Lang.optionSet(option.name(), option.display(value)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return CommandSupport.filterStartingWith(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            switch (sub) {
                case "start", "extend", "shorten" -> {
                    return CommandSupport.filterStartingWith(List.of("1m", "5m", "10m", "30m", "1h"), args[1]);
                }
                case "set" -> {
                    return CommandSupport.filterStartingWith(ConfigOption.names(OPTIONS), args[1]);
                }
                default -> {
                    return new ArrayList<>();
                }
            }
        }

        if (args.length == 3) {
            if (sub.equals("set")) {
                ConfigOption option = ConfigOption.find(OPTIONS, args[1]);
                if (option != null) {
                    return CommandSupport.filterStartingWith(option.suggestions(), args[2]);
                }
            }
        }
        return new ArrayList<>();
    }
}