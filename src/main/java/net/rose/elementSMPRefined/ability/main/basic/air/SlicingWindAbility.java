package net.rose.elementSMPRefined.ability.main.basic.air;

import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.visual.model.AirCutterVisual;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Slicing Wind - fires a fast-moving blade of compressed air in front of the
 * player that cuts through anything in a narrow line, dealing damage and
 * knocking targets away.
 * <p>
 * The blade is drawn with the {@code elementsmp:air_cutter} resource-pack model via
 * {@link AirCutterVisual} (an ItemDisplay teleported each tick), the same way Hell's Chain
 * draws its chain with {@code ChainVisual}.
 */
public class SlicingWindAbility extends BaseAbility {
    private final ElementSMPRefined plugin;

    public SlicingWindAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("slicing_wind", ElementType.AIR, 2, 60, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        TrustManager trust = context.getTrustManager();

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
            final AirCutterVisual blade =
                    new AirCutterVisual(origin.clone().add(direction.clone().multiply(1.0)), direction);

            /** Every exit path calls cancel(), so the blade entity can never outlive the ability. */
            @Override
            public synchronized void cancel() throws IllegalStateException {
                blade.remove();
                super.cancel();
            }

            @Override
            public void run() {
                if (!player.isOnline() || travelled >= range) {
                    cancel();
                    return;
                }

                travelled += 1.5;
                Location slice = origin.clone().add(direction.clone().multiply(travelled));

                blade.moveTo(slice);
                // Faint wind trail behind the blade
                w.spawnParticle(Particle.CLOUD, slice, 2, 0.5, 0.05, 0.5, 0.0, null, true);

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

    @Override
    public String getName() {
        return ChatColor.WHITE + "Slicing Wind";
    }

    @Override
    public String getDescription() {
        return "Fire a razor-sharp blade of wind that slices through enemies in a line, dealing damage and knocking them back.";
    }
}
