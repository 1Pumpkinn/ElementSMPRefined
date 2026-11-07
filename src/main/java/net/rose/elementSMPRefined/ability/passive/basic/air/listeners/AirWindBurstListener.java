package net.rose.elementSMPRefined.ability.passive.basic.air.listeners;

import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.data.PassiveToggle;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.managers.TeamManager;
import net.rose.elementSMPRefined.util.damage.AbilityDamage;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AirWindBurstListener implements Listener {

    private static final double MIN_FALL_DISTANCE = 1.5;
    // Exactly the Wind Burst enchantment's knockback multiplier lookup: level I / II / III.
    private static final double[] LEVEL_MULTIPLIERS = {1.2, 1.75, 2.2};
    private static final double BASE_LAUNCH_VELOCITY_Y = 0.75;
    private static final double BURST_RADIUS = 3.5;
    private static final double BASE_BURST_STRENGTH = 0.6;

    private static final boolean DEBUG = false;

    private final JavaPlugin plugin;
    private final ElementManager elementManager;
    private final TeamManager teamManager;

    private final Map<UUID, Double> peakY = new HashMap<>();

    public AirWindBurstListener(
            JavaPlugin plugin, ElementManager elementManager, TeamManager teamManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        this.teamManager = teamManager;
    }

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

    private static boolean isSupported(Player player) {
        return player.isOnGround()
                || player.isFlying()
                || player.isGliding()
                || player.isInsideVehicle()
                || player.isClimbing()
                || player.isSwimming()
                || player.isInWater()
                || player.isInLava()
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || isInFallBreakingBlock(player);
    }

    /**
     * Blocks that stop a fall dead (vanilla zeroes the fall distance while you're inside one): cobwebs,
     * sweet berry bushes and powder snow. Without this the tracked peak height survives a fall into a
     * web, so a player stuck in it was still treated as "falling" and could trigger Wind Burst.
     */
    private static boolean isInFallBreakingBlock(Player player) {
        BoundingBox box = player.getBoundingBox();
        World world = player.getWorld();
        final double eps = 1.0E-6;
        for (int x = (int) Math.floor(box.getMinX() + eps);
                x <= (int) Math.floor(box.getMaxX() - eps);
                x++) {
            for (int y = (int) Math.floor(box.getMinY() + eps);
                    y <= (int) Math.floor(box.getMaxY() - eps);
                    y++) {
                for (int z = (int) Math.floor(box.getMinZ() + eps);
                        z <= (int) Math.floor(box.getMaxZ() - eps);
                        z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type == Material.COBWEB
                            || type == Material.SWEET_BERRY_BUSH
                            || type == Material.POWDER_SNOW) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private double effectiveFallDistance(Player player) {
        double fall = player.getFallDistance();
        // Standing on something / in water / etc.: the fall is over, a stale peak must not revive it.
        if (isSupported(player)) return fall;
        Double peak = peakY.get(player.getUniqueId());
        if (peak != null) {
            fall = Math.max(fall, peak - player.getLocation().getY());
        }
        return fall;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        // Direct melee only - projectiles and abilities don't count
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK)
            return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (attacker.equals(target)) return;

        // Ability damage (Tornado, Magnetar, ...) is dealt with the caster as the attacker, so it
        // looks
        // like a melee hit. It is not one: never let it trigger Wind Burst.
        if (AbilityDamage.isAbilityDamage(target)) return;

        var data = elementManager.data(attacker.getUniqueId());
        if (data == null || !hasAirPassives(data.getCurrentElement())) return;

        // Player switched Wind Burst off with /toggle windburst
        if (!data.isToggleEnabled(PassiveToggle.WIND_BURST)) return;

        if (data.getUpgradeLevel(data.getCurrentElement()) < BaseElement.PASSIVE_2_UPGRADE_LEVEL)
            return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType().isAir()) return;

        // A real Wind Burst enchantment is handled by vanilla - never double up
        if (weapon.containsEnchantment(Enchantment.WIND_BURST)) return;

        // Stuck in a web (etc.): the fall is over. Forget the old peak so leaving the web doesn't
        // revive it.
        if (isInFallBreakingBlock(attacker)) {
            peakY.put(attacker.getUniqueId(), attacker.getLocation().getY());
            debug(attacker, "inside a fall-breaking block (web/berry bush/powder snow)");
            return;
        }

        // Vanilla: Doubles.atLeast(1.5), so exactly 1.5 counts.
        double fall = effectiveFallDistance(attacker);
        if (fall < MIN_FALL_DISTANCE) {
            debug(
                    attacker,
                    "fall distance too low: "
                            + fall
                            + " (server "
                            + attacker.getFallDistance()
                            + ")");
            return;
        }
        // Vanilla only rejects an attacker that is fall-flying (elytra). Every other "supported"
        // state already zeroes the fall distance, so it is not checked again here: re-checking the
        // client-reported onGround/climbing/swimming flags is what made the burst randomly fail.
        if (attacker.isGliding()) {
            debug(attacker, "gliding");
            return;
        }

        if (target instanceof Player victim
                && teamManager.isFriendly(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }

        // Captured now: the hit may kill the target, and a dead target has no valid position to
        // burst from
        Location center = target.getLocation().add(0, target.getHeight() / 2.0, 0);
        int level = data.getWindBurstLevel();

        // Let vanilla finish the hit (damage, hit sound, mace smash sound) before launching
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (!attacker.isOnline() || attacker.isDead()) return;
                            burst(attacker, center, level);
                        });
    }

    private void debug(Player attacker, String reason) {
        if (DEBUG) {
            plugin.getLogger().info("[WindBurst] " + attacker.getName() + " no burst: " + reason);
        }
    }

    private void burst(Player attacker, Location center, int level) {
        World world = center.getWorld();
        if (world == null) return;

        double multiplier =
                LEVEL_MULTIPLIERS[Math.max(1, Math.min(LEVEL_MULTIPLIERS.length, level)) - 1];
        double launchVelocityY = BASE_LAUNCH_VELOCITY_Y * multiplier;
        double burstStrength = BASE_BURST_STRENGTH * multiplier;

        // Launch the attacker and cancel the fall they just spent
        Vector velocity = attacker.getVelocity();
        velocity.setY(launchVelocityY);
        attacker.setVelocity(velocity);
        attacker.setFallDistance(0f);
        // The fall was just spent: restart the tracked drop from here so the next hit needs a new
        // fall
        peakY.put(attacker.getUniqueId(), attacker.getLocation().getY());

        // Blow everything nearby (including the target) away from the burst
        for (Entity entity :
                world.getNearbyEntities(center, BURST_RADIUS, BURST_RADIUS, BURST_RADIUS)) {
            if (entity.equals(attacker)) continue;
            if (!(entity instanceof LivingEntity living)) continue;
            if (living instanceof Player other
                    && teamManager.isFriendly(attacker.getUniqueId(), other.getUniqueId())) {
                continue;
            }

            Vector offset = living.getLocation().toVector().subtract(center.toVector());
            offset.setY(0);
            if (offset.lengthSquared() < 1.0E-4) {
                offset =
                        attacker.getLocation()
                                .getDirection()
                                .setY(0); // fallback: push the way the attacker faces
                if (offset.lengthSquared() < 1.0E-4) offset = new Vector(0, 0, 1);
            }
            Vector push = offset.normalize().multiply(burstStrength);
            push.setY(0.3);
            living.setVelocity(living.getVelocity().add(push));
        }
        world.spawnParticle(Particle.GUST_EMITTER_LARGE, center, 1);
        world.playSound(center, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f);
    }
}
