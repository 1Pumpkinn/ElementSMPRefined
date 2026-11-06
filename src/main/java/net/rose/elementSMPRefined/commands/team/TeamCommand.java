package net.rose.elementSMPRefined.commands.team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.TeamManager;
import net.rose.elementSMPRefined.managers.TeamManager.Team;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /team - create a team, invite players, and ally with other teams.
 * <p>
 * Only the owner can invite, kick, transfer, disband and handle alliances; every member can
 * leave and look at team info. Member and ally limits are enforced by {@link TeamManager}.
 */
public class TeamCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS =
            List.of("create", "invite", "accept", "deny", "leave", "kick", "transfer", "disband", "info", "ally", "chat");
    private static final List<String> ALLY_SUBCOMMANDS = List.of("add", "accept", "deny", "remove");

    private final TeamManager teams;

    public TeamCommand(TeamManager teams) {
        this.teams = teams;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Lang.TEAM_PLAYERS_ONLY);
            return true;
        }
        if (args.length == 0) {
            if (teams.getTeam(p.getUniqueId()) != null) info(p, args);
            else p.sendMessage(Lang.TEAM_USAGE);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> create(p, args);
            case "invite", "add" -> invite(p, args);
            case "accept" -> accept(p, args);
            case "deny" -> deny(p, args);
            case "leave" -> leave(p);
            case "kick", "remove" -> kick(p, args);
            case "transfer" -> transfer(p, args);
            case "disband" -> disband(p);
            case "info", "list" -> info(p, args);
            case "ally" -> ally(p, args);
            case "chat", "c" -> chat(p, args);
            default -> p.sendMessage(Lang.TEAM_USAGE);
        }
        return true;
    }

    // Team management

    private void create(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(Lang.teamUsage("/team create <name>")); return; }
        String name = args[1];
        switch (teams.create(p.getUniqueId(), name)) {
            case OK -> p.sendMessage(Lang.teamOk("Created team " + name + ". Invite players with /team invite <player>."));
            case INVALID_NAME -> p.sendMessage(Lang.teamError("Team names are " + TeamManager.MIN_NAME_LENGTH + "-"
                    + TeamManager.MAX_NAME_LENGTH + " characters: letters, numbers and underscores."));
            case NAME_TAKEN -> p.sendMessage(Lang.teamError("A team named " + name + " already exists."));
            case IN_TEAM -> p.sendMessage(Lang.teamError("You are already in a team. Leave it first with /team leave."));
            default -> { }
        }
    }

    private void invite(Player p, String[] args) {
        Team team = ownedTeam(p);
        if (team == null) return;
        if (args.length < 2) { p.sendMessage(Lang.teamUsage("/team invite <player>")); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { p.sendMessage(Lang.teamError("Player not found")); return; }
        if (target.equals(p)) { p.sendMessage(Lang.teamError("You are already on your team.")); return; }
        Team theirs = teams.getTeam(target.getUniqueId());
        if (theirs != null) {
            p.sendMessage(Lang.teamError(target.getName() + (theirs == team ? " is already on your team." : " is already in a team.")));
            return;
        }
        if (team.getSize() >= teams.getMaxMembers()) {
            p.sendMessage(Lang.teamError("Your team is full (" + teams.getMaxMembers() + " members max)."));
            return;
        }
        if (teams.hasInvite(target.getUniqueId(), team)) {
            p.sendMessage(Lang.teamNote("You already invited " + target.getName() + "."));
            return;
        }
        teams.addInvite(target.getUniqueId(), team);
        target.sendMessage(requestMessage(p.getName() + " invited you to join team " + team.getName() + ". ",
                "/team accept " + team.getName(), "/team deny " + team.getName()));
        p.sendMessage(Lang.teamOk("Invited " + target.getName() + " to your team. The invite expires in 60 seconds."));
    }

    private void accept(Player p, String[] args) {
        UUID me = p.getUniqueId();
        Team team = inviteTeam(p, args, "/team accept <team>");
        if (team == null) return;
        switch (teams.addMember(team, me)) {
            case OK -> {
                p.sendMessage(Lang.teamOk("You joined team " + team.getName() + "."));
                broadcast(team, Lang.teamOk(p.getName() + " joined your team."), me);
            }
            case TEAM_FULL -> p.sendMessage(Lang.teamError("That team is full."));
            case IN_TEAM -> p.sendMessage(Lang.teamError("You are already in a team. Leave it first with /team leave."));
            default -> { }
        }
    }

    private void deny(Player p, String[] args) {
        Team team = inviteTeam(p, args, "/team deny <team>");
        if (team == null) return;
        teams.clearInvite(p.getUniqueId(), team);
        p.sendMessage(Lang.teamNote("Declined the invite from team " + team.getName() + "."));
        Player owner = Bukkit.getPlayer(team.getOwner());
        if (owner != null) owner.sendMessage(Lang.teamError(p.getName() + " declined your team invite."));
    }

    /** The team a player is answering an invite from: the named one, or their only pending invite. */
    private Team inviteTeam(Player p, String[] args, String usage) {
        UUID me = p.getUniqueId();
        Team team;
        if (args.length >= 2) {
            team = teams.getTeamByName(args[1]);
        } else {
            List<Team> pending = teams.getInvites(me);
            if (pending.size() != 1) { p.sendMessage(Lang.teamUsage(usage)); return null; }
            team = pending.getFirst();
        }
        if (team == null || !teams.hasInvite(me, team)) {
            p.sendMessage(Lang.teamError("No pending invite from that team."));
            return null;
        }
        return team;
    }

    private void leave(Player p) {
        UUID me = p.getUniqueId();
        Team team = teams.getTeam(me);
        if (team == null) { p.sendMessage(Lang.TEAM_NOT_IN_TEAM); return; }
        if (team.isOwner(me)) {
            p.sendMessage(Lang.teamError("You own this team. Use /team transfer <player> to hand it over, or /team disband."));
            return;
        }
        teams.removeMember(team, me);
        p.sendMessage(Lang.teamNote("You left team " + team.getName() + "."));
        broadcast(team, Lang.teamNote(p.getName() + " left your team."), null);
    }

    private void kick(Player p, String[] args) {
        Team team = ownedTeam(p);
        if (team == null) return;
        if (args.length < 2) { p.sendMessage(Lang.teamUsage("/team kick <player>")); return; }
        // Works for offline members too: exact name, or a raw UUID.
        UUID target = teams.findMember(team, args[1]);
        if (target == null) { p.sendMessage(Lang.teamError(args[1] + " isn't on your team.")); return; }
        if (target.equals(p.getUniqueId())) {
            p.sendMessage(Lang.teamError("You can't kick yourself. Use /team transfer or /team disband."));
            return;
        }
        String name = teams.getName(target);
        teams.removeMember(team, target);
        p.sendMessage(Lang.teamNote("Removed " + (name != null ? name : args[1]) + " from your team."));
        Player online = Bukkit.getPlayer(target);
        if (online != null) online.sendMessage(Lang.teamError("You were removed from team " + team.getName() + "."));
    }

    private void transfer(Player p, String[] args) {
        Team team = ownedTeam(p);
        if (team == null) return;
        if (args.length < 2) { p.sendMessage(Lang.teamUsage("/team transfer <player>")); return; }
        UUID target = teams.findMember(team, args[1]);
        if (target == null) { p.sendMessage(Lang.teamError(args[1] + " isn't on your team.")); return; }
        if (!teams.transferOwner(team, target)) {
            p.sendMessage(Lang.teamError("You already own this team."));
            return;
        }
        String name = teams.getName(target);
        broadcast(team, Lang.teamOk((name != null ? name : args[1]) + " is now the owner of team " + team.getName() + "."), null);
    }

    private void disband(Player p) {
        Team team = ownedTeam(p);
        if (team == null) return;
        List<UUID> members = new ArrayList<>(team.getMembers());
        teams.disband(team);
        Component msg = Lang.teamNote("Team " + team.getName() + " was disbanded.");
        for (UUID u : members) {
            Player online = Bukkit.getPlayer(u);
            if (online != null) online.sendMessage(msg);
        }
    }

    private void info(Player p, String[] args) {
        Team team;
        if (args.length >= 2) {
            team = teams.getTeamByName(args[1]);
            if (team == null) { p.sendMessage(Lang.teamError("No team named " + args[1] + ".")); return; }
        } else {
            team = teams.getTeam(p.getUniqueId());
            if (team == null) { p.sendMessage(Lang.TEAM_NOT_IN_TEAM); return; }
        }
        String ownerName = teams.getName(team.getOwner());
        List<String> allyNames = teams.getAllies(team).stream().map(Team::getName).toList();

        p.sendMessage(Lang.teamOk("Team " + team.getName()));
        p.sendMessage(Lang.teamInfoLine("Owner", ownerName != null ? ownerName : team.getOwner().toString()));
        p.sendMessage(Lang.teamInfoLine("Members (" + team.getSize() + "/" + teams.getMaxMembers() + ")",
                String.join(", ", teams.getMemberNames(team))));
        p.sendMessage(Lang.teamInfoLine("Allies (" + allyNames.size() + "/" + teams.getMaxAllies() + ")",
                allyNames.isEmpty() ? "(none)" : String.join(", ", allyNames)));
    }

    private void chat(Player p, String[] args) {
        UUID me = p.getUniqueId();
        Team team = teams.getTeam(me);
        if (team == null) { p.sendMessage(Lang.TEAM_NOT_IN_TEAM); return; }

        if (args.length >= 2) {
            String arg = args[1].toLowerCase();
            if (args.length == 2 && (arg.equals("on") || arg.equals("off"))) {
                boolean enable = arg.equals("on");
                teams.setTeamChat(me, enable);
                p.sendMessage(Lang.teamNote(enable ? "Team chat on. Only your team can see your messages."
                        : "Team chat off. Your messages go to everyone again."));
                return;
            }
            String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            sendTeamMessage(team, p, message);
            return;
        }

        boolean enabled = teams.toggleTeamChat(me);
        p.sendMessage(Lang.teamNote(enabled ? "Team chat on. Only your team can see your messages. Use /team chat to turn it off."
                : "Team chat off. Your messages go to everyone again."));
    }

    /** Sends a one-off message to the team without changing the sender's chat mode. */
    private void sendTeamMessage(Team team, Player sender, String message) {
        Component line = Lang.teamChatMessage(team.getName(), sender.getName(), message);
        broadcast(team, line, null);
        Bukkit.getConsoleSender().sendMessage(line);
    }

    // Alliances

    private void ally(Player p, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase() : "";
        if (!ALLY_SUBCOMMANDS.contains(sub)) {
            p.sendMessage(Lang.teamUsage("/team ally <add|accept|deny|remove> <team>"));
            return;
        }
        Team mine = ownedTeam(p);
        if (mine == null) return;
        if (args.length < 3) { p.sendMessage(Lang.teamUsage("/team ally " + sub + " <team>")); return; }
        Team other = teams.getTeamByName(args[2]);
        if (other == null) { p.sendMessage(Lang.teamError("No team named " + args[2] + ".")); return; }
        if (other == mine) { p.sendMessage(Lang.teamError("You can't ally with your own team.")); return; }

        switch (sub) {
            case "add" -> allyAdd(p, mine, other);
            case "accept" -> allyAccept(p, mine, other);
            case "deny" -> allyDeny(p, mine, other);
            case "remove" -> allyRemove(p, mine, other);
            default -> { }
        }
    }

    private void allyAdd(Player p, Team mine, Team other) {
        if (mine.getAllyIds().contains(other.getId())) {
            p.sendMessage(Lang.teamNote("Your team is already allied with " + other.getName() + "."));
            return;
        }
        // They already asked us: both sides want it, so just finish it.
        if (teams.hasAllyRequest(mine, other)) { allyAccept(p, mine, other); return; }
        if (teams.hasAllyRequest(other, mine)) {
            p.sendMessage(Lang.teamNote("You already sent team " + other.getName() + " an ally request."));
            return;
        }
        int max = teams.getMaxAllies();
        if (max == 0) { p.sendMessage(Lang.teamError("Alliances are turned off on this server.")); return; }
        if (mine.getAllyIds().size() >= max) {
            p.sendMessage(Lang.teamError("Your team already has the maximum number of allies (" + max + ")."));
            return;
        }
        if (other.getAllyIds().size() >= max) {
            p.sendMessage(Lang.teamError("Team " + other.getName() + " already has the maximum number of allies."));
            return;
        }
        teams.addAllyRequest(other, mine);
        Player theirOwner = Bukkit.getPlayer(other.getOwner());
        if (theirOwner != null) {
            theirOwner.sendMessage(requestMessage("Team " + mine.getName() + " wants to ally with your team. ",
                    "/team ally accept " + mine.getName(), "/team ally deny " + mine.getName()));
        }
        p.sendMessage(Lang.teamOk("Sent an ally request to team " + other.getName() + ". It expires in 60 seconds."
                + (theirOwner == null ? " Their owner is offline right now." : "")));
    }

    private void allyAccept(Player p, Team mine, Team other) {
        if (!teams.hasAllyRequest(mine, other)) {
            p.sendMessage(Lang.teamError("No pending ally request from that team."));
            return;
        }
        switch (teams.addAlly(mine, other)) {
            case OK -> {
                broadcast(mine, Lang.teamOk("Your team is now allied with team " + other.getName() + "."), null);
                broadcast(other, Lang.teamOk("Your team is now allied with team " + mine.getName() + "."), null);
            }
            case ALLY_LIMIT_SELF -> p.sendMessage(Lang.teamError(
                    "Your team already has the maximum number of allies (" + teams.getMaxAllies() + ")."));
            case ALLY_LIMIT_OTHER -> p.sendMessage(Lang.teamError(
                    "Team " + other.getName() + " already has the maximum number of allies."));
            case ALREADY_ALLIED -> p.sendMessage(Lang.teamNote("Your team is already allied with " + other.getName() + "."));
            default -> { }
        }
    }

    private void allyDeny(Player p, Team mine, Team other) {
        if (!teams.hasAllyRequest(mine, other)) {
            p.sendMessage(Lang.teamError("No pending ally request from that team."));
            return;
        }
        teams.clearAllyRequest(mine, other);
        p.sendMessage(Lang.teamNote("Declined the ally request from team " + other.getName() + "."));
        Player theirOwner = Bukkit.getPlayer(other.getOwner());
        if (theirOwner != null) {
            theirOwner.sendMessage(Lang.teamError("Team " + mine.getName() + " declined your ally request."));
        }
    }

    private void allyRemove(Player p, Team mine, Team other) {
        if (!teams.removeAlly(mine, other)) {
            p.sendMessage(Lang.teamError("Your team isn't allied with " + other.getName() + "."));
            return;
        }
        broadcast(mine, Lang.teamNote("Your team is no longer allied with team " + other.getName() + "."), null);
        broadcast(other, Lang.teamNote("Team " + mine.getName() + " ended its alliance with your team."), null);
    }

    // Helpers

    /** The caller's team if they own one; otherwise tells them why not and returns null. */
    private Team ownedTeam(Player p) {
        Team team = teams.getTeam(p.getUniqueId());
        if (team == null) { p.sendMessage(Lang.TEAM_NOT_IN_TEAM); return null; }
        if (!team.isOwner(p.getUniqueId())) { p.sendMessage(Lang.TEAM_OWNER_ONLY); return null; }
        return team;
    }

    /** Sends to every online member of the team, except {@code except} (may be null). */
    private void broadcast(Team team, Component message, UUID except) {
        for (UUID u : team.getMembers()) {
            if (u.equals(except)) continue;
            Player online = Bukkit.getPlayer(u);
            if (online != null) online.sendMessage(message);
        }
    }

    private static Component requestMessage(String text, String acceptCommand, String denyCommand) {
        return Component.text(text, NamedTextColor.GOLD)
                .append(Component.text("[ACCEPT]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand(acceptCommand)))
                .append(Component.text(" "))
                .append(Component.text("[DENY]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand(denyCommand)));
    }

    // Tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return new ArrayList<>();
        UUID me = p.getUniqueId();
        Team mine = teams.getTeam(me);
        List<String> suggestions = new ArrayList<>();
        String prefix;

        if (args.length == 1) {
            suggestions.addAll(SUBCOMMANDS);
            prefix = args[0];
        } else if (args.length == 2) {
            prefix = args[1];
            switch (args[0].toLowerCase()) {
                case "invite", "add" -> {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!online.equals(p) && teams.getTeam(online.getUniqueId()) == null) suggestions.add(online.getName());
                    }
                }
                case "kick", "remove", "transfer" -> {
                    if (mine != null) suggestions.addAll(teams.getMemberNames(mine)); // includes offline members
                }
                case "accept", "deny" -> teams.getInvites(me).forEach(t -> suggestions.add(t.getName()));
                case "info", "list" -> teams.getAllTeams().forEach(t -> suggestions.add(t.getName()));
                case "ally" -> suggestions.addAll(ALLY_SUBCOMMANDS);
                case "chat" -> suggestions.addAll(List.of("on", "off"));
                default -> { }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("ally") && mine != null) {
            prefix = args[2];
            switch (args[1].toLowerCase()) {
                case "add" -> {
                    for (Team t : teams.getAllTeams()) {
                        if (t != mine && !mine.getAllyIds().contains(t.getId())) suggestions.add(t.getName());
                    }
                }
                case "accept", "deny" -> teams.getAllyRequests(mine).forEach(t -> suggestions.add(t.getName()));
                case "remove" -> teams.getAllies(mine).forEach(t -> suggestions.add(t.getName()));
                default -> { }
            }
        } else {
            return new ArrayList<>();
        }

        String lower = prefix.toLowerCase();
        return suggestions.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }
}
