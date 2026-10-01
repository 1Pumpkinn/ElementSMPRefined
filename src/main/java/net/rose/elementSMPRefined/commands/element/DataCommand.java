package net.rose.elementSMPRefined.commands.element;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.commands.supporters.CommandSupport;
import net.rose.elementSMPRefined.commands.supporters.ElementSubCommand;
import net.rose.elementSMPRefined.core.API.element.Element;
import net.rose.elementSMPRefined.core.API.element.ElementId;
import net.rose.elementSMPRefined.data.DataStore;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.managers.CooldownManager;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.status.DisarmManager;
import net.rose.elementSMPRefined.status.StatusEffectManager;
import net.rose.elementSMPRefined.status.StatusEffectType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /element data [player] - read-only overview of a player's stored data plus a
 * short live summary if they're online. Unlike {@code /element debug} this never
 * invalidates or reloads any cache, so it's safe to run at any time.
 * <p>
 * Sections: identity, element + upgrade progress + passives, owned element items,
 * trusted players, pending reroller refunds, and (online only) ability cooldowns,
 * disarm state, applied passive potion effects, combat status effects and reroll state.
 * <p>
 * No player given = yourself (console must name one). A name that isn't online
 * falls back to a known offline player; their data is read from disk and the
 * cache entry that read creates is dropped again so this can't grow the cache.
 */
public class DataCommand implements ElementSubCommand {
    private static final int MAX_NAMES_SHOWN = 15;

    private final DataStore dataStore;
    private final ElementManager elementManager;
    private final CooldownManager cooldownManager;
    private final DisarmManager disarmManager;
    private final StatusEffectManager statusEffectManager;

    public DataCommand(DataStore dataStore, ElementManager elementManager, CooldownManager cooldownManager,
                       DisarmManager disarmManager, StatusEffectManager statusEffectManager) {
        this.dataStore = dataStore;
        this.elementManager = elementManager;
        this.cooldownManager = cooldownManager;
        this.disarmManager = disarmManager;
        this.statusEffectManager = statusEffectManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (sender instanceof Player self) {
                show(sender, self);
            } else {
                sender.sendMessage(Component.text("Usage: /element data <player>", NamedTextColor.RED));
            }
            return true;
        }

        String name = args[1];
        Player online = Bukkit.getPlayer(name);
        if (online != null) {
            show(sender, online);
            return true;
        }

