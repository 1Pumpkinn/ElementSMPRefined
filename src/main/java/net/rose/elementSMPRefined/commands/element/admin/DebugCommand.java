package net.rose.elementSMPRefined.commands.element.admin;

import net.rose.elementSMPRefined.commands.supporters.CommandSupport;
import net.rose.elementSMPRefined.commands.supporters.ElementSubCommand;
import net.rose.elementSMPRefined.core.API.element.Element;
import net.rose.elementSMPRefined.core.API.element.ElementId;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.data.DataStore;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.managers.CooldownManager;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.status.DisarmManager;
import net.rose.elementSMPRefined.status.StatusEffectManager;
import net.rose.elementSMPRefined.status.StatusEffectType;
import net.rose.elementSMPRefined.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /element debug [player] - Admin-only dump of everything the plugin
 * currently believes about a player, split into two sections:
 * <ul>
 *     <li><b>Persisted Data</b> - element ElementManager reports before and
 *     after a forced cache reload (the original point of this command),
 *     display name, upgrade level, trust list (cross-checked between
 *     {@link TrustManager}'s live cache and the raw stored data - a
 *     mismatch here is a real cache-drift bug), and pending reroller
 *     refunds. All of this comes straight from disk-backed storage, so
 *     it's available even if the target isn't online right now.</li>
 *     <li><b>Live State</b> - both abilities' cooldown state (READY or
 *     seconds remaining, straight from {@link CooldownManager} - the same
 *     source the action bar HUD reads from), disarm state, active combat
 *     status effects with remaining duration, the player's actually-active
 *     passive potion effects (what {@code applyUpsides} granted them right
 *     now, as opposed to what their element's description merely promises),
 *     reroll-in-progress flag, and game mode. Only available while the
 *     target is online.</li>
 * </ul>
 * If no player is given, defaults to debugging yourself (console must name
 * one explicitly). A name that isn't currently online falls back to a
 * known offline player so the persisted-data section still works without
 * requiring them to log in first.
 */
public class DebugCommand implements ElementSubCommand {
    /**
     * Which {@link StatusEffectType}s to check via {@link StatusEffectManager}.
     * Excludes ABILITY_DISARM/WEAPON_DISARM/MAIN_HAND_DISARM - those three are
     * tracked separately by {@link DisarmManager}, not this manager, and
     * {@code debugDisarmed()} already covers the ability-disarm case.
     */
    private static final EnumSet<StatusEffectType> MONITORED_STATUS_EFFECTS = EnumSet.of(
            StatusEffectType.SLOW, StatusEffectType.SILENCE, StatusEffectType.WEAKNESS,
            StatusEffectType.FREEZE, StatusEffectType.BLEED, StatusEffectType.BURN,
            StatusEffectType.ROOT, StatusEffectType.FULL_STUN, StatusEffectType.PARTIAL_STUN,
            StatusEffectType.STUN
    );

    /**
     * Element passives are applied with an effectively-infinite duration (see
     * {@code EffectService#isElementPotionEffect}) - that's what actually
     * distinguishes "granted by our passive system" from "player drank a
     * potion", since both otherwise show up in the same active-effects list.
     */
    private static final int ELEMENT_PASSIVE_DURATION_THRESHOLD_TICKS = 1_000_000;

    /** Trust list names beyond this many are collapsed to "(+N more)" so a long list doesn't flood chat. */
    private static final int MAX_TRUST_NAMES_SHOWN = 10;

    private final DataStore dataStore;
    private final ElementManager elementManager;
    private final CooldownManager cooldownManager;
    private final DisarmManager disarmManager;
    private final StatusEffectManager statusEffectManager;
    private final TrustManager trustManager;

    public DebugCommand(DataStore dataStore, ElementManager elementManager,
                        CooldownManager cooldownManager, DisarmManager disarmManager,
                        StatusEffectManager statusEffectManager, TrustManager trustManager) {
        this.dataStore = dataStore;
        this.elementManager = elementManager;
        this.cooldownManager = cooldownManager;
        this.disarmManager = disarmManager;
        this.statusEffectManager = statusEffectManager;
        this.trustManager = trustManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!(sender instanceof Player self)) {
                sender.sendMessage(Lang.DEBUG_USAGE_ELEMENT_DEBUG_PLAYER);
                return true;
            }
            return debugTarget(sender, self, self.getName());
        }

        String targetName = args[1];
        Player online = Bukkit.getPlayer(targetName);
        if (online != null) {
            return debugTarget(sender, online, online.getName());
        }

        // Not online right now - fall back to a known offline player so the
        // persisted-data section still works without requiring them to log
        // in first. Bukkit#getOfflinePlayer(String) is deprecated in favor of
        // UUID lookups, but a name is all an admin has to go on here, and
        // this only runs for an infrequent manual debug command.
        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
        if (!offline.hasPlayedBefore()) {
            sender.sendMessage(Lang.debugPlayer(targetName));
            return true;
        }
        return debugTarget(sender, offline, targetName);
    }

    private boolean debugTarget(CommandSender sender, OfflinePlayer target, String displayName) {
        UUID uuid = target.getUniqueId();
        Player livePlayer = target.getPlayer(); // non-null only if actually online right now

        sender.sendMessage(Lang.debugElementDebug(displayName));
        sender.sendMessage(Lang.debugOnlineStatus(livePlayer != null));

        sendPersistedData(sender, uuid);

        sender.sendMessage(Lang.debugSection("Live State"));
        if (livePlayer != null) {
            sendLiveState(sender, livePlayer);
        } else {
            sender.sendMessage(Lang.DEBUG_LIVE_STATE_UNAVAILABLE);
        }

        sender.sendMessage(Lang.DEBUG_FOOTER);
        return true;
    }

    /** Everything that comes straight from disk-backed storage - works whether or not the target is online. */
    private void sendPersistedData(CommandSender sender, UUID uuid) {
        sender.sendMessage(Lang.debugSection("Persisted Data"));

        ElementType reportedType = elementManager.data(uuid).getCurrentElement();
        sender.sendMessage(Lang.debugElementmanagerReportsBuiltinType(reportedType != null ? reportedType.name() : "null"));

        ElementId reportedId = elementManager.data(uuid).getCurrentElementId();
        sender.sendMessage(Lang.debugElementmanagerReportsElementId(reportedId != null ? reportedId.toString() : "null"));

        dataStore.invalidateCache(uuid);
        ElementType reloadedType = elementManager.data(uuid).getCurrentElement();
        sender.sendMessage(Lang.debugAfterCacheInvalidation(reloadedType != null ? reloadedType.name() : "null"));

        Element element = reportedId != null ? elementManager.get(reportedId) : null;
        if (element != null) {
            sender.sendMessage(Lang.debugDisplayName(ChatColor.stripColor(element.getDisplayName())));
        }

        PlayerData pd = elementManager.data(uuid);
        sender.sendMessage(Lang.debugUpgradeLevel(pd.getCurrentElementUpgradeLevel()));

        sendTrustInfo(sender, uuid, pd);

        sender.sendMessage(Lang.debugPendingRefunds(pd.getPendingRerollerRefunds(), pd.getPendingAdvancedRerollerRefunds()));
    }

    /** Everything that needs the target actually online right now. */
    private void sendLiveState(CommandSender sender, Player livePlayer) {
        ElementId elementId = elementManager.getPlayerElementId(livePlayer);
        sendAbilityStatus(sender, livePlayer, elementId);

        sender.sendMessage(Lang.debugDisarmed(disarmManager.isAbilityDisarmed(livePlayer)));
        sender.sendMessage(Lang.debugActiveStatusEffects(formatActiveStatusEffects(livePlayer)));
        sender.sendMessage(Lang.debugActivePassiveEffects(formatActivePassiveEffects(livePlayer)));
        sender.sendMessage(Lang.debugRerolling(elementManager.isCurrentlyRolling(livePlayer)));
        sender.sendMessage(Lang.debugGameMode(livePlayer.getGameMode().name()));
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

    private String formatActiveStatusEffects(Player player) {
        List<String> active = new ArrayList<>();
        for (StatusEffectType type : MONITORED_STATUS_EFFECTS) {
            if (statusEffectManager.hasEffect(player, type)) {
                int secondsLeft = statusEffectManager.getRemainingDuration(player, type) / 20;
                active.add(type.name() + " (" + secondsLeft + "s)");
            }
        }
        return active.isEmpty() ? "None" : String.join(", ", active);
    }

    /**
     * The live "active passive viewer": scans the player's actual current potion
     * effects for ones that look element-granted (an effectively-infinite duration,
     * per {@code EffectService#isElementPotionEffect}) rather than a normal potion
     * someone drank. This shows what's genuinely applied right now, as opposed to
     * what the element's static description promises - the two can disagree if
     * {@code applyUpsides} isn't being re-run when it should be.
     */
    private String formatActivePassiveEffects(Player player) {
        List<String> active = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getDuration() == PotionEffect.INFINITE_DURATION
                    || effect.getDuration() > ELEMENT_PASSIVE_DURATION_THRESHOLD_TICKS) {
                active.add(prettyEffectName(effect) + " (amplifier " + effect.getAmplifier() + ")");
            }
        }
        return active.isEmpty() ? "None" : String.join(", ", active);
    }

    private String prettyEffectName(PotionEffect effect) {
        String key = effect.getType().getKey().getKey(); // e.g. "fire_resistance"
        String name = key.replace('_', ' ');
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    /**
     * Trust is tracked in two places that are each other's cache: {@link TrustManager}
     * keeps its own in-memory copy for fast lookups, separate from the
     * {@link PlayerData#getTrustedPlayers()} set loaded straight from disk. They should
     * always agree - if they don't, something changed one without invalidating the
     * other, and surfacing that drift is exactly what a debug command is for.
     */
    private void sendTrustInfo(CommandSender sender, UUID uuid, PlayerData pd) {
        List<String> liveNames = trustManager.getTrustedNames(uuid);
        sender.sendMessage(Lang.debugTrustedNames(joinCapped(liveNames, MAX_TRUST_NAMES_SHOWN), liveNames.size()));

        int storedCount = pd.getTrustedPlayers().size();
        if (storedCount != liveNames.size()) {
            sender.sendMessage(Lang.debugTrustCacheMismatch(liveNames.size(), storedCount));
        }
    }

    private String joinCapped(List<String> names, int max) {
        if (names.isEmpty()) {
            return "None";
        }
        if (names.size() <= max) {
            return String.join(", ", names);
        }
        return String.join(", ", names.subList(0, max)) + " (+" + (names.size() - max) + " more)";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return CommandSupport.getOnlinePlayerNames(args[1]);
        }
        return Collections.emptyList();
    }
}