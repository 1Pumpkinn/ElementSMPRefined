package net.rose.elementSMPRefined.util.compat;

import com.viaversion.viaversion.api.Via;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Client-version checks for players joining through ViaVersion/ViaBackwards.
 * Particles added in 26.2 (e.g. {@code Particle.GEYSER}) crash older clients that are
 * being translated down, so anything 26.2-only must be gated through here.
 */
public final class ClientCompat {

    private ClientCompat() {}

    /**
     * True if this player's client natively understands 26.2-only particles, i.e. it is
     * on the server's own protocol (or newer) rather than being translated down by Via.
     * Falls back to {@code true} when ViaVersion isn't installed (everyone is native) and
     * {@code false} if the Via API errors, so the safe fallback visual is used.
     */
    public static boolean supportsNewParticles(Player player) {
        if (Bukkit.getPluginManager().getPlugin("ViaVersion") == null) return true;
        try {
            int client = Via.getAPI().getPlayerVersion(player.getUniqueId());
            return client < 0 || client >= Bukkit.getUnsafe().getProtocolVersion();
        } catch (Throwable t) {
            return false;
        }
    }
}
