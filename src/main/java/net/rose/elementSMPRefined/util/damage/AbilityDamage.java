package net.rose.elementSMPRefined.util.damage;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tags damage that comes from an element ability, so defensive abilities (e.g. the Metal
 * Shield) can tell "a damage ability just hit me" apart from plain melee / projectile hits.
 * <p>
 * Anything dealt through {@link TrueDamage} is already recognised. Abilities that call
 * {@link LivingEntity#damage} directly wrap that call in {@link #deal} instead:
 * <pre>
 *   AbilityDamage.deal(target, () -> target.damage(4.0, caster));
 * </pre>
 * Like {@link TrueDamage}, the flag only exists for the duration of the synchronous
 * {@code damage()} call, which is exactly when the damage event fires.
 */
public final class AbilityDamage {

    private static final Map<UUID, boolean[]> IN_FLIGHT = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();

    private AbilityDamage() {}

    /**
     * Runs {@code damageCall} with {@code target} flagged as taking ability damage.
     *
     * @return false if an ability shield absorbed the hit (see {@link #markBlocked}), true otherwise
     */
    public static boolean deal(LivingEntity target, Runnable damageCall) {
        return deal(target, null, damageCall);
    }

    /**
     * Same as {@link #deal(LivingEntity, Runnable)}, but if this hit kills a player the death
     * message names {@code abilityName} instead of whatever the attacker happens to be holding.
     */
    public static boolean deal(LivingEntity target, String abilityName, Runnable damageCall) {
        UUID id = target.getUniqueId();
        boolean[] blocked = new boolean[1];
        boolean outermost = IN_FLIGHT.putIfAbsent(id, blocked) == null;
        if (outermost && abilityName != null) NAMES.put(id, abilityName);
        try {
            damageCall.run();
        } finally {
            if (outermost) {
                IN_FLIGHT.remove(id);
                NAMES.remove(id);
            }
        }
        return !blocked[0];
    }

    static String abilityName(Entity entity) {
        return NAMES.get(entity.getUniqueId());
    }

    /** True while ability damage (via {@link #deal} or {@link TrueDamage}) is being processed on this entity. */
    public static boolean isAbilityDamage(Entity entity) {
        return IN_FLIGHT.containsKey(entity.getUniqueId()) || TrueDamage.isTrueDamage(entity);
    }

    /** Called by a shield that cancelled the hit, so {@link #deal} can report it to the ability. */
    public static void markBlocked(Entity entity) {
        boolean[] blocked = IN_FLIGHT.get(entity.getUniqueId());
        if (blocked != null) blocked[0] = true;
    }
}
