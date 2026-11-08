package net.rose.elementSMPRefined.ability.main.mythic.death;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.Element;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

/**
 * Death ability 2 - Soul Siphon.
 * <p>
 * Drains the player the caster is looking at: their element abilities are disabled for two
 * minutes, and for the same two minutes the caster casts that player's abilities in place of
 * their own.
 */
public class DeathSoulSiphonAbility extends BaseAbility {
    private static final double RANGE = 20.0;
    private static final double RAY_SIZE = 0.6;
    private static final int DURATION_TICKS = 20 * 60 * 2;

    private final ElementSMPRefined plugin;

    public DeathSoulSiphonAbility(JavaPlugin plugin, ConfigManager configManager) {
        super(
                "death_soul_siphon",
                ElementType.DEATH,
                2,
                Constants.Cooldown.ABILITY_2_SECONDS,
                2,
                configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player caster = context.getPlayer();

        Player target = findLookedAtPlayer(context);
        if (target == null) {
            caster.sendMessage(Lang.DEATH_SOUL_SIPHON_NO_TARGET_IN_RANGE);
            return false;
        }

        if (plugin.getDisarmManager().isAbilityDisarmed(target)) {
            caster.sendMessage(Lang.DEATH_SOUL_SIPHON_TARGET_ALREADY_DRAINED);
            return false;
        }

        ElementType targetType = plugin.getElementManager().getPlayerElement(target);
        Element targetElement =
                targetType != null ? plugin.getElementManager().get(targetType) : null;
        if (targetElement == null) {
            caster.sendMessage(Lang.DEATH_SOUL_SIPHON_TARGET_HAS_NO_ELEMENT);
            return false;
        }

        plugin.getDisarmManager().applyAbilityDisarm(target, DURATION_TICKS);
        plugin.getStolenAbilityManager().steal(caster, targetType, DURATION_TICKS);

        caster.sendMessage(
                Lang.deathSoulSiphonCaster(
                        target.getName(),
                        targetElement.getAbility1Name()
                                + ChatColor.GRAY
                                + " & "
                                + targetElement.getAbility2Name()));
        target.sendMessage(Lang.deathSoulSiphonVictim(caster.getName()));

        playEffects(caster, target);
        return true;
    }

    private Player findLookedAtPlayer(ElementContext context) {
        Player caster = context.getPlayer();
        Location eye = caster.getEyeLocation();

        RayTraceResult hit =
                caster.getWorld()
                        .rayTraceEntities(
                                eye,
                                eye.getDirection(),
                                RANGE,
                                RAY_SIZE,
                                entity ->
                                        entity instanceof Player other
                                                && !other.equals(caster)
                                                && other.getGameMode() != GameMode.SPECTATOR
                                                && isValidTarget(context, other));
        if (hit == null || !(hit.getHitEntity() instanceof Player target)) {
            return null;
        }
        return target;
    }

    private void playEffects(Player caster, Player target) {
        World world = target.getWorld();
        Location from = target.getLocation().add(0, 1, 0);
        Location to = caster.getLocation().add(0, 1, 0);

        world.spawnParticle(Particle.SOUL, from, 40, 0.4, 0.6, 0.4, 0.05, null, true);
        world.playSound(from, Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.8f);
        world.playSound(from, Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.6f);

        int steps = 24;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Location point =
                    from.clone()
                            .add(
                                    (to.getX() - from.getX()) * t,
                                    (to.getY() - from.getY()) * t + Math.sin(t * Math.PI) * 0.8,
                                    (to.getZ() - from.getZ()) * t);
            world.spawnParticle(Particle.SOUL, point, 2, 0.05, 0.05, 0.05, 0.01, null, true);
        }

        world.spawnParticle(Particle.SOUL, to, 30, 0.4, 0.6, 0.4, 0.05, null, true);
        world.playSound(to, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 0.7f);
    }

    @Override
    public String getName() {
        return ChatColor.DARK_PURPLE + "Soul Siphon";
    }

    @Override
    public String getDescription() {
        return "Drain the player you're looking at: their abilities are disabled for 2 minutes,"
                + " and you wield them instead of your own.";
    }
}
