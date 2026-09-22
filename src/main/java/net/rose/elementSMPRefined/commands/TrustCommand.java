package net.rose.elementSMPRefined.commands;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.lang.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
                var names = trust.getTrustedNames(p.getUniqueId());

                // If getTrustedNames returns UUIDs (from old implementation), convert them
                List<String> displayNames = new ArrayList<>();
                List<String> unknownUUIDs = new ArrayList<>();

                for (String name : names) {
                    try {
                        // Try to parse as UUID
                        UUID uuid = UUID.fromString(name);
                        // Convert UUID to player name
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                        String playerName = offlinePlayer.getName();
                        if (playerName != null) {
                            displayNames.add(playerName);
                        } else {
                            // If name is null, this player never joined the server
                            unknownUUIDs.add(uuid.toString().substring(0, 8) + "...");
                        }
                    } catch (IllegalArgumentException e) {
                        // Not a UUID, assume it's already a name
                        displayNames.add(name);
                    }
                }

                if (displayNames.isEmpty() && unknownUUIDs.isEmpty()) {
                    p.sendMessage(Lang.TRUST_TRUSTED);
                } else {
                    p.sendMessage(Lang.trustTrusted2(String.join(", ", displayNames)));
                    if (!unknownUUIDs.isEmpty()) {
                        p.sendMessage(Lang.trustUnknownPlayers(String.join(", ", unknownUUIDs)));
                        p.sendMessage(Lang.TRUST_USE_TRUST_REMOVE_UUID_CLEAN);
                    }
                }
            }
            case "add" -> {
                if (args.length < 2) { p.sendMessage(Lang.TRUST_USAGE_TRUST_ADD_PLAYER); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { p.sendMessage(Lang.TRUST_PLAYER_NOT_FOUND); return true; }
                if (target.equals(p)) { p.sendMessage(Lang.TRUST_YOU_CANNOT_TRUST_YOURSELF); return true; }
                if (trust.isTrusted(p.getUniqueId(), target.getUniqueId()) && trust.isTrusted(target.getUniqueId(), p.getUniqueId())) {
                    p.sendMessage(Lang.TRUST_YOU_ARE_ALREADY_MUTUALLY_TRUSTED);
                    return true;
                }
                trust.addPending(target.getUniqueId(), p.getUniqueId());
                // Send clickable message to target
                Component msg = Component.text(p.getName() + " wants to trust with you. ", NamedTextColor.GOLD)
                        .append(Component.text("[ACCEPT]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/trust accept " + p.getUniqueId())))
                        .append(Component.text(" "))
                        .append(Component.text("[DENY]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/trust deny " + p.getUniqueId())));
                target.sendMessage(msg);
                p.sendMessage(Lang.trustSentTrustRequest(target.getName()));
            }
            case "accept" -> {
                if (args.length < 2) { p.sendMessage(Lang.TRUST_USAGE_TRUST_ACCEPT_PLAYER_UUID); return true; }
                Player from = Bukkit.getPlayer(args[1]);
                UUID fromId = null;
                if (from != null) fromId = from.getUniqueId();
                else {
                    try { fromId = UUID.fromString(args[1]); } catch (Exception ex) { p.sendMessage(Lang.TRUST_PLAYER_NOT_FOUND); return true; }
                }
                if (!trust.hasPending(p.getUniqueId(), fromId)) { p.sendMessage(Lang.TRUST_NO_PENDING_REQUEST_FROM_THAT); return true; }
                trust.clearPending(p.getUniqueId(), fromId);
                trust.addMutualTrust(p.getUniqueId(), fromId);
                p.sendMessage(Lang.TRUST_YOU_ARE_NOW_MUTUALLY_TRUSTED);
                Player other = Bukkit.getPlayer(fromId);
                if (other != null) other.sendMessage(Lang.trustAcceptedYourTrustRequest(p.getName()));
            }
            case "deny" -> {
                if (args.length < 2) { p.sendMessage(Lang.TRUST_USAGE_TRUST_DENY_PLAYER_UUID); return true; }
                Player from = Bukkit.getPlayer(args[1]);
                UUID fromId = null;
                if (from != null) fromId = from.getUniqueId();
                else {
                    try { fromId = UUID.fromString(args[1]); } catch (Exception ex) { p.sendMessage(Lang.TRUST_PLAYER_NOT_FOUND); return true; }
                }
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
                Player target = Bukkit.getPlayer(args[1]);
                UUID uuid;
                if (target != null) uuid = target.getUniqueId(); else {
                    // Fallback: try parsing UUID
                    try { uuid = UUID.fromString(args[1]); } catch (IllegalArgumentException ex) {
                        p.sendMessage(Lang.TRUST_PLAYER_MUST_BE_ONLINE_OR);
                        return true;
                    }
                }
                trust.removeMutualTrust(p.getUniqueId(), uuid);
                p.sendMessage(Lang.TRUST_REMOVED_MUTUAL_TRUST);
            }
            default -> p.sendMessage(Lang.TRUST_USAGE_TRUST_LIST_ADD_REMOVE);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            return new ArrayList<>();
        }

        // First argument: show subcommands
        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("list", "add", "remove", "accept", "deny");
            return subcommands.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Second argument: show player names based on subcommand
        if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();

            switch (subcommand) {
                case "add" -> {
                    // Show online players except self and already trusted players
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!online.equals(p) &&
                                !trust.isTrusted(p.getUniqueId(), online.getUniqueId())) {
                            suggestions.add(online.getName());
                        }
                    }
                }
                case "remove" -> {
                    // Show currently trusted players (convert UUIDs to names)
                    var names = trust.getTrustedNames(p.getUniqueId());
                    for (String name : names) {
                        try {
                            UUID uuid = UUID.fromString(name);
                            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                            String playerName = offlinePlayer.getName();
                            if (playerName != null) {
                                suggestions.add(playerName);
                            }
                        } catch (IllegalArgumentException e) {
                            suggestions.add(name);
                        }
                    }
                }
                case "accept", "deny" -> {
                    // Show players who have sent pending requests
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!online.equals(p) && trust.hasPending(p.getUniqueId(), online.getUniqueId())) {
                            suggestions.add(online.getName());
                        }
                    }
                }
            }

            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}