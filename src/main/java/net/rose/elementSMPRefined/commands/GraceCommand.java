package net.rose.elementSMPRefined.commands;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.util.server.GracePeriod;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /grace <start|stop|status> [duration_seconds] [hunger_protection_seconds]}
 * <p>
 * Thin command wrapper around {@link GracePeriod}, which does all the actual
 * work (boss bar, PvP/hunger protection, ticking). Fetched fresh from
 * {@link net.rose.elementSMPRefined.core.initializers.ListenerInitializer}
 * on every invocation rather than cached at construction time, since
 * commands are registered before {@code GracePeriod} is created during
 * plugin startup.
 */
public class GraceCommand implements CommandExecutor, TabCompleter {
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
            default -> sender.sendMessage(Lang.GRACE_CMD_USAGE);
        }

        return true;
    }

    private void handleStart(CommandSender sender, GracePeriod gracePeriod, String[] args) {
        int durationSeconds = plugin.getConfigManager().getGracePeriodDurationSeconds();
        int hungerProtectionSeconds = plugin.getConfigManager().getGracePeriodHungerProtectionSeconds();

        if (args.length >= 2) {
            Integer parsedDuration = parsePositiveInt(args[1]);
            if (parsedDuration == null) {
                sender.sendMessage(Lang.GRACE_CMD_INVALID_NUMBER);
                return;
            }
            durationSeconds = parsedDuration;
            // Re-derive the default hunger protection window against the
            // custom duration, in case only duration was overridden.
            hungerProtectionSeconds = Math.min(hungerProtectionSeconds, durationSeconds);
        }

        if (args.length >= 3) {
            Integer parsedHunger = parsePositiveInt(args[2]);
            if (parsedHunger == null) {
                sender.sendMessage(Lang.GRACE_CMD_INVALID_NUMBER);
                return;
            }
            hungerProtectionSeconds = parsedHunger;
        }

        boolean started = gracePeriod.start(durationSeconds, hungerProtectionSeconds);
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
        sender.sendMessage(Lang.graceCmdStatus(formatTime(gracePeriod.getRemainingSeconds())));
    }

    /** Returns null (instead of throwing) on anything that isn't a positive whole number. */
    private static Integer parsePositiveInt(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatTime(int seconds) {
        return "%02d:%02d".formatted(seconds / 60, seconds % 60);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("start", "stop", "status");
            String input = args[0].toLowerCase();
            return subcommands.stream()
                    .filter(s -> s.startsWith(input))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}