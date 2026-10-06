package net.rose.elementSMPRefined.ability.main.upgraded.lava;

import net.rose.elementSMPRefined.managers.TrustManager;
import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Shared "can this lava ability hurt that entity" rule for the Lava element's abilities. */
final class LavaTargets {
    private LavaTargets() {}

    static boolean isValid(Player caster, LivingEntity target, TrustManager trust) {
        if (target.equals(caster) || target.isDead() || !target.isValid()) return false;
        if (target instanceof ArmorStand) return false;
        if (target instanceof Player other) {
            if (other.getGameMode() == GameMode.SPECTATOR || other.getGameMode() == GameMode.CREATIVE) return false;
            return !trust.isTrusted(caster.getUniqueId(), other.getUniqueId());
        }
        return true;
    }
}
