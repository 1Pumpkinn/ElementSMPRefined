package net.rose.elementSMPRefined.listeners.player;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.TeamManager;
import net.rose.elementSMPRefined.managers.TeamManager.Team;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Set;
import java.util.UUID;

/**
 * While a player has team chat switched on with /team chat, their chat messages are only
 * delivered to their own team (and the console, so staff logs still show them).
 */
public class TeamChatListener implements Listener {
    private final TeamManager teams;

    public TeamChatListener(TeamManager teams) {
        this.teams = teams;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        UUID senderId = sender.getUniqueId();
        if (!teams.isTeamChat(senderId)) return;

        Team team = teams.getTeam(senderId);
        if (team == null) {
            teams.setTeamChat(senderId, false);
            return;
        }

        Set<UUID> members = team.getMembers();
        event.viewers().removeIf(viewer -> isOutsider(viewer, members));
        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
                Lang.teamChatMessage(team.getName(), source.getName(), message)));
    }

    private static boolean isOutsider(Audience viewer, Set<UUID> members) {
        return viewer instanceof Player player && !members.contains(player.getUniqueId());
    }
}
