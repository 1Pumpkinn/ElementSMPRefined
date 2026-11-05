package net.rose.elementSMPRefined.ability.main.mythic.death;

import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TeamManager;
import net.rose.elementSMPRefined.util.damage.AbilityDamage;
import net.rose.elementSMPRefined.util.visual.model.DarkBladeVisual;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Death ability 1 - Dark Blade.
 * <p>
 * Darkrai-style: the caster gathers dark energy (purple vortex, short charge), then hurls a spinning fan of
 * purple blades straight ahead. The blades pierce everything in their path (each target is hit once), and
 * burst in a Dark Pulse where they stop - on a wall or at the end of their range.
 */
public class DeathVoidAbility extends BaseAbility {

    private static final int CHARGE_TICKS = 10;
    private static final double CHARGE_RADIUS = 2.2;

    private static final double SPEED = 1.5;            // blocks per tick
    private static final double MAX_DISTANCE = 24.0;
    private static final double HIT_RADIUS = 2.4;
    private static final float BLADE_SCALE = 4.0f;

    private static final double BLADE_DAMAGE = 6.0;     // 3 hearts, once per target
    private static final double BLADE_KNOCKBACK = 0.6;

    private static final double PULSE_RADIUS = 4.0;
    private static final double PULSE_DAMAGE = 4.0;     // 2 hearts
    private static final double PULSE_KNOCKBACK = 1.1;

    private static final int DEBUFF_TICKS = 80;         // Slowness + Darkness on anything the blades or pulse hit

    private static final Particle.DustOptions PURPLE_DUST =
            new Particle.DustOptions(Color.fromRGB(150, 30, 210), 1.6F);
    private static final Particle.DustOptions DARK_DUST =
            new Particle.DustOptions(Color.fromRGB(35, 0, 55), 1.9F);

    private final JavaPlugin plugin;

