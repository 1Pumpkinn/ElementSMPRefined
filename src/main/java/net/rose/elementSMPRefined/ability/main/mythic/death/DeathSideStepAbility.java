package net.rose.elementSMPRefined.ability.main.mythic.death;

import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class DeathSideStepAbility extends BaseAbility {

    private static final double BLINK_DISTANCE = 12.0;
    private static final double SWEEP_STEP = 0.25;
    private static final Particle.DustOptions BLACK_DUST =
            new Particle.DustOptions(Color.fromRGB(5, 5, 5), 1.4F);

    public DeathSideStepAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("death_side_step", ElementType.DEATH, 1, Constants.Cooldown.ABILITY_1_SECONDS, 1, configManager);
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        World world = player.getWorld();
        Location origin = player.getLocation();

        // Horizontal-only facing direction, derived from yaw alone so looking
        // straight up/down (where getDirection()'s x/z collapse to ~0) can't
        // produce a zero vector.
        double yawRad = Math.toRadians(origin.getYaw());
        Vector dir = new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad));

        double safeDistance = sweepForSafeDistance(origin, dir);

        Location destination = origin.clone().add(dir.clone().multiply(safeDistance));
        destination.setY(origin.getY());
        destination.setYaw(origin.getYaw());
        destination.setPitch(origin.getPitch());

        // Vanish effect + trail sampled along the path, so the "teleport" reads
        // instantly rather than as movement over time.
        spawnBurst(world, origin.clone().add(0, 1, 0));
        for (double t = 0.15; t < 1.0; t += 0.15) {
            Location point = origin.clone().add(dir.clone().multiply(safeDistance * t)).add(0, 1, 0);
            world.spawnParticle(Particle.DUST, point, 3, 0.1, 0.2, 0.1, 0, BLACK_DUST, true);
        }
        world.playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.6f);

        player.teleport(destination);
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0f);

        // Reappear effect at the new spot.
        spawnBurst(world, destination.clone().add(0, 1, 0));
        world.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.9f);

        return true;
    }

    private double sweepForSafeDistance(Location origin, Vector dir) {
        double safeDistance = 0;
        for (double d = SWEEP_STEP; d <= BLINK_DISTANCE; d += SWEEP_STEP) {
            Location check = origin.clone().add(dir.clone().multiply(d));
            check.setY(origin.getY());
            if (isBlocked(check)) {
                break;
            }
            safeDistance = d;
        }
        return safeDistance;
    }

    private boolean isBlocked(Location loc) {
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        return feet.getType().isSolid() || head.getType().isSolid();
    }

    private void spawnBurst(World world, Location center) {
        world.spawnParticle(Particle.DUST, center, 40, 0.35, 0.5, 0.35, 0, BLACK_DUST, true);
        world.spawnParticle(Particle.SQUID_INK, center, 6, 0.3, 0.4, 0.3, 0.01, null, true);
    }

    @Override
    public String getName() {
        return ChatColor.DARK_PURPLE + "Side Step";
    }

    @Override
    public String getDescription() {
        return "Teleport forward a short distance.";
    }
}