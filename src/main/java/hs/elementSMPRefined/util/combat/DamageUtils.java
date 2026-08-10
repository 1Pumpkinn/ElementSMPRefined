package hs.elementSMPRefined.util.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Optional;

public final class DamageUtils {
    public record DamageConfig(
            LivingEntity target,
            double amount,
            Optional<Player> source,
            boolean ignoreArmor,
            boolean applyKnockback,
            Vector knockbackDirection,
            double knockbackStrength
    ) {
        public DamageConfig {
            if (amount < 0) throw new IllegalArgumentException("Damage cannot be negative");
            if (knockbackStrength < 0) {
                throw new IllegalArgumentException("Knockback strength cannot be negative");
            }
        }

        public static DamageConfig simple(LivingEntity target, double amount) {
            return new DamageConfig(target, amount, Optional.empty(),
                    false, false, new Vector(0, 0, 0), 0);
        }

        public static DamageConfig withKnockback(LivingEntity target, double amount,
                                                 Vector direction, double strength) {
            return new DamageConfig(target, amount, Optional.empty(),
                    false, true, direction, strength);
        }

        public static DamageConfig trueWithKnockback(LivingEntity target, double amount,
                                                     Vector direction, double strength) {
            return new DamageConfig(target, amount, Optional.empty(),
                    true, true, direction, strength);
        }
    }

    public record DamageResult(
            boolean applied,
            double actualDamage,
            boolean killedTarget
    ) {}

    public static DamageResult applyDamage(DamageConfig config) {
        if (config.target().isDead() || !config.target().isValid()) {
            return new DamageResult(false, 0, false);
        }

        double actualDamage;

        if (config.ignoreArmor()) {
            double currentHealth = config.target().getHealth();
            double newHealth = Math.max(0, currentHealth - config.amount());
            config.target().setHealth(newHealth);
            actualDamage = currentHealth - newHealth;
        } else {
            if (config.source().isPresent()) {
                config.target().damage(config.amount(), config.source().get());
            } else {
                config.target().damage(config.amount());
            }
            actualDamage = config.amount();
        }

        if (config.applyKnockback() && config.knockbackDirection() != null) {
            Vector knockback = config.knockbackDirection().clone()
                    .normalize()
                    .multiply(config.knockbackStrength());
            config.target().setVelocity(knockback);
        }

        boolean killed = config.target().isDead();
        return new DamageResult(true, actualDamage, killed);
    }

    public static boolean isValidTarget(Player attacker, LivingEntity target,
                                        hs.elementSMPRefined.managers.TrustManager trustManager) {
        if (target.equals(attacker)) return false;
        if (target instanceof org.bukkit.entity.ArmorStand) return false;

        if (target instanceof Player targetPlayer) {
            return !trustManager.isTrusted(attacker.getUniqueId(),
                    targetPlayer.getUniqueId());
        }

        return true;
    }

    public static Vector calculateKnockback(LivingEntity attacker, LivingEntity target,
                                            double verticalComponent) {
        Vector direction = target.getLocation().toVector()
                .subtract(attacker.getLocation().toVector())
                .normalize();
        direction.setY(verticalComponent);
        return direction;
    }

    private DamageUtils() {}
}

