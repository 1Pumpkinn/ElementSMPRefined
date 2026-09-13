package net.rose.elementSMPRefined.ability.main.metal;

import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class MetalShardAbility extends BaseAbility {

    private static final int SHARD_COUNT = 3;
    private static final double SPREAD_DEGREES = 6.0; // angle between each shard
    private static final double RANGE = 35.0;
    private static final double HITBOX_EXPAND = 0.35; // ray tolerance for near-misses
    private static final int MAX_PIERCE_PER_SHARD = 4; // entities a single shard can pass through
    private static final double DAMAGE = 4.0; // 2 hearts per shard hit

    public MetalShardAbility(JavaPlugin plugin, ConfigManager configManager) {
        // slot 2 assumed - confirm against wherever this replaces/joins MetalDashAbility
        super("metal_shard", ElementType.METAL, 1, 8, 1, configManager);
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        if (player == null) return false;

        Location eye = player.getEyeLocation();
        Vector baseDir = eye.getDirection();

        for (double offsetDeg : buildOffsets()) {
            Vector dir = rotateAroundY(baseDir.clone(), offsetDeg);
            fireShard(context, player, eye.clone(), dir);
        }

        SoundUtils.playTo(player, SoundUtils.Element.METAL);
        return true;
    }

    private double[] buildOffsets() {
        double[] offsets = new double[SHARD_COUNT];
        double start = -SPREAD_DEGREES * (SHARD_COUNT - 1) / 2.0;
        for (int i = 0; i < SHARD_COUNT; i++) {
            offsets[i] = start + (i * SPREAD_DEGREES);
        }
        return offsets;
    }

    private void fireShard(ElementContext context, Player caster, Location origin, Vector direction) {
        Set<LivingEntity> hitOrder = new LinkedHashSet<>();
        Location cursor = origin.clone();
        double travelled = 0.0;
        double step = 0.5;

        while (travelled < RANGE && hitOrder.size() < MAX_PIERCE_PER_SHARD) {
            cursor.add(direction.clone().multiply(step));
            travelled += step;

            spawnTrailParticle(cursor);

            RayTraceResult result = caster.getWorld().rayTraceEntities(
                    cursor, direction, step + HITBOX_EXPAND,
                    entity -> isValidTarget(context, entity, caster, hitOrder)
            );

            if (result != null && result.getHitEntity() instanceof LivingEntity target) {
                hitOrder.add(target);
                strike(target);
            }

            if (cursor.getBlock().getType().isSolid()) {
                break; // stop the shard at solid terrain
            }
        }
    }

    private boolean isValidTarget(ElementContext context, Entity entity, Player caster, Set<LivingEntity> alreadyHit) {
        if (!(entity instanceof LivingEntity living)) return false;
        if (entity.equals(caster)) return false;
        if (alreadyHit.contains(living)) return false;
        if (!living.isValid() || living.isDead()) return false;

        if (living instanceof Player targetPlayer
                && context.getTrustManager() != null
                && context.getTrustManager().isTrusted(caster.getUniqueId(), targetPlayer.getUniqueId())) {
            return false;
        }
        return true;
    }

    /**
     * Applies damage while bypassing shield blocking: a shield only blocks while
     * the defender's main hand holds it, so we swap it out for the instant of the
     * damage call and put it right back.
     */
    private void strike(LivingEntity target) {
        ItemStack savedHand = null;
        boolean wasBlocking = target instanceof Player targetPlayer && targetPlayer.isBlocking();

        if (wasBlocking) {
            Player targetPlayer = (Player) target;
            savedHand = targetPlayer.getInventory().getItemInMainHand();
            targetPlayer.getInventory().setItemInMainHand(null);
        }

        target.damage(DAMAGE);

        if (wasBlocking) {
            ((Player) target).getInventory().setItemInMainHand(savedHand);
        }

        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.05);
        if (target instanceof Player targetPlayer) {
            SoundUtils.playTo(targetPlayer, SoundUtils.Combat.HIT);
        }
    }

    private void spawnTrailParticle(Location loc) {
        loc.getWorld().spawnParticle(Particle.CRIT, loc, 1, 0, 0, 0, 0);
    }

    private Vector rotateAroundY(Vector v, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = v.getX() * cos + v.getZ() * sin;
        double z = -v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z).normalize();
    }

    @Override
    public String getName() {
        return ChatColor.GRAY + "Metal Shard";
    }

    @Override
    public String getDescription() {
        return "Damage entities by shooting Metal Shards at them";
    }

    /**
     * No per-player state to clean up currently (unlike MetalDashAbility's
     * stunned/dashing/pendingStun sets) - kept as a no-op so PlayerLifecycle's
     * quit hook still compiles if this ability grows player-keyed state later.
     */
    public void onPlayerQuit(UUID playerUuid) {
    }
}