package net.rose.elementSMPRefined.commands.element.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.commands.supporters.CommandSupport;
import net.rose.elementSMPRefined.commands.supporters.ElementSubCommand;
import net.rose.elementSMPRefined.util.parse.ParseUtil;
import net.rose.elementevents.api.EventManager;
import net.rose.elementevents.api.ServerEvent;
import net.rose.elementevents.lifedeath.LifeDeathEvent;
import net.rose.elementevents.mythic.MythicBook.Kind;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /element event <start <event> [duration]|end [event]|cancel [event]|status [event]|list
 * |give <life|death> [player]>} - runs server events.
 * <p>
 * Events come from the {@link EventManager}: registering a new {@link ServerEvent} there is all it
 * takes for it to show up here, with tab completion. {@code end}, {@code cancel} and {@code status}
 * can leave the event out when exactly one is running.
 */
public class EventCommand implements ElementSubCommand {
    private static final List<String> SUBS =
            List.of("start", "end", "cancel", "status", "list", "give");
    private static final List<String> DURATION_SUGGESTIONS = List.of("10m", "30m", "1h");

    private final ElementSMPRefined plugin;

    public EventCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    private EventManager events() {
        return plugin.getEventManager();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";

        switch (sub) {
            case "start" -> start(sender, args);
            case "end" -> targetOf(sender, args).ifPresent(event -> finish(sender, event, true));
            case "cancel" ->
                    targetOf(sender, args).ifPresent(event -> finish(sender, event, false));
            case "status" -> status(sender, args);
            case "list" -> list(sender);
            case "give" -> give(sender, args);
            default ->
                    sender.sendMessage(
                            Component.text(
                                    "Usage: /element event <start <event> [duration]|end [event]"
                                            + "|cancel [event]|status [event]|list"
                                            + "|give <life|death> [player]>",
                                    NamedTextColor.YELLOW));
        }
        return true;
    }

    private void start(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendStartUsage(sender);
            return;
        }
        Optional<ServerEvent> found = events().find(args[2]);
        if (found.isEmpty()) {
            sender.sendMessage(Component.text("Unknown event: " + args[2], NamedTextColor.RED));
            sendStartUsage(sender);
            return;
        }
        ServerEvent event = found.get();

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

        if (event.isActive()) {
            sender.sendMessage(
                    Component.text(
                            "The " + event.getId() + " event is already running. Use /element"
                                    + " event end or cancel.",
                            NamedTextColor.RED));
            return;
        }
        if (!event.start(seconds)) {
            sender.sendMessage(
                    Component.text(
                            "The " + event.getId() + " event was not started (cancelled by"
                                    + " another plugin).",
                            NamedTextColor.RED));
            return;
        }
        event.startNotes().forEach(sender::sendMessage);
    }

    private void sendStartUsage(CommandSender sender) {
        sender.sendMessage(
                Component.text(
                        "Usage: /element event start <event> [duration]", NamedTextColor.YELLOW));
        sender.sendMessage(
                Component.text(
                        "Events: " + String.join(", ", events().ids()), NamedTextColor.YELLOW));
    }

    /**
     * The event {@code end}/{@code cancel} applies to: the one named in {@code args[2]}, or the
     * only running one. Tells the sender why and returns empty when there is no single target.
     */
    private Optional<ServerEvent> targetOf(CommandSender sender, String[] args) {
        if (args.length > 2) {
            Optional<ServerEvent> found = events().find(args[2]);
            if (found.isEmpty()) {
                sender.sendMessage(
                        Component.text("Unknown event: " + args[2], NamedTextColor.RED));
            }
            return found;
        }

        List<ServerEvent> running = events().active();
        if (running.isEmpty()) {
            sender.sendMessage(Component.text("No event is running.", NamedTextColor.RED));
            return Optional.empty();
        }
        if (running.size() > 1) {
            sender.sendMessage(
                    Component.text(
                            "Several events are running - name one: "
                                    + String.join(
                                            ", ", running.stream().map(ServerEvent::getId).toList()),
                            NamedTextColor.RED));
            return Optional.empty();
        }
        return Optional.of(running.getFirst());
    }

    private void finish(CommandSender sender, ServerEvent event, boolean award) {
        boolean done = award ? event.end() : event.cancel();
        if (!done) {
            sender.sendMessage(
                    Component.text(
                            "The " + event.getId() + " event is not running.",
                            NamedTextColor.RED));
        }
    }

    private void status(CommandSender sender, String[] args) {
        if (args.length > 2) {
            Optional<ServerEvent> found = events().find(args[2]);
            if (found.isEmpty()) {
                sender.sendMessage(
                        Component.text("Unknown event: " + args[2], NamedTextColor.RED));
                return;
            }
            found.get().sendStatus(sender);
            return;
        }

        List<ServerEvent> running = events().active();
        if (running.isEmpty()) {
            sender.sendMessage(Component.text("No event is running.", NamedTextColor.YELLOW));
            return;
        }
        running.forEach(event -> event.sendStatus(sender));
    }

    private void list(CommandSender sender) {
        sender.sendMessage(Component.text("Events:", NamedTextColor.GOLD));
        for (ServerEvent event : events().all()) {
            String state =
                    event.isActive()
                            ? "running, " + ParseUtil.formatCompact(event.getRemainingSeconds())
                                    + " left"
                            : "idle (default " + ParseUtil.formatCompact(event.defaultDurationSeconds())
                                    + ")";
            sender.sendMessage(
                    Component.text(
                            " - " + event.getId() + ": " + state,
                            event.isActive() ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
    }

    private void give(CommandSender sender, String[] args) {
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
                sender.sendMessage(Component.text("Not online: " + args[3], NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(Component.text("Specify a player.", NamedTextColor.RED));
            return;
        }
        // Books are handed out the same way the Life & Death event awards them.
        Optional<LifeDeathEvent> lifeDeath = events().get(LifeDeathEvent.class);
        if (lifeDeath.isEmpty()) {
            sender.sendMessage(
                    Component.text("The Life & Death event is not registered.", NamedTextColor.RED));
            return;
        }
        lifeDeath.get().give(target, kind);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) return CommandSupport.filterStartingWith(SUBS, args[1]);
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> {
                if (args.length == 3) {
                    return CommandSupport.filterStartingWith(List.of("life", "death"), args[2]);
                }
                if (args.length == 4) return CommandSupport.getOnlinePlayerNames(args[3]);
            }
            case "start" -> {
                if (args.length == 3) {
                    return CommandSupport.filterStartingWith(events().ids(), args[2]);
                }
                if (args.length == 4 && events().find(args[2]).isPresent()) {
                    return CommandSupport.filterStartingWith(DURATION_SUGGESTIONS, args[3]);
                }
            }
            case "end", "cancel", "status" -> {
                if (args.length == 3) {
                    return CommandSupport.filterStartingWith(events().ids(), args[2]);
                }
            }
            default -> {}
        }
        return List.of();
    }
}
