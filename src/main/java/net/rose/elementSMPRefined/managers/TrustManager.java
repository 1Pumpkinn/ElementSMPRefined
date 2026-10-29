package net.rose.elementSMPRefined.managers;

import net.rose.elementSMPRefined.data.DataStore;
import net.rose.elementSMPRefined.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trust relationships between players.
 * <p>
 * The trusted set lives only in {@link PlayerData} (via {@link DataStore}); this class keeps no
 * second copy, so the two can never disagree. Trust can be changed for offline players too - when
 * that happens the offline player's data is saved and then evicted from the {@link DataStore}
 * cache so we don't leave it cached until they next join.
 */
public class TrustManager {
    private final DataStore store;
    private final Map<UUID, Set<UUID>> pending = new ConcurrentHashMap<>(); // target -> requestors

    public TrustManager(JavaPlugin plugin, DataStore store) {
        this.store = store;
    }

    /** A copy of the owner's trusted set. Changing it does not change anything stored. */
    public Set<UUID> getTrusted(UUID owner) {
        return store.getPlayerData(owner).getTrustedPlayers();
    }

    public boolean isTrusted(UUID owner, UUID other) {
        if (owner == null || other == null) return false;
        return store.getPlayerData(owner).isTrusted(other);
    }

    /** Adds one-way trust. Returns true if anything changed. */
    public boolean addTrust(UUID owner, UUID other) {
        return setTrust(owner, other, true);
    }

    /** Removes one-way trust. Returns true if anything changed. */
    public boolean removeTrust(UUID owner, UUID other) {
        return setTrust(owner, other, false);
    }

    public void addMutualTrust(UUID a, UUID b) {
        addTrust(a, b);
        addTrust(b, a);
    }

    /**
     * Removes trust in both directions; works whether or not either player is online.
     * Both sides are always attempted so a one-sided leftover entry gets cleaned up too.
     *
     * @return true if at least one side was actually removed
     */
    public boolean removeMutualTrust(UUID a, UUID b) {
        boolean first = removeTrust(a, b);
        boolean second = removeTrust(b, a);
        return first | second;
    }

    private boolean setTrust(UUID owner, UUID other, boolean trusted) {
        boolean online = Bukkit.getPlayer(owner) != null;
        PlayerData data = store.getPlayerData(owner);
        boolean changed = data.isTrusted(other) != trusted;
        if (changed) {
            Set<UUID> set = data.getTrustedPlayers();
            if (trusted) set.add(other); else set.remove(other);
            store.setTrusted(owner, set); // saves to disk
        }
        if (!online) {
            // Saved above; don't leave an offline player's data cached forever.
            store.invalidateCache(owner);
        }
        return changed;
    }

    public void addPending(UUID target, UUID requestor) {
        pending.computeIfAbsent(target, k -> ConcurrentHashMap.newKeySet()).add(requestor);
    }

    public boolean hasPending(UUID target, UUID requestor) {
        return pending.getOrDefault(target, Set.of()).contains(requestor);
    }

    public void clearPending(UUID target, UUID requestor) {
        Set<UUID> set = pending.get(target);
        if (set == null) return;
        set.remove(requestor);
        if (set.isEmpty()) pending.remove(target, set);
    }

    /** Last known name of a player, or null if the server has never seen them. */
    public String getName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid); // lookup by UUID: no network call
        return offline.getName();
    }

    /** Trusted players as names (online or offline); entries with no known name show their full UUID. */
    public List<String> getTrustedNames(UUID owner) {
        List<String> out = new ArrayList<>();
        for (UUID u : getTrusted(owner)) {
            String name = getName(u);
            out.add(name != null ? name : u.toString());
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /**
     * Resolves what a player typed in {@code /trust remove} to a UUID: a raw UUID, an online
     * player's exact name, or the name of someone in the owner's trusted list (online or not).
     * Never does a Mojang lookup. Returns null if nothing matches.
     */
    public UUID findTrusted(UUID owner, String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            // not a UUID, try names
        }

        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return online.getUniqueId();

        String wanted = input.toLowerCase(Locale.ROOT);
        for (UUID u : getTrusted(owner)) {
            String name = getName(u);
            if (name != null && name.toLowerCase(Locale.ROOT).equals(wanted)) return u;
        }
        return null;
    }

    /**
     * Pending requests only make sense while both players are online, so drop every request
     * to or from a player who just left. Trust itself is already saved to disk.
     */
    public void onPlayerQuit(UUID uuid) {
        pending.remove(uuid);
        for (Map.Entry<UUID, Set<UUID>> entry : pending.entrySet()) {
            Set<UUID> requestors = entry.getValue();
            requestors.remove(uuid);
            if (requestors.isEmpty()) pending.remove(entry.getKey(), requestors);
        }
    }
}