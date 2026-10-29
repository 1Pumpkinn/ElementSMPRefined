package net.rose.elementSMPRefined.commands;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.lang.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TrustCommand implements CommandExecutor, TabCompleter {
    private final ElementSMPRefined plugin;
    private final TrustManager trust;

    public TrustCommand(ElementSMPRefined plugin, TrustManager trust) {
        this.plugin = plugin;
        this.trust = trust;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Lang.TRUST_PLAYERS_ONLY);
            return true;
        }
        if (args.length == 0) {
            p.sendMessage(Lang.TRUST_USAGE_TRUST_LIST_ADD_REMOVE);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "list" -> {
                List<String> names = new ArrayList<>();
                List<String> unknown = new ArrayList<>();
                for (UUID u : trust.getTrusted(p.getUniqueId())) {
                    String name = trust.getName(u);
                    if (name != null) names.add(name); else unknown.add(u.toString());
                }
                names.sort(String.CASE_INSENSITIVE_ORDER);

                if (names.isEmpty() && unknown.isEmpty()) {
                    p.sendMessage(Lang.TRUST_TRUSTED);
                } else {
                    if (!names.isEmpty()) p.sendMessage(Lang.trustTrustedList(String.join(", ", names)));
                    if (!unknown.isEmpty()) {
                        // Full UUIDs so they can actually be copied into /trust remove.
                        p.sendMessage(Lang.trustUnknownPlayers(String.join(", ", unknown)));
                        p.sendMessage(Lang.TRUST_USE_TRUST_REMOVE_UUID_CLEAN);
                    }
                }
            }
            case "add" -> {
                if (args.length < 2) { p.sendMessage(Lang.TRUST_USAGE_TRUST_ADD_PLAYER); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { p.sendMessage(Lang.TRUST_PLAYER_NOT_FOUND); return true; }
                if (target.equals(p)) { p.sendMessage(Lang.TRUST_YOU_CANNOT_TRUST_YOURSELF); return true; }
                UUID me = p.getUniqueId();
                UUID them = target.getUniqueId();
                if (trust.isTrusted(me, them) && trust.isTrusted(them, me)) {
                    p.sendMessage(Lang.TRUST_YOU_ARE_ALREADY_MUTUALLY_TRUSTED);
                    return true;
                }
                // They already asked us: both sides want it, so just finish it.
                if (trust.hasPending(me, them)) {
                    trust.clearPending(me, them);
                    trust.addMutualTrust(me, them);
                    p.sendMessage(Lang.TRUST_YOU_ARE_NOW_MUTUALLY_TRUSTED);
                    target.sendMessage(Lang.trustAcceptedYourTrustRequest(p.getName()));
                    return true;
                }
                if (trust.hasPending(them, me)) {
                    p.sendMessage(Component.text("You already sent " + target.getName() + " a trust request.", NamedTextColor.YELLOW));
                    return true;
                }
                trust.addPending(them, me);
                Component msg = Component.text(p.getName() + " wants to trust with you. ", NamedTextColor.GOLD)
                        .append(Component.text("[ACCEPT]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/trust accept " + p.getUniqueId())))
                        .append(Component.text(" "))
                        .append(Component.text("[DENY]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/trust deny " + p.getUniqueId())));
                target.sendMessage(msg);
                p.sendMessage(Lang.trustSentTrustRequest(target.getName()));
            }
            case "accept" -> {
                if (args.length < 2) { p.sendMessage(Lang.TRUST_USAGE_TRUST_ACCEPT_PLAYER_UUID); return true; }
                UUID fromId = resolveRequestor(args[1]);
                if (fromId == null || fromId.equals(p.getUniqueId())) { p.sendMessage(Lang.TRUST_PLAYER_NOT_FOUND); return true; }
                if (!trust.hasPending(p.getUniqueId(), fromId)) { p.sendMessage(Lang.TRUST_NO_PENDING_REQUEST_FROM_THAT); return true; }
                Player other = Bukkit.getPlayer(fromId);
                if (other == null) {
                    // Requestor left; requests are dropped on quit, this is just a safety net.
                    trust.clearPending(p.getUniqueId(), fromId);
                    p.sendMessage(Lang.TRUST_NO_PENDING_REQUEST_FROM_THAT);
                    return true;
                }
                trust.clearPending(p.getUniqueId(), fromId);
                trust.addMutualTrust(p.getUniqueId(), fromId);
                p.sendMessage(Lang.TRUST_YOU_ARE_NOW_MUTUALLY_TRUSTED);
                other.sendMessage(Lang.trustAcceptedYourTrustRequest(p.getName()));
            }
            case "deny" -> {
                if (args.length < 2) { p.sendMessage(Lang.TRUST_USAGE_TRUST_DENY_PLAYER_UUID); return true; }
                UUID fromId = resolveRequestor(args[1]);
                if (fromId == null || fromId.equals(p.getUniqueId())) { p.sendMessage(Lang.TRUST_PLAYER_NOT_FOUND); return true; }
                if (trust.hasPending(p.getUniqueId(), fromId)) {
                    trust.clearPending(p.getUniqueId(), fromId);
                    p.sendMessage(Lang.TRUST_DENIED_TRUST_REQUEST);
                    Player other = Bukkit.getPlayer(fromId);
                    if (other != null) other.sendMessage(Lang.trustDeniedYourTrustRequest(p.getName()));
                } else {
                    p.sendMessage(Lang.TRUST_NO_PENDING_REQUEST_FROM_THAT);
                }
            }
            case "remove" -> {
                if (args.length < 2) { p.sendMessage(Lang.TRUST_USAGE_TRUST_REMOVE_PLAYER); return true; }
                UUID me = p.getUniqueId();
                // Works for online and offline players: exact online name, a name from your
                // trusted list, or a raw UUID.
                UUID target = trust.findTrusted(me, args[1]);
                if (target == null) {
                    p.sendMessage(Component.text("No trusted player named " + args[1] + ". Use /trust list.", NamedTextColor.RED));
                    return true;
                }
                if (target.equals(me)) { p.sendMessage(Lang.TRUST_YOU_CANNOT_TRUST_YOURSELF); return true; }
                if (!trust.removeMutualTrust(me, target)) {
                    p.sendMessage(Component.text("You don't trust that player.", NamedTextColor.RED));
                    return true;
                }
                String name = trust.getName(target);
                p.sendMessage(name != null
                        ? Component.text("Removed mutual trust with " + name + ".", NamedTextColor.YELLOW)
                        : Lang.TRUST_REMOVED_MUTUAL_TRUST);
            }
            default -> p.sendMessage(Lang.TRUST_USAGE_TRUST_LIST_ADD_REMOVE);
        }
        return true;
    }

    /** An online player's exact name, or a raw UUID (what the clickable [ACCEPT]/[DENY] buttons send). */
    private UUID resolveRequestor(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return online.getUniqueId();
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> subcommands = List.of("list", "add", "remove", "accept", "deny");
            return subcommands.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();

            switch (subcommand) {
                case "add" -> {
                    // Online players except self and already trusted players
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!online.equals(p) &&
                                !trust.isTrusted(p.getUniqueId(), online.getUniqueId())) {
                            suggestions.add(online.getName());
                        }
                    }
                }
                case "remove" -> suggestions.addAll(trust.getTrustedNames(p.getUniqueId())); // includes offline players
                case "accept", "deny" -> {
                    // Players who have sent pending requests
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!online.equals(p) && trust.hasPending(p.getUniqueId(), online.getUniqueId())) {
                            suggestions.add(online.getName());
                        }
                    }
                }
            }

            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return new ArrayList<>();
    }
}