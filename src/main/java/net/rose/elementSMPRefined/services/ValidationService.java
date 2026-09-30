package net.rose.elementSMPRefined.services;

import net.rose.elementSMPRefined.managers.TrustManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class ValidationService {
    private final TrustManager trustManager;

    public ValidationService(TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    public boolean isValidTarget(Player attacker, LivingEntity target) {
        if (target.equals(attacker)) return false;

        if (target instanceof Player targetPlayer) {
            return !trustManager.isTrusted(attacker.getUniqueId(), targetPlayer.getUniqueId());
        }

        return true;
    }
}