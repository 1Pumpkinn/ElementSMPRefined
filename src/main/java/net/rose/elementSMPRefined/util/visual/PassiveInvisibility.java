package net.rose.elementSMPRefined.util.visual;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players whose current invisibility comes from an element passive,
 * Passives call {@link #mark} when they apply the effect and {@link #unmark}
 * when they remove it.
 */
public final class PassiveInvisibility {

    private static final Set<UUID> PASSIVE = ConcurrentHashMap.newKeySet();

    private PassiveInvisibility() {
    }

    public static void mark(Player player) {
        PASSIVE.add(player.getUniqueId());
    }

    public static void unmark(Player player) {
        PASSIVE.remove(player.getUniqueId());
    }

    public static void unmark(UUID uuid) {
        PASSIVE.remove(uuid);
    }

    public static boolean isPassive(Player player) {
        return PASSIVE.contains(player.getUniqueId());
    }
}