        // Only a name to go on for offline players - deprecated but fine for a manual admin command.
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (!offline.hasPlayedBefore()) {
            sender.sendMessage(Component.text("No player named '" + name + "' has played on this server.", NamedTextColor.RED));
            return true;
        }
        try {
            show(sender, offline);
        } finally {
            // Offline players have no live use for the cached copy; drop it so this read can't leak.
            dataStore.invalidateCache(offline.getUniqueId());
        }
        return true;
    }

    private void show(CommandSender sender, OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        Player live = target.getPlayer();
        PlayerData pd = elementManager.data(uuid);

        String name = target.getName() != null ? target.getName() : uuid.toString();
        sender.sendMessage(header("Player Data: " + name));
        sender.sendMessage(row("UUID", uuid.toString()));
        sender.sendMessage(row("Status", live != null ? "Online" : "Offline"));

        sendElement(sender, pd);
        sendOwnedItems(sender, pd);
        sendTrusted(sender, pd);
        sender.sendMessage(row("Pending refunds",
                "reroller x" + pd.getPendingRerollerRefunds()
                        + ", advanced reroller x" + pd.getPendingAdvancedRerollerRefunds()));

        if (live != null) {
            sendLive(sender, live, pd);
        }
        sender.sendMessage(Component.text("=========================", NamedTextColor.GOLD));
    }

    private void sendElement(CommandSender sender, PlayerData pd) {
        sender.sendMessage(section("Element"));
        ElementId id = pd.getCurrentElementId();
        if (id == null) {
            sender.sendMessage(row("Current", "None"));
            return;
        }

        Element element = elementManager.get(id);
        String display = element != null ? ChatColor.stripColor(element.getDisplayName()) : id.key();
        sender.sendMessage(row("Current", display + " (" + id + ")"));

        int level = pd.getCurrentElementUpgradeLevel();
        sender.sendMessage(row("Upgrade level", level + "/" + PlayerData.MAX_UPGRADE_LEVEL));
        sender.sendMessage(row("Ability 1", level >= 1 ? "Unlocked" : "Locked (needs Upgrade I)"));
        sender.sendMessage(row("Ability 2", level >= 2 ? "Unlocked" : "Locked (needs Upgrade II)"));

        // Static description of what the element's passives do - covers listener-based
        // passives (e.g. Water's in-water invisibility) that have no potion effect to show.
        List<String> passives = element != null ? element.getPassiveBenefits() : List.of();
        if (passives.isEmpty()) {
            sender.sendMessage(row("Passives", "None"));
        } else {
            sender.sendMessage(row("Passives", ""));
            for (String passive : passives) {
                sender.sendMessage(Component.text("  - " + ChatColor.stripColor(passive), NamedTextColor.WHITE));
            }
        }
    }

    private void sendOwnedItems(CommandSender sender, PlayerData pd) {
        List<String> owned = new ArrayList<>();
        for (ElementId id : pd.getOwnedItemIds()) owned.add(id.toString());
        Collections.sort(owned);
        sender.sendMessage(row("Owned element items", owned.isEmpty() ? "None" : String.join(", ", owned)));
    }

    private void sendTrusted(CommandSender sender, PlayerData pd) {
        List<String> names = new ArrayList<>();
        for (UUID trusted : pd.getTrustedPlayers()) {
            String n = Bukkit.getOfflinePlayer(trusted).getName();
            names.add(n != null ? n : trusted.toString());
        }
        names.sort(Comparator.comparing(String::toLowerCase));

        String shown = names.isEmpty() ? "None"
                : names.size() <= MAX_NAMES_SHOWN ? String.join(", ", names)
                : String.join(", ", names.subList(0, MAX_NAMES_SHOWN)) + " (+" + (names.size() - MAX_NAMES_SHOWN) + " more)";
        sender.sendMessage(row("Trusted (" + names.size() + ")", shown));
    }

    private void sendLive(CommandSender sender, Player live, PlayerData pd) {
        sender.sendMessage(section("Live"));

        Element element = pd.getCurrentElementId() != null ? elementManager.get(pd.getCurrentElementId()) : null;
        if (element != null) {
            sender.sendMessage(row("Ability 1 cooldown", cooldown(live, element.getAbility1Id(), element.getAbility1CooldownSeconds())));
            sender.sendMessage(row("Ability 2 cooldown", cooldown(live, element.getAbility2Id(), element.getAbility2CooldownSeconds())));
        }

        sender.sendMessage(row("Ability disarmed", disarmManager.isAbilityDisarmed(live) ? "Yes" : "No"));
        sender.sendMessage(row("Applied passive effects", appliedPassiveEffects(live)));
        sender.sendMessage(row("Status effects", statusEffects(live)));
        sender.sendMessage(row("Rerolling", elementManager.isCurrentlyRolling(live) ? "Yes" : "No"));
        sender.sendMessage(row("Game mode", live.getGameMode().name()));
    }

    private String cooldown(Player player, String abilityId, int totalSeconds) {
        long remaining = cooldownManager.getRemainingSeconds(player, abilityId);
        return remaining <= 0 ? "READY (" + totalSeconds + "s)" : remaining + "s left of " + totalSeconds + "s";
    }

    /**
     * Potion effects granted by an element passive (see {@code EffectService#isElementPotionEffect}):
     * they're applied with an infinite duration, which is what separates them from a drunk potion.
     * Listener-based passives have no potion effect and only appear in the Passives list above.
     */
    private String appliedPassiveEffects(Player player) {
        List<String> applied = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getDuration() == PotionEffect.INFINITE_DURATION) {
                String key = effect.getType().getKey().getKey().replace('_', ' ');
                String pretty = key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
                applied.add(pretty + " " + (effect.getAmplifier() + 1));
            }
        }
        return applied.isEmpty() ? "None" : String.join(", ", applied);
    }

    private String statusEffects(Player player) {
        List<String> active = new ArrayList<>();
        for (StatusEffectType type : StatusEffectType.values()) {
            if (statusEffectManager.hasEffect(player, type)) {
                active.add(type.name() + " (" + statusEffectManager.getRemainingDuration(player, type) / 20 + "s)");
            }
        }
        return active.isEmpty() ? "None" : String.join(", ", active);
    }

    private static Component header(String text) {
        return Component.text("=== " + text + " ===", NamedTextColor.GOLD);
    }

    private static Component section(String text) {
        return Component.text("-- " + text + " --", NamedTextColor.YELLOW);
    }

    private static Component row(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 2 ? CommandSupport.getOnlinePlayerNames(args[1]) : Collections.emptyList();
    }
}
