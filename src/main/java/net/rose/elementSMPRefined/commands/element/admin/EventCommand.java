package net.rose.elementSMPRefined.commands.element.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.commands.supporters.CommandSupport;
import net.rose.elementSMPRefined.commands.supporters.ElementSubCommand;
import net.rose.elementSMPRefined.util.parse.ParseUtil;
import net.rose.elementevents.mythic.MythicBook.Kind;
import net.rose.elementevents.lifedeath.LifeDeathEvent;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /element event <start <event_name> [duration]|end|cancel|status|give <life|death>
 * [player]>} - runs server events. Right now the only event is {@code life_death} (see {@link
 * LifeDeathEvent}); new events go into {@link #EVENT_ALIASES} and the switch in {@link
 * #start(CommandSender, String[])}.
 */
public class EventCommand implements ElementSubCommand {
    private static final List<String> SUBS = List.of("start", "end", "cancel", "status", "give");

    private static final String LIFE_DEATH = "life_death";
    private static final List<String> EVENT_NAMES = List.of(LIFE_DEATH);
    private static final Map<String, String> EVENT_ALIASES = Map.of("lifedeath", LIFE_DEATH);

    private final ElementSMPRefined plugin;

    public EventCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        LifeDeathEvent event = plugin.getLifeDeathEvent();
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";

        switch (sub) {
            case "start" -> start(sender, args);
            case "end" -> {
                if (!event.end()) {
                    sender.sendMessage(
                            Component.text("No event is running.", NamedTextColor.RED));
                }
            }
            case "cancel" -> {
                if (!event.cancel()) {
                    sender.sendMessage(
                            Component.text("No event is running.", NamedTextColor.RED));
                }
            }
            case "status" -> status(sender, event);
            case "give" -> give(sender, args, event);
            default ->
                    sender.sendMessage(
                            Component.text(
                                    "Usage: /element event <start <event_name> [duration]|end|cancel"
                                            + "|status|give <life|death> [player]>",
                                    NamedTextColor.YELLOW));
        }
        return true;
    }

    private void start(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendStartUsage(sender);
            return;
        }
        String name = resolveEventName(args[2]);
        if (name == null) {
            sender.sendMessage(Component.text("Unknown event: " + args[2], NamedTextColor.RED));
            sendStartUsage(sender);
            return;
        }
        switch (name) {
            case LIFE_DEATH -> startLifeDeath(sender, args);
            default -> sendStartUsage(sender);
        }
    }

    private void startLifeDeath(CommandSender sender, String[] args) {
        LifeDeathEvent event = plugin.getLifeDeathEvent();
        int seconds = event.defaultDurationSeconds();
        if (args.length > 3) {
            Integer parsed = ParseUtil.parseSeconds(args[3], false);
            if (parsed == null) {
                sender.sendMessage(
                        Component.text(
                                "Invalid duration. Examples: 30m, 1h30m, 90", NamedTextColor.RED));
                return;
            }
            seconds = parsed;
        }
        if (!event.start(seconds)) {
            sender.sendMessage(
                    Component.text(
                            "The event is already running. Use /element event end or cancel.",
                            NamedTextColor.RED));
            return;
        }
        for (Kind kind : Kind.values()) {
            if (plugin.getMythicElements().isClaimed(kind.element())) {
                sender.sendMessage(
                        Component.text(
                                "Note: the "
                                        + kind.elementName()
                                        + " is already claimed, so no "
                                        + kind.id()
                                        + " book will be awarded.",
                                NamedTextColor.YELLOW));
            }
        }
    }

    private void sendStartUsage(CommandSender sender) {
        sender.sendMessage(
                Component.text(
                        "Usage: /element event start <event_name> [duration]",
                        NamedTextColor.YELLOW));
        sender.sendMessage(
                Component.text(
                        "Events: " + String.join(", ", EVENT_NAMES), NamedTextColor.YELLOW));
    }

    /** @return the canonical event name for {@code raw} (ignoring case, '-' and '_'), or null. */
    private static String resolveEventName(String raw) {
        String key = raw.toLowerCase(Locale.ROOT).replaceAll("[-_\\s]", "");
        if (EVENT_ALIASES.containsKey(key)) return EVENT_ALIASES.get(key);
        return null;
    }

    private void status(CommandSender sender, LifeDeathEvent event) {
        if (!event.isActive()) {
            sender.sendMessage(Component.text("No event is running.", NamedTextColor.YELLOW));
            return;
        }
        if (sender instanceof Player player) {
            event.showStandings(player);
            return;
        }
        sender.sendMessage(
                Component.text(
                        "Life & Death event - "
                                + ParseUtil.formatCompact(event.getRemainingSeconds())
                                + " left",
                        NamedTextColor.GOLD));
        for (Kind kind : Kind.values()) {
            Map<String, Integer> top = event.standings(kind);
            StringBuilder line = new StringBuilder(kind.id().toUpperCase(Locale.ROOT) + ": ");
            if (top.isEmpty()) {
                line.append("nobody yet");
            } else {
                int shown = 0;
                for (Map.Entry<String, Integer> e : top.entrySet()) {
                    if (shown++ == 5) break;
                    line.append(shown > 1 ? ", " : "").append(e.getKey()).append(" ").append(e.getValue());
                }
            }
            sender.sendMessage(
                    Component.text(
                            line.toString(),
                            kind == Kind.LIFE ? NamedTextColor.GREEN : NamedTextColor.DARK_PURPLE));
        }
    }

    private void give(CommandSender sender, String[] args, LifeDeathEvent event) {
        Kind kind = args.length > 2 ? Kind.parse(args[2]) : null;
        if (kind == null) {
            sender.sendMessage(
                    Component.text(
                            "Usage: /element event give <life|death> [player]",
                            NamedTextColor.YELLOW));
            return;
        }
        Player target;
        if (args.length > 3) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                sender.sendMessage(
                        Component.text("Not online: " + args[3], NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(Component.text("Specify a player.", NamedTextColor.RED));
            return;
        }
        event.give(target, kind);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) return CommandSupport.filterStartingWith(SUBS, args[1]);
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("give")) {
            if (args.length == 3) {
                return CommandSupport.filterStartingWith(List.of("life", "death"), args[2]);
            }
            if (args.length == 4) return CommandSupport.getOnlinePlayerNames(args[3]);
        }
        if (sub.equals("start")) {
            if (args.length == 3) return CommandSupport.filterStartingWith(EVENT_NAMES, args[2]);
            if (args.length == 4 && LIFE_DEATH.equals(resolveEventName(args[2]))) {
                return CommandSupport.filterStartingWith(List.of("10m", "30m", "1h"), args[3]);
            }
        }
        return List.of();
    }
}
