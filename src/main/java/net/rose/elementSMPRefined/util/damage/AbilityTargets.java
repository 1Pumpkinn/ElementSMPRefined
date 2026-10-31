package net.rose.elementSMPRefined.util.damage;

import net.rose.elementSMPRefined.managers.TrustManager;
import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The shared "can this ability hit that entity?" check: not the caster, alive, not an armor stand,
 * and for players not in creative/spectator and not trusted by the caster.
 */
public final class AbilityTargets {

    private AbilityTargets() {}

    /** @param trust may be null, in which case nobody counts as trusted */
    public static boolean isValid(Player caster, LivingEntity target, TrustManager trust) {
        if (target.equals(caster) || target.isDead() || !target.isValid()) return false;
        if (target instanceof ArmorStand) return false;
        if (target instanceof Player other) {
            GameMode mode = other.getGameMode();
            if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) return false;
            return trust == null || !trust.isTrusted(caster.getUniqueId(), other.getUniqueId());
        }
        return true;
    }
}
