package net.rose.elementSMPRefined.ability.main.air;

import net.rose.elementSMPRefined.config.Constants;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class AirDashAbility extends BaseAbility {
    private final ElementSMPRefined plugin;

    public AirDashAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("air_dash", ElementType.AIR, 1, 30, 1, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        // Air has no grip in water to dash off of - let the player nudge
        // forward a little instead of launching at full power, rather than
        // rocketing across/out of the water the way a dash on land would.
        boolean inWater = player.getLocation().getBlock().getType() == Material.WATER;
        double dashPower = inWater ? 0.8 : 2.5;
        int durationTicks = inWater ? 6 : 20;

        Vector direction = player.getLocation().getDirection();
        direction.setY(Math.max(direction.getY(), 0.5));
        player.setVelocity(direction.multiply(dashPower));

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= durationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }

                Location loc = player.getLocation();
                player.getWorld().spawnParticle(Particle.CLOUD, loc, 5, 0.3, 0.3, 0.3, 0.05, null, true);

                if (ticks % 5 == 0) {
                    for (LivingEntity entity : loc.getNearbyLivingEntities(Constants.Distance.AIR_DASH_RADIUS)) {
                        if (entity.equals(player)) continue;
                        if (!AirDashAbility.this.isValidTarget(context, entity)) continue;

                        Vector knockback = entity.getLocation().toVector().subtract(loc.toVector()).normalize();
                        knockback.setY(0.2);
                        entity.setVelocity(knockback.multiply(1.0));
                        entity.getWorld().spawnParticle(Particle.CLOUD, entity.getLocation(), 10, 0.3, 0.3, 0.3, 0.05, null, true);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.5f);
        return true;
    }

    @Override
    public String getName() {
        return ChatColor.WHITE + "Air Dash";
    }

    @Override
    public String getDescription() {
        return "Dash forward with incredible speed, pushing away any enemies in your path.";
    }
}