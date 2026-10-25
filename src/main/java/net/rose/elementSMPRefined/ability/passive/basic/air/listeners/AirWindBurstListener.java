package net.rose.elementSMPRefined.ability.passive.basic.air.listeners;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Air Element Passive 2: Wind Burst
 *
 * Built-in emulation of the Wind Burst enchantment. It is NOT a real enchantment:
 * nothing is ever added to or removed from an item, so it can never be obtained,
 * applied, anvil'd or stripped, and legitimately enchanted items are never touched.
 *
 * Works with any held item. Mirrors the vanilla trigger for a mace smash attack:
 * a direct melee hit while falling more than {@link #MIN_FALL_DISTANCE} blocks.
 * Launches the attacker upward and bursts nearby entities away from the target.
 *
 * Skipped when the held item already has a real Wind Burst enchantment, so vanilla
 * handles it and the effect never doubles up.
 *
 * Also inherited by Storm (upgraded from Air).
 */
public class AirWindBurstListener implements Listener {

    /** Same threshold vanilla uses for a mace smash attack. */
    private static final double MIN_FALL_DISTANCE = 1.5;
    /** Equivalent to Wind Burst level I (vanilla multipliers: I=1.2, II=1.75, III=2.2). */
    private static final double LEVEL_MULTIPLIER = 1.2;
    private static final double LAUNCH_VELOCITY_Y = 0.75 * LEVEL_MULTIPLIER;
    private static final double BURST_RADIUS = 3.5;
    private static final double BURST_STRENGTH = 0.6 * LEVEL_MULTIPLIER;

    private final ElementManager elementManager;
    private final TrustManager trustManager;

    public AirWindBurstListener(ElementManager elementManager, TrustManager trustManager) {
        this.elementManager = elementManager;
        this.trustManager = trustManager;
    }

    /** Air, and Storm (upgraded from Air, inherits these passives). */
    private static boolean hasAirPassives(ElementType type) {
        return type == ElementType.AIR || type == ElementType.STORM;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        // Direct melee only - projectiles and abilities don't count
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (attacker.equals(target)) return;

        var data = elementManager.data(attacker.getUniqueId());
        if (data == null || !hasAirPassives(data.getCurrentElement())) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType().isAir()) return;

        // A real Wind Burst enchantment is handled by vanilla - never double up
        if (weapon.containsEnchantment(Enchantment.WIND_BURST)) return;

        // Smash-attack conditions
        if (attacker.getFallDistance() <= MIN_FALL_DISTANCE) return;
        if (attacker.isOnGround() || attacker.isFlying() || attacker.isGliding()
                || attacker.isInsideVehicle() || attacker.isClimbing() || attacker.isSwimming()) return;

        if (target instanceof Player victim
                && trustManager.isTrusted(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }

        burst(attacker, target);
    }

    private void burst(Player attacker, LivingEntity target) {
        Location center = target.getLocation().add(0, target.getHeight() / 2.0, 0);
        World world = center.getWorld();
        if (world == null) return;

        // Launch the attacker and cancel the fall they just spent
        Vector velocity = attacker.getVelocity();
        velocity.setY(LAUNCH_VELOCITY_Y);
        attacker.setVelocity(velocity);
        attacker.setFallDistance(0f);

        // Blow everything nearby (including the target) away from the burst
        for (Entity entity : world.getNearbyEntities(center, BURST_RADIUS, BURST_RADIUS, BURST_RADIUS)) {
            if (entity.equals(attacker)) continue;
            if (!(entity instanceof LivingEntity living)) continue;
            if (living instanceof Player other
                    && trustManager.isTrusted(attacker.getUniqueId(), other.getUniqueId())) {
                continue;
            }

            Vector offset = living.getLocation().toVector().subtract(center.toVector());
            offset.setY(0);
            if (offset.lengthSquared() < 1.0E-4) {
                offset = attacker.getLocation().getDirection().setY(0); // fallback: push the way the attacker faces
                if (offset.lengthSquared() < 1.0E-4) offset = new Vector(0, 0, 1);
            }
            Vector push = offset.normalize().multiply(BURST_STRENGTH);
            push.setY(0.3);
            living.setVelocity(living.getVelocity().add(push));
        }

        world.spawnParticle(Particle.GUST_EMITTER_LARGE, center, 1);
        world.spawnParticle(Particle.CLOUD, center, 25, 0.5, 0.3, 0.5, 0.1);
        world.playSound(center, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f);
    }
}
