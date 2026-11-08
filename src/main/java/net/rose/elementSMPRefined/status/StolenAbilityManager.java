package net.rose.elementSMPRefined.status;

import net.rose.elementSMPRefined.core.API.element.ElementType;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players who are temporarily casting another element's abilities (Death's Soul Siphon).
 * <p>
 * It also tracks how long each drained player stays drained, so the cooldown action bar can show
 * them a timer.
 * <p>
 * Like {@link DisarmManager}, expiries are absolute wall-clock timestamps and every read
 * re-checks them, so nothing needs to keep ticking for a steal to end on time.
 */
public class StolenAbilityManager {
    private record Steal(ElementType type, UUID victim, long expiryMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiryMillis;
        }
    }

    private final Map<UUID, Steal> stolen = new ConcurrentHashMap<>();
    private final Map<UUID, Long> drainedUntil = new ConcurrentHashMap<>();

    public void steal(Player caster, Player victim, ElementType type, int durationTicks) {
        long expiry = System.currentTimeMillis() + (durationTicks * 50L);
        stolen.put(caster.getUniqueId(), new Steal(type, victim.getUniqueId(), expiry));
        drainedUntil.put(victim.getUniqueId(), expiry);
    }

    /** Seconds left on this player's Soul Siphon drain, or 0 if their abilities aren't drained. */
    public long getDrainedRemainingSeconds(Player player) {
        Long expiry = drainedUntil.get(player.getUniqueId());
        if (expiry == null) return 0;
        long remainingMillis = expiry - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            drainedUntil.remove(player.getUniqueId());
            return 0;
        }
        return (remainingMillis + 999) / 1000;
    }

    /**
     * Ends the caster's active steal early and removes the drained player's timer.
     *
     * @return the UUID of the player whose abilities were being siphoned, or {@code null} if the
     *     caster had no active steal
     */
    public UUID cancel(Player caster) {
        Steal steal = stolen.remove(caster.getUniqueId());
        if (steal == null || steal.isExpired()) return null;
        drainedUntil.remove(steal.victim());
        return steal.victim();
    }

    /** The element whose abilities this player is currently casting, or {@code null} if none. */
    public ElementType getStolenElement(Player player) {
        Steal steal = stolen.get(player.getUniqueId());
        if (steal == null) return null;
        if (steal.isExpired()) {
            stolen.remove(player.getUniqueId());
            return null;
        }
        return steal.type();
    }

    public void clear(Player player) {
        stolen.remove(player.getUniqueId());
    }

    /** Drops finished steals so the map doesn't keep an entry for every player who ever cast one. */
    public void pruneExpired() {
        stolen.values().removeIf(Steal::isExpired);
        long now = System.currentTimeMillis();
        drainedUntil.values().removeIf(expiry -> now >= expiry);
    }

    public void cleanup() {
        stolen.clear();
        drainedUntil.clear();
    }
}
