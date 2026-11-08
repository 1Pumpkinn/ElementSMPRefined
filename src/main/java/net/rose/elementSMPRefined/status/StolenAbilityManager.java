package net.rose.elementSMPRefined.status;

import net.rose.elementSMPRefined.core.API.element.ElementType;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players who are temporarily casting another element's abilities (Death's Soul Siphon).
 * <p>
 * Like {@link DisarmManager}, expiries are absolute wall-clock timestamps and every read
 * re-checks them, so nothing needs to keep ticking for a steal to end on time.
 */
public class StolenAbilityManager {
    private record Steal(ElementType type, long expiryMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiryMillis;
        }
    }

    private final Map<UUID, Steal> stolen = new ConcurrentHashMap<>();

    public void steal(Player caster, ElementType type, int durationTicks) {
        long expiry = System.currentTimeMillis() + (durationTicks * 50L);
        stolen.put(caster.getUniqueId(), new Steal(type, expiry));
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
    }

    public void cleanup() {
        stolen.clear();
    }
}