    public DeathVoidAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("death_dark_blade", ElementType.DEATH, 1, Constants.Cooldown.ABILITY_1_SECONDS, 1, configManager);
        this.plugin = plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        TeamManager teams = context.getTeamManager();

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.8f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.5f);

        new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                if (tick < CHARGE_TICKS) {
                    charge(player, tick);
                    tick++;
                    return;
                }
                cancel();
                launch(player, teams);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    /** Dark energy spirals in toward the caster's hands. */
    private void charge(Player player, int tick) {
        World world = player.getWorld();
        Location center = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.9))
                .subtract(0, 0.4, 0);
        double progress = (double) tick / CHARGE_TICKS;
        double radius = CHARGE_RADIUS * (1.0 - progress) + 0.2;
        double angle = tick * 0.9;
        for (int arm = 0; arm < 3; arm++) {
            double a = angle + arm * (Math.PI * 2 / 3);
            Location p = center.clone().add(Math.cos(a) * radius, Math.sin(a * 1.3) * radius * 0.5, Math.sin(a) * radius);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, arm == 0 ? DARK_DUST : PURPLE_DUST, true);
        }
        world.spawnParticle(Particle.PORTAL, center, 6, 0.4, 0.4, 0.4, 0.4, null, true);
        if (tick % 3 == 0) {
            world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f, 0.5f + (float) progress);
        }
    }

    private void launch(Player player, TeamManager teams) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location start = eye.clone().add(dir.clone().multiply(2.5)).subtract(0, 0.3, 0);

        world.playSound(start, Sound.ENTITY_WITHER_SHOOT, 1f, 0.6f);
        world.playSound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);

        DarkBladeVisual visual = new DarkBladeVisual(start, dir, BLADE_SCALE);
        Set<UUID> alreadyHit = new HashSet<>();
        Location pos = start.clone();

        new BukkitRunnable() {
            private double travelled = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !visual.isValid()) {
                    visual.remove();
                    cancel();
                    return;
                }

                Location next = pos.clone().add(dir.clone().multiply(SPEED));
                boolean blocked = !next.getBlock().isPassable();
                if (!blocked) {
                    pos.setX(next.getX());
                    pos.setY(next.getY());
                    pos.setZ(next.getZ());
                    travelled += SPEED;
                }

                visual.tick(pos);
                trail(world, pos);
                sweep(player, teams, pos, dir, alreadyHit);

                if (blocked || travelled >= MAX_DISTANCE) {
                    visual.remove();
                    pulse(player, teams, pos);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void trail(World world, Location pos) {
        world.spawnParticle(Particle.DUST, pos, 6, 1.0, 1.0, 1.0, 0, PURPLE_DUST, true);
        world.spawnParticle(Particle.DUST, pos, 4, 1.3, 1.3, 1.3, 0, DARK_DUST, true);
        world.spawnParticle(Particle.SQUID_INK, pos, 1, 0.3, 0.3, 0.3, 0.01, null, true);
    }

    private void sweep(Player player, TeamManager teams, Location pos, Vector dir, Set<UUID> alreadyHit) {
        for (LivingEntity target : pos.getNearbyLivingEntities(HIT_RADIUS)) {
            if (!isValidTarget(player, teams, target)) continue;
            if (!alreadyHit.add(target.getUniqueId())) continue;

            target.setNoDamageTicks(0);
            AbilityDamage.deal(target, getName(), () -> target.damage(BLADE_DAMAGE, player));
            debuff(target);

            Vector push = dir.clone().setY(0);
            if (push.lengthSquared() > 1.0E-6) {
                push.normalize().multiply(BLADE_KNOCKBACK).setY(0.15);
                target.setVelocity(target.getVelocity().add(push));
            }

            Location hitLoc = target.getLocation().add(0, target.getHeight() * 0.6, 0);
            target.getWorld().spawnParticle(Particle.CRIT, hitLoc, 14, 0.3, 0.4, 0.3, 0.2, null, true);
            target.getWorld().spawnParticle(Particle.DUST, hitLoc, 14, 0.3, 0.4, 0.3, 0, PURPLE_DUST, true);
            target.getWorld().playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.9f, 0.6f);
        }
    }

    /** Dark Pulse: a purple burst where the blades end. */
    private void pulse(Player player, TeamManager teams, Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.8f, 0.7f);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.6f);
        world.spawnParticle(Particle.DUST, at, 80, PULSE_RADIUS * 0.5, PULSE_RADIUS * 0.4, PULSE_RADIUS * 0.5, 0, PURPLE_DUST, true);
        world.spawnParticle(Particle.DUST, at, 60, PULSE_RADIUS * 0.5, PULSE_RADIUS * 0.4, PULSE_RADIUS * 0.5, 0, DARK_DUST, true);
        world.spawnParticle(Particle.EXPLOSION, at, 1, 0, 0, 0, 0, null, true);

        // Expanding ring
        for (int i = 0; i < 40; i++) {
            double a = i * (Math.PI * 2 / 40);
            Location ring = at.clone().add(Math.cos(a) * PULSE_RADIUS, 0.2, Math.sin(a) * PULSE_RADIUS);
            world.spawnParticle(Particle.DUST, ring, 2, 0.1, 0.1, 0.1, 0, PURPLE_DUST, true);
        }

        for (LivingEntity target : at.getNearbyLivingEntities(PULSE_RADIUS)) {
            if (!isValidTarget(player, teams, target)) continue;

            target.setNoDamageTicks(0);
            AbilityDamage.deal(target, getName(), () -> target.damage(PULSE_DAMAGE, player));
            debuff(target);

            Vector away = target.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (away.lengthSquared() > 1.0E-6) {
                away.normalize().multiply(PULSE_KNOCKBACK).setY(0.35);
                target.setVelocity(target.getVelocity().add(away));
            }
        }
    }

    private boolean isValidTarget(Player player, TeamManager teams, LivingEntity target) {
        if (target.equals(player)) return false;
        return !(target instanceof Player other && teams.isFriendly(player.getUniqueId(), other.getUniqueId()));
    }

    private void debuff(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, DEBUFF_TICKS, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, DEBUFF_TICKS, 0));
    }

    @Override
    public String getName() {
        return ChatColor.DARK_PURPLE + "Death's Void";
    }

    @Override
    public String getDescription() {
        return "Shoot corrupted blades that damages everything in its path and bursts into corrupted energy.";
    }
}
