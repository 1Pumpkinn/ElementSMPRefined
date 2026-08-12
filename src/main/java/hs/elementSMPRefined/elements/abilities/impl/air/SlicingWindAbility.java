package hs.elementSMPRefined.elements.abilities.impl.air;

import hs.elementSMPRefined.elements.ElementContext;
import hs.elementSMPRefined.elements.abilities.BaseAbility;
import hs.elementSMPRefined.managers.ManaManager;
import hs.elementSMPRefined.managers.TrustManager;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Slicing Wind - fires a fast-moving blade of compressed air in front of the
 * player that cuts through anything in a narrow line, dealing damage and
 * knocking targets away.
 */
public class SlicingWindAbility extends BaseAbility {
    private final hs.elementSMPRefined.ElementSMPRefined plugin;

    public SlicingWindAbility(hs.elementSMPRefined.ElementSMPRefined plugin) {
        super("slicing_wind", 40, 6, 1);
        this.plugin = plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        ManaManager mana = context.getManaManager();
        TrustManager trust = context.getTrustManager();
        int cost = getManaCost();

        if (!mana.hasMana(player, cost)) {
            player.sendMessage(ChatColor.RED + "Not enough mana (" + cost + ")");
            return false;
        }

        World w = player.getWorld();
        Vector direction = player.getLocation().getDirection().normalize();
        Location origin = player.getEyeLocation();

        w.playSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 1.4f);

        double range = 20.0;
        double hitboxWidth = 1.1;
        double damage = 5.0;

        new BukkitRunnable() {
            double travelled = 0;
            final java.util.Set<java.util.UUID> hitEntities = new java.util.HashSet<>();

            @Override
            public void run() {
                if (!player.isOnline() || travelled >= range) {
                    cancel();
                    return;
                }

                travelled += 1.5;
                Location slice = origin.clone().add(direction.clone().multiply(travelled));

                // Slashing crescent particle effect
                for (int deg = -60; deg <= 60; deg += 15) {
                    double rad = Math.toRadians(deg);
                    Vector offset = rotateAroundVertical(direction, rad).multiply(0.6);
                    Location particleLoc = slice.clone().add(offset.getX(), 0, offset.getZ());
                    w.spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 0, 0, 0, 0, 0, null, true);
                    w.spawnParticle(Particle.CLOUD, particleLoc, 1, 0.05, 0.05, 0.05, 0.0, null, true);
                }

                for (LivingEntity e : slice.getNearbyLivingEntities(hitboxWidth)) {
                    if (e.equals(player)) continue;
                    if (e instanceof Player other && trust.isTrusted(player.getUniqueId(), other.getUniqueId())) continue;
                    if (hitEntities.contains(e.getUniqueId())) continue;

                    hitEntities.add(e.getUniqueId());
                    e.damage(damage, player);

                    Vector knockback = direction.clone().multiply(1.4).setY(0.25);
                    e.setVelocity(e.getVelocity().add(knockback));
                    e.getWorld().spawnParticle(Particle.SWEEP_ATTACK, e.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0, null, true);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    /**
     * Rotates a horizontal direction vector around the vertical (Y) axis by the given angle in radians.
     */
    private Vector rotateAroundVertical(Vector direction, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = direction.getX() * cos - direction.getZ() * sin;
        double z = direction.getX() * sin + direction.getZ() * cos;
        return new Vector(x, 0, z);
    }

    @Override
    public String getName() {
        return ChatColor.WHITE + "Slicing Wind";
    }

    @Override
    public String getDescription() {
        return "Fire a razor-sharp blade of wind that slices through enemies in a line, dealing damage and knocking them back.";
    }
}