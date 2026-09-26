package net.rose.elementSMPRefined.commands.element;

import net.rose.elementSMPRefined.core.API.element.Element;
import net.rose.elementSMPRefined.core.API.element.ElementId;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.data.DataStore;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.managers.CooldownManager;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.status.DisarmManager;
import net.rose.elementSMPRefined.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * /element debug <player> - Admin-only dump of everything the plugin
 * currently believes about a player: which element ElementManager reports
 * before and after a forced cache reload (the original point of this
 * command), plus their upgrade level, both abilities' live cooldown state
 * (READY or seconds remaining, straight from {@link CooldownManager} - the
 * same source the action bar HUD reads from, so this is the fastest way to
 * confirm whether a "stuck" cooldown is a real server-side value or just a
 * display lag), disarm state, trust list size, any pending reroller
 * refunds, whether they're mid-reroll, and their game mode.
 */
public class DebugCommand implements ElementSubCommand {
    private final DataStore dataStore;
    private final ElementManager elementManager;
    private final CooldownManager cooldownManager;
    private final DisarmManager disarmManager;

    public DebugCommand(DataStore dataStore, ElementManager elementManager,
                        CooldownManager cooldownManager, DisarmManager disarmManager) {
        this.dataStore = dataStore;
        this.elementManager = elementManager;
        this.cooldownManager = cooldownManager;
        this.disarmManager = disarmManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Lang.DEBUG_USAGE_ELEMENT_DEBUG_PLAYER);
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Lang.debugPlayer(args[1]));
            return true;
        }

        sender.sendMessage(Lang.debugElementDebug(target.getName()));

        ElementType managerElement = elementManager.getPlayerElement(target);
        sender.sendMessage(Lang.debugElementmanagerReportsBuiltinType((managerElement != null ? managerElement.name() : "null")));

        ElementId managerElementId = elementManager.getPlayerElementId(target);
        sender.sendMessage(Lang.debugElementmanagerReportsElementId((managerElementId != null ? managerElementId.toString() : "null")));

        dataStore.invalidateCache(target.getUniqueId());
        ElementType reloadedElement = elementManager.getPlayerElement(target);
        sender.sendMessage(Lang.debugAfterCacheInvalidation((reloadedElement != null ? reloadedElement.name() : "null")));

        PlayerData pd = elementManager.data(target.getUniqueId());
        sender.sendMessage(Lang.debugUpgradeLevel(pd.getCurrentElementUpgradeLevel()));

        sendAbilityStatus(sender, target, managerElementId);

        sender.sendMessage(Lang.debugDisarmed(disarmManager.isAbilityDisarmed(target)));
        sender.sendMessage(Lang.debugTrustedCount(pd.getTrustedPlayers().size()));
        sender.sendMessage(Lang.debugPendingRefunds(pd.getPendingRerollerRefunds(), pd.getPendingAdvancedRerollerRefunds()));
        sender.sendMessage(Lang.debugRerolling(elementManager.isCurrentlyRolling(target)));
        sender.sendMessage(Lang.debugGameMode(target.getGameMode().name()));

        sender.sendMessage(Lang.DEBUG_FOOTER);

        return true;
    }

    /**
     * Reports live {@link CooldownManager} state for both of the target's
     * abilities - the same {@code isReady}/{@code getRemainingSeconds} calls
     * the action bar HUD makes each tick, so what this prints is exactly
     * what the player's action bar should be showing right now.
     */
    private void sendAbilityStatus(CommandSender sender, Player target, ElementId elementId) {
        Element element = elementId != null ? elementManager.get(elementId) : null;

        if (element == null) {
            sender.sendMessage(Lang.DEBUG_NO_ABILITIES_ELEMENT);
            return;
        }

        sender.sendMessage(Lang.debugAbilityStatus(
                1, ChatColor.stripColor(element.getAbility1Name()), element.getAbility1Id(),
                cooldownManager.isReady(target, element.getAbility1Id()),
                cooldownManager.getRemainingSeconds(target, element.getAbility1Id()),
                element.getAbility1CooldownSeconds()
        ));
        sender.sendMessage(Lang.debugAbilityStatus(
                2, ChatColor.stripColor(element.getAbility2Name()), element.getAbility2Id(),
                cooldownManager.isReady(target, element.getAbility2Id()),
                cooldownManager.getRemainingSeconds(target, element.getAbility2Id()),
                element.getAbility2CooldownSeconds()
        ));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return CommandSupport.getOnlinePlayerNames(args[1]);
        }
        return Collections.emptyList();
    }
}