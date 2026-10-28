package net.rose.elementSMPRefined.ability.passive.basic.air.listeners;

import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.data.PassiveToggle;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.damage.AbilityDamage;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
 * Unlocks at Upgrade II. Also inherited by Storm (upgraded from Air). Players can switch it
 * off/on with /toggle windburst.
 *
 * Sounds: this passive plays no sound of its own, and the launch is deferred by one tick so
 * it never interferes with vanilla hit / mace smash sounds. Resetting the fall distance
 * inside the damage event used to stop vanilla from treating the hit as a smash attack,
 * which silenced the mace.
 *
 * Reliability: the fall check uses the larger of the server's fall distance and the drop from the
 * player's own highest point since they last stood on something, because the server's value can
 * be reset or lag behind after wind charge launches. The burst is also applied when the hit
 * kills the target (the launch used to be skipped because the dead target was no longer valid).
 * Set {@link #DEBUG} to true to log why a hit did not trigger a burst.
 */
public class AirWindBurstListener implements Listener {

    /** Same threshold vanilla uses for a mace smash attack. */
    private static final double MIN_FALL_DISTANCE = 1.5;
    /** Equivalent to Wind Burst level I (vanilla multipliers: I=1.2, II=1.75, III=2.2). */
    private static final double LEVEL_MULTIPLIER = 1.2;
    private static final double LAUNCH_VELOCITY_Y = 0.75 * LEVEL_MULTIPLIER;
    private static final double BURST_RADIUS = 3.5;
    private static final double BURST_STRENGTH = 0.6 * LEVEL_MULTIPLIER;

    /** Logs to console why an eligible Air/Storm hit did not trigger a burst. */
    private static final boolean DEBUG = false;

    private final JavaPlugin plugin;
    private final ElementManager elementManager;
    private final TrustManager trustManager;

    /** Highest Y each Air/Storm player has been at since they last stood on something. */
    private final Map<UUID, Double> peakY = new HashMap<>();

    public AirWindBurstListener(JavaPlugin plugin, ElementManager elementManager, TrustManager trustManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        this.trustManager = trustManager;
    }

    /** Air, and Storm (upgraded from Air, inherits these passives). */
    private static boolean hasAirPassives(ElementType type) {
        return type == ElementType.AIR || type == ElementType.STORM;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) return;

        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        var data = elementManager.data(id);
        if (data == null || !hasAirPassives(data.getCurrentElement())) {
            if (!peakY.isEmpty()) peakY.remove(id);
            return;
        }

        double y = event.getTo().getY();
        if (isSupported(player)) {
            peakY.put(id, y);
        } else {
            peakY.merge(id, y, Math::max);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        peakY.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        peakY.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        peakY.remove(event.getPlayer().getUniqueId());
    }

    /** Standing on something, or in a state where vanilla doesn't count falling at all. */
    private static boolean isSupported(Player player) {
        return player.isOnGround() || player.isFlying() || player.isGliding()
                || player.isInsideVehicle() || player.isClimbing() || player.isSwimming()
                || player.isInWater() || player.isInLava()
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
    }

    /** Larger of the server's fall distance and the drop from the player's own highest point. */
    private double effectiveFallDistance(Player player) {
        double fall = player.getFallDistance();
        Double peak = peakY.get(player.getUniqueId());
        if (peak != null) {
            fall = Math.max(fall, peak - player.getLocation().getY());
        }
        return fall;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        // Direct melee only - projectiles and abilities don't count
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (attacker.equals(target)) return;

        // Ability damage (Tornado, Magnetar, ...) is dealt with the caster as the attacker, so it looks
        // like a melee hit. It is not one: never let it trigger Wind Burst.
        if (AbilityDamage.isAbilityDamage(target)) return;

        var data = elementManager.data(attacker.getUniqueId());
        if (data == null || !hasAirPassives(data.getCurrentElement())) return;

        // Player switched Wind Burst off with /toggle windburst
        if (!data.isToggleEnabled(PassiveToggle.WIND_BURST)) return;

        // Second passive: unlocks at Upgrade II
        if (data.getUpgradeLevel(data.getCurrentElement()) < BaseElement.PASSIVE_2_UPGRADE_LEVEL) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType().isAir()) return;

        // A real Wind Burst enchantment is handled by vanilla - never double up
        if (weapon.containsEnchantment(Enchantment.WIND_BURST)) return;

        // Smash-attack conditions
        double fall = effectiveFallDistance(attacker);
        if (fall <= MIN_FALL_DISTANCE) {
            debug(attacker, "fall distance too low: " + fall + " (server " + attacker.getFallDistance() + ")");
            return;
        }
        if (attacker.isOnGround() || attacker.isFlying() || attacker.isGliding()
                || attacker.isInsideVehicle() || attacker.isClimbing() || attacker.isSwimming()) {
            debug(attacker, "blocked by state: onGround=" + attacker.isOnGround()
                    + " flying=" + attacker.isFlying() + " gliding=" + attacker.isGliding()
                    + " vehicle=" + attacker.isInsideVehicle() + " climbing=" + attacker.isClimbing()
                    + " swimming=" + attacker.isSwimming());
            return;
        }

        if (target instanceof Player victim
                && trustManager.isTrusted(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }

        // Captured now: the hit may kill the target, and a dead target has no valid position to burst from
        Location center = target.getLocation().add(0, target.getHeight() / 2.0, 0);

        // Let vanilla finish the hit (damage, hit sound, mace smash sound) before launching
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!attacker.isOnline() || attacker.isDead()) return;
            burst(attacker, center);
        });
    }

    private void debug(Player attacker, String reason) {
        if (DEBUG) {
            plugin.getLogger().info("[WindBurst] " + attacker.getName() + " no burst: " + reason);
        }
    }

    private void burst(Player attacker, Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Launch the attacker and cancel the fall they just spent
        Vector velocity = attacker.getVelocity();
        velocity.setY(LAUNCH_VELOCITY_Y);
        attacker.setVelocity(velocity);
        attacker.setFallDistance(0f);
        // The fall was just spent: restart the tracked drop from here so the next hit needs a new fall
        peakY.put(attacker.getUniqueId(), attacker.getLocation().getY());

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
        world.spawnParticle(Particle.GUST_EMITTER_SMALL, center, 1);
        world.playSound(center, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f);
    }
}