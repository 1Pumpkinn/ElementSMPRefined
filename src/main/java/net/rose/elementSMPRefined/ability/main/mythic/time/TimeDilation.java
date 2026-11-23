package net.rose.elementSMPRefined.ability.main.mythic.time;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Slows time" for a living entity by scaling every attribute that decides how fast it acts, so
 * the client predicts it too (no rubber-banding like a velocity hack would cause).
 * <p>
 * With {@code k} = {@link #TIME_SCALE}: movement, jump strength, attack speed and block break speed
 * all run at {@code k}; gravity runs at {@code k^2}. Speeds scale with {@code k} but accelerations
 * with {@code k^2}, which is what keeps a jump the same height, only slower.
 * <p>
 * Several Time Rings can overlap, so each entity is held by a set of owners and the modifiers only
 * come off when the last owner lets go. All modifiers are transient: they are never saved with the
 * entity, so a crash or a logout can't leave anyone permanently slowed.
 */
public final class TimeDilation {

    /** How fast things inside a ring run compared to normal (0.4 = 60% slower). */
    public static final double TIME_SCALE = 0.4;

    private static final NamespacedKey KEY = new NamespacedKey("elementsmp", "time_ring_slow");

    private record Slow(Attribute attribute, double amount) {}

    private static final List<Slow> SLOWS =
            List.of(
                    new Slow(Attribute.MOVEMENT_SPEED, TIME_SCALE - 1.0),
                    new Slow(Attribute.ATTACK_SPEED, TIME_SCALE - 1.0),
                    new Slow(Attribute.BLOCK_BREAK_SPEED, TIME_SCALE - 1.0),
                    new Slow(Attribute.JUMP_STRENGTH, TIME_SCALE - 1.0),
                    new Slow(Attribute.GRAVITY, TIME_SCALE * TIME_SCALE - 1.0));

    private static final class Held {
        final LivingEntity entity;
        final Set<Object> owners = new HashSet<>();

        Held(LivingEntity entity) {
            this.entity = entity;
        }
    }

    private static final Map<UUID, Held> HELD = new HashMap<>();

    private TimeDilation() {}

    /**
     * Slows {@code entity} on behalf of {@code owner}. Safe to call every tick: it does nothing
     * more than remember the owner once the entity is already slowed.
     */
    public static void hold(LivingEntity entity, Object owner) {
        UUID id = entity.getUniqueId();
        Held held = HELD.get(id);
        if (held != null && held.entity != entity) {
            // A respawned player or a reloaded mob is a new object: the old one's modifiers went
            // with it, so start again on the new one.
            HELD.remove(id);
            held = null;
        }
        if (held == null) {
            held = new Held(entity);
            HELD.put(id, held);
            apply(entity);
        }
        held.owners.add(owner);
    }

    /** {@code owner} no longer slows this entity; it speeds back up once nobody else does. */
    public static void release(UUID id, Object owner) {
        Held held = HELD.get(id);
        if (held == null) return;
        held.owners.remove(owner);
        if (!held.owners.isEmpty()) return;
        HELD.remove(id);
        strip(held.entity);
    }

    /** Speeds everything back up. Called when the plugin shuts down. */
    public static void releaseAll() {
        for (Held held : new ArrayList<>(HELD.values())) {
            strip(held.entity);
        }
        HELD.clear();
    }

    private static void apply(LivingEntity entity) {
        for (Slow slow : SLOWS) {
            AttributeInstance attribute = entity.getAttribute(slow.attribute());
            if (attribute == null || find(attribute) != null) continue;
            attribute.addTransientModifier(
                    new AttributeModifier(
                            KEY, slow.amount(), AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    private static void strip(LivingEntity entity) {
        // Gone (logged off, unloaded): its transient modifiers went with it.
        if (!entity.isValid()) return;
        for (Slow slow : SLOWS) {
            AttributeInstance attribute = entity.getAttribute(slow.attribute());
            if (attribute == null) continue;
            AttributeModifier modifier = find(attribute);
            if (modifier != null) attribute.removeModifier(modifier);
        }
    }

    private static AttributeModifier find(AttributeInstance attribute) {
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (KEY.equals(modifier.getKey())) return modifier;
        }
        return null;
    }
}
