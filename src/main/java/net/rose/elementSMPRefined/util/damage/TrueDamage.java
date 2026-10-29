package net.rose.elementSMPRefined.util.damage;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * True damage helper.
 *
 * - Ignores armor, enchantments (prot etc), resistance, shield blocking, hard hat.
 * - Goes through the normal vanilla damage pipeline (EntityDamageEvent), so:
 *     * the red hit overlay + hurt sound/animation plays on mobs and players
 *     * totems of undying still trigger (health is never set directly)
 *     * other plugins (WorldGuard etc.) can still cancel it
 *     * kill credit / death messages work when an attacker is supplied
 *
 * Setup (once): this.trueDamage = new TrueDamage(this);   // registers its own listener
 *
 * Usage:
 *   TrueDamage.apply(target, 4.0);                       // 2 hearts, no attacker
 *   TrueDamage.apply(target, 4.0, attacker);             // with attacker (knockback + kill credit)
 *   TrueDamage.of(4.0).attacker(p).ignoreIFrames(false).bypassAbsorption(true).apply(target);
 *   TrueDamage.of(4.0).attacker(p).ability(getName()).apply(target);   // death message names the ability
 *   TrueDamage.of(4.0).attacker(p).blockableByShield(direction).apply(target); // projectile ability: a raised shield stops it
 */
public final class TrueDamage implements Listener {

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private record Pending(double amount, boolean bypassAbsorption, String abilityName) {}

    /** Creates the instance and registers its listener (auto-unregistered when the plugin disables). */
    public TrueDamage(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static boolean apply(LivingEntity target, double amount) {
        return of(amount).apply(target);
    }

    public static boolean apply(LivingEntity target, double amount, Entity attacker) {
        return of(amount).attacker(attacker).apply(target);
    }

    public static Builder of(double amount) {
        return new Builder(amount);
    }

    /** True while a true-damage hit is being processed on this entity (useful for your own listeners). */
    public static boolean isTrueDamage(Entity entity) {
        return PENDING.containsKey(entity.getUniqueId());
    }

    public static final class Builder {
        private final double amount;
        private Entity attacker = null;
        private DamageType type = DamageType.GENERIC;
        private boolean ignoreIFrames = true;
        private boolean bypassAbsorption = false;
        private String abilityName = null;
        private Vector shieldDirection = null;

        private Builder(double amount) {
            this.amount = amount;
        }

        /** Source of the damage (knockback direction + kill credit). Optional. */
        public Builder attacker(Entity attacker) {
            this.attacker = attacker;
            return this;
        }

        public Builder type(DamageType type) {
            this.type = type;
            return this;
        }

        /** If true (default) the hit lands even during invulnerability frames. */
        public Builder ignoreIFrames(boolean ignore) {
            this.ignoreIFrames = ignore;
            return this;
        }

        /** If true, absorption hearts are skipped and damage hits real health. Default false. */
        public Builder bypassAbsorption(boolean bypass) {
            this.bypassAbsorption = bypass;
            return this;
        }

        /**
         * Display name of the ability dealing this damage. If the hit kills a player, the death
         * message names it ("X was slain by Y using Magma Beam") instead of a generic message.
         */
        public Builder ability(String abilityName) {
            this.abilityName = abilityName;
            return this;
        }

        /**
         * Marks this as a projectile-style hit that a normal raised shield can stop. {@code travelDirection}
         * is the way the attack is moving (attacker -> victim). If the victim blocks it, no damage is dealt,
         * the shield loses durability (see {@link ShieldBlocking}) and {@link #apply} returns false - so
         * callers should skip on-hit effects (burning, slowing, knockback...) when it does.
         */
        public Builder blockableByShield(Vector travelDirection) {
            this.shieldDirection = travelDirection;
            return this;
        }

        /** Applies the damage. Returns false if skipped (invalid/dead/creative/shield-blocked/etc). */
        public boolean apply(LivingEntity target) {
            if (target == null || target.isDead() || !target.isValid()) return false;
            if (amount <= 0) return false;

            if (target instanceof Player player) {
                GameMode gm = player.getGameMode();
                if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return false;
            }
            if (target.isInvulnerable()) return false;

            UUID id = target.getUniqueId();
            if (PENDING.containsKey(id)) return false; // prevent re-entrancy / recursion

            if (shieldDirection != null && target instanceof Player victim
                    && ShieldBlocking.tryBlock(victim, shieldDirection, amount)) {
                return false;
            }

            if (ignoreIFrames) {
                target.setNoDamageTicks(0);
            }

            DamageSource.Builder sb = DamageSource.builder(type);
            if (attacker != null) {
                sb.withCausingEntity(attacker).withDirectEntity(attacker);
            }
            DamageSource source = sb.build();

            PENDING.put(id, new Pending(amount, bypassAbsorption, abilityName));
            try {
                target.damage(amount, source);
            } finally {
                PENDING.remove(id);
            }
            return true;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onDamage(EntityDamageEvent event) {
        Pending pending = PENDING.get(event.getEntity().getUniqueId());
        if (pending == null) return;

        for (DamageModifier mod : DamageModifier.values()) {
            if (mod == DamageModifier.BASE) continue;
            if (!event.isApplicable(mod)) continue;
            if (mod == DamageModifier.ABSORPTION && !pending.bypassAbsorption()) continue;
            event.setDamage(mod, 0);
        }
        event.setDamage(DamageModifier.BASE, pending.amount());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (event.deathMessage() == null) return; // death messages are turned off
        Player victim = event.getEntity();
        Pending pending = PENDING.get(victim.getUniqueId());
        String abilityName = pending != null ? pending.abilityName() : AbilityDamage.abilityName(victim);
        if (abilityName == null) return;
        event.deathMessage(AbilityDeathMessage.build(victim, abilityName));
    }
}
