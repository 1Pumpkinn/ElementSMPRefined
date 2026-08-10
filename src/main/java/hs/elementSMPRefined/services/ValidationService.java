package hs.elementSMPRefined.services;

import hs.elementPlugin.data.PlayerData;
import hs.elementPlugin.elements.ElementType;
import hs.elementPlugin.managers.TrustManager;
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

    public boolean hasUpgradeLevel(PlayerData pd, int required) {
        return pd.getCurrentElementUpgradeLevel() >= required;
    }

    public boolean canUseElementItem(Player player, ElementType itemElement, PlayerData pd) {
        return pd.getCurrentElement() == itemElement;
    }
}