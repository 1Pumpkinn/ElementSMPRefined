package hs.elementSMPRefined.ability.main.death;

import hs.elementSMPRefined.API.ability.BaseAbility;
import hs.elementSMPRefined.API.element.ElementContext;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Death ability1: Side Step.
 * <p>
 * Pure movement ability - a short lateral dash that slips the player out of
 * the way. No combat effect; see {@link DeathAbilityDisarmAbility} for the
 * disarm (that's ability2).
 */
public class DeathSideStepAbility extends BaseAbility {

    public DeathSideStepAbility(JavaPlugin plugin) {
        super("death_side_step", 30, 8, 1);
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        // Perpendicular to look direction, so it reads as "stepping aside"
        // rather than a forward dash.
        Vector look = player.getLocation().getDirection().normalize();
        Vector side = new Vector(-look.getZ(), 0, look.getX()).normalize();

        Vector dashVelocity = side.multiply(1.4);
        dashVelocity.setY(Math.max(player.getVelocity().getY(), 0.25));
        player.setVelocity(dashVelocity);

        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.SMOKE, loc, 25, 0.4, 0.5, 0.4, 0.05, null, true);
        player.getWorld().playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 1f, 1.6f);

        return true;
    }

    @Override
    public String getName() {
        return ChatColor.DARK_PURPLE + "Side Step";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Dash aside to evade an attack. (25 mana)";
    }
}