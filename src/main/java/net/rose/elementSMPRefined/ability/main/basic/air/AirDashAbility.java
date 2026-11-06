package net.rose.elementSMPRefined.ability.main.basic.air;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.util.bukkit.WebUtil;

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
        super(
                "air_dash",
                ElementType.AIR,
                1,
                Constants.Cooldown.ABILITY_1_SECONDS,
                1,
                configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        boolean inWater = player.getLocation().getBlock().getType() == Material.WATER;
        double dashPower = inWater ? 0.8 : 2.5;
        int durationTicks = inWater ? 6 : 20;

        Vector direction = player.getLocation().getDirection();
        direction.setY(Math.max(direction.getY(), 0.5));
        final Vector dashVelocity = direction.multiply(dashPower);
        // In a web the dash is driven by teleport steps (see the runnable) - setting velocity
        // there only makes the server rubber-band the player.
        if (!WebUtil.isInWeb(player)) {
            player.setVelocity(dashVelocity);
        }

        new BukkitRunnable() {
            int ticks = 0;
            // The velocity the dash "should" currently have, decayed like vanilla air movement.
            // Webs wipe the player's real momentum every tick, so while inside one the player is
            // moved by this much per tick directly instead.
            final Vector intended = dashVelocity.clone();
            boolean wasInWeb = WebUtil.isInWeb(player);

            @Override
            public void run() {
                if (ticks >= durationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }

                boolean inWeb = WebUtil.isInWeb(player);
                if (inWeb) {
                    WebUtil.moveThrough(player, intended);
                    player.setVelocity(new Vector(0, 0, 0));
                } else if (wasInWeb) {
                    // Just broke out of the web: the web wiped the real momentum, so hand the
                    // dash back its full velocity instead of letting it fizzle at the edge.
                    player.setVelocity(intended.clone());
                }
                wasInWeb = inWeb;

                intended.setX(intended.getX() * 0.91);
                intended.setZ(intended.getZ() * 0.91);
                intended.setY((intended.getY() - 0.08) * 0.98);

                Location loc = player.getLocation();
                player.getWorld()
                        .spawnParticle(Particle.CLOUD, loc, 5, 0.3, 0.3, 0.3, 0.05, null, true);

                if (ticks % 5 == 0) {
                    for (LivingEntity entity :
                            loc.getNearbyLivingEntities(Constants.Distance.AIR_DASH_RADIUS)) {
                        if (entity.equals(player)) continue;
                        if (!AirDashAbility.this.isValidTarget(context, entity)) continue;

                        Vector offset = entity.getLocation().toVector().subtract(loc.toVector());
                        if (offset.lengthSquared() < 1.0E-4) {
                            offset = new Vector(0, 0, 1); // zero vector normalizes to NaN
                        }
                        Vector knockback = offset.normalize();
                        knockback.setY(0.2);
                        entity.setVelocity(knockback.multiply(1.0));
                        entity.getWorld()
                                .spawnParticle(
                                        Particle.CLOUD,
                                        entity.getLocation(),
                                        10,
                                        0.3,
                                        0.3,
                                        0.3,
                                        0.05,
                                        null,
                                        true);
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
        return "Dash forward and push away enemies near you.";
    }
}
