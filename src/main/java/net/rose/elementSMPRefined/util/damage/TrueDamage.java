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
import org.bukkit.plugin.Plugin;

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
 */
public final class TrueDamage implements Listener {

    /** Entities currently being hit by true damage -> the exact amount to deal. */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private record Pending(double amount, boolean bypassAbsorption) {}

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

        private Builder(double amount) {
            this.amount = amount;
        }

        /** Source of the damage (knockback direction + kill credit). Optional. */
        public Builder attacker(Entity attacker) {
            this.attacker = attacker;
            return this;
        }

        /** Damage type shown for death messages. Default GENERIC. */
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

        /** Applies the damage. Returns false if skipped (invalid/dead/creative/etc). */
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

            if (ignoreIFrames) {
                target.setNoDamageTicks(0);
            }

            DamageSource.Builder sb = DamageSource.builder(type);
            if (attacker != null) {
                sb.withCausingEntity(attacker).withDirectEntity(attacker);
            }
            DamageSource source = sb.build();

            PENDING.put(id, new Pending(amount, bypassAbsorption));
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
}