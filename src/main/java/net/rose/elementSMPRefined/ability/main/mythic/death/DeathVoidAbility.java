package net.rose.elementSMPRefined.ability.main.mythic.death;

import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TeamManager;
import net.rose.elementSMPRefined.util.damage.AbilityTargets;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.movement.AbilityLaunch;
import net.rose.elementSMPRefined.util.visual.model.DeathsVoidVisual;

import org.bukkit.Bukkit;
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
 * <p>
 * The pulse also boosts the caster forward if it bursts right beneath them: aim the blades straight down at
 * your feet and the explosion throws you the way you are facing.
 */
public class DeathVoidAbility extends BaseAbility {

    private static final int CHARGE_TICKS = 10;
    private static final double CHARGE_RADIUS = 1.3;
    private static final double CHARGE_DISTANCE = 3.5;
    private static final double MIN_PARTICLE_DISTANCE = 2.5;

    private static final double SPEED = 1.5; // blocks per tick
    private static final double MAX_DISTANCE = 32;
    private static final double TRAIL_START_DISTANCE = 4.0;
    private static final double HIT_RADIUS = 2.4;
    private static final float BLADE_SCALE = 4.0f;

    private static final double BLADE_DAMAGE = 6.0; // 3 hearts, once per target
    private static final double BLADE_KNOCKBACK = 0.6;

    private static final double PULSE_RADIUS = 4.0;
    private static final double PULSE_DAMAGE = 4.0; // 2 hearts
    private static final double PULSE_KNOCKBACK = 1.1;

    private static final double SELF_BOOST = 2.5; // forward speed at the centre of your own pulse
    private static final double SELF_BOOST_LIFT = 0.5; // small hop so the boost doesn't scrape the ground
    private static final double SELF_BOOST_MIN_UP = 0.7; // the pulse must be mostly beneath you

    private static final int DEBUFF_TICKS = 80;

    private static final Particle.DustOptions PURPLE_DUST =
            new Particle.DustOptions(Color.fromRGB(150, 30, 210), 1.6F);
    private static final Particle.DustOptions DARK_DUST =
            new Particle.DustOptions(Color.fromRGB(35, 0, 55), 1.9F);

    private final JavaPlugin plugin;

    public DeathVoidAbility(JavaPlugin plugin, ConfigManager configManager) {
        super(
                "death_dark_blade",
                ElementType.DEATH,
                1,
                Constants.Cooldown.ABILITY_1_SECONDS,
                1,
                configManager);
        this.plugin = plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        TeamManager teams = context.getTeamManager();

        playCastSound(player.getWorld(), player.getLocation());

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

    /** Dark energy gathers a few blocks out in front of the caster, never near their face. */
    private void charge(Player player, int tick) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Location center =
                eye.clone().add(eye.getDirection().multiply(CHARGE_DISTANCE)).subtract(0, 0.3, 0);
        double progress = (double) tick / CHARGE_TICKS;
        double radius = CHARGE_RADIUS * (1.0 - progress) + 0.2;
        double angle = tick * 0.9;
        for (int arm = 0; arm < 2; arm++) {
            double a = angle + arm * Math.PI;
            Location p =
                    center.clone()
                            .add(
                                    Math.cos(a) * radius,
                                    Math.sin(a * 1.3) * radius * 0.5,
                                    Math.sin(a) * radius);
            if (p.distanceSquared(eye) < MIN_PARTICLE_DISTANCE * MIN_PARTICLE_DISTANCE) continue;
            world.spawnParticle(
                    Particle.DUST, p, 1, 0, 0, 0, 0, arm == 0 ? DARK_DUST : PURPLE_DUST, true);
        }
        if (tick % 3 == 0) {
            playChargeNote(world, center, tick / 3);
        }
    }

    private void launch(Player player, TeamManager teams) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location start = eye.clone().add(dir.clone().multiply(2.5)).subtract(0, 0.3, 0);

        playLaunchSound(world, start);

        DeathsVoidVisual visual = new DeathsVoidVisual(start, dir, BLADE_SCALE, player);
        Set<UUID> alreadyHit = new HashSet<>();
        Location pos = start.clone();

        new BukkitRunnable() {
            private double travelled = 0;
            private int flightTicks = 0;

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
                if (travelled > TRAIL_START_DISTANCE) trail(world, pos);
                if (flightTicks++ % 5 == 0) {
                    playFlightHum(world, pos, flightTicks / 5);
                }
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
        world.spawnParticle(Particle.DUST, pos, 1, 0.6, 0.6, 0.6, 0, PURPLE_DUST, true);
        world.spawnParticle(Particle.DUST, pos, 1, 0.8, 0.8, 0.8, 0, DARK_DUST, true);
    }

    private void sweep(
            Player player, TeamManager teams, Location pos, Vector dir, Set<UUID> alreadyHit) {
        for (LivingEntity target : pos.getNearbyLivingEntities(HIT_RADIUS)) {
            if (!isValidTarget(player, teams, target)) continue;
            if (!alreadyHit.add(target.getUniqueId())) continue;

            TrueDamage.of(BLADE_DAMAGE).attacker(player).ability(getName()).apply(target);
            debuff(target);

            Vector push = dir.clone().setY(0);
            if (push.lengthSquared() > 1.0E-6) {
                push.normalize().multiply(BLADE_KNOCKBACK).setY(0.15);
                target.setVelocity(target.getVelocity().add(push));
            }

            Location hitLoc = target.getLocation().add(0, target.getHeight() * 0.6, 0);
            target.getWorld()
                    .spawnParticle(Particle.CRIT, hitLoc, 14, 0.3, 0.4, 0.3, 0.2, null, true);
            target.getWorld()
                    .spawnParticle(Particle.DUST, hitLoc, 14, 0.3, 0.4, 0.3, 0, PURPLE_DUST, true);
            target.getWorld().playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.9f, 0.6f);
        }
    }

    /** Dark Pulse: a purple burst where the blades end. */
    private void pulse(Player player, TeamManager teams, Location at) {
        World world = at.getWorld();
        playPulseSound(world, at);
        world.spawnParticle(
                Particle.DUST,
                at,
                80,
                PULSE_RADIUS * 0.5,
                PULSE_RADIUS * 0.4,
                PULSE_RADIUS * 0.5,
                0,
                PURPLE_DUST,
                true);
        world.spawnParticle(
                Particle.DUST,
                at,
                60,
                PULSE_RADIUS * 0.5,
                PULSE_RADIUS * 0.4,
                PULSE_RADIUS * 0.5,
                0,
                DARK_DUST,
                true);

        // Expanding ring
        for (int i = 0; i < 40; i++) {
            double a = i * (Math.PI * 2 / 40);
            Location ring =
                    at.clone().add(Math.cos(a) * PULSE_RADIUS, 0.2, Math.sin(a) * PULSE_RADIUS);
            world.spawnParticle(Particle.DUST, ring, 2, 0.1, 0.1, 0.1, 0, PURPLE_DUST, true);
        }

        boostCaster(player, at);

        for (LivingEntity target : at.getNearbyLivingEntities(PULSE_RADIUS)) {
            if (!isValidTarget(player, teams, target)) continue;

            TrueDamage.of(PULSE_DAMAGE).attacker(player).ability(getName()).apply(target);
            debuff(target);

            Vector away = target.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (away.lengthSquared() > 1.0E-6) {
                away.normalize().multiply(PULSE_KNOCKBACK).setY(0.35);
                target.setVelocity(target.getVelocity().add(away));
            }
        }
    }

    /**
     * Blast boost: the Dark Pulse throws the caster forward, the way they are facing, if it goes off right
     * beneath them, which means aiming the blades straight down at your feet. A pulse beside or above you does
     * nothing. The closer the pulse, the harder the push.
     */
    private void boostCaster(Player player, Location at) {
        if (!player.getWorld().equals(at.getWorld())) return;

        Location body = player.getLocation().add(0, player.getHeight() * 0.5, 0);
        double distance = body.distance(at);
        if (distance > PULSE_RADIUS) return;

        Vector away = body.toVector().subtract(at.toVector());
        if (away.lengthSquared() < 1.0E-6) {
            away = new Vector(0, 1, 0);
        }
        if (away.normalize().getY() < SELF_BOOST_MIN_UP) return;

        double yaw = Math.toRadians(player.getLocation().getYaw());
        Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));

        double falloff = 1.0 - 0.5 * (distance / PULSE_RADIUS);
        Vector boost = forward.multiply(SELF_BOOST * falloff);
        boost.setY(SELF_BOOST_LIFT);

        AbilityLaunch.launch(player, boost);

        World world = at.getWorld();
        world.spawnParticle(
                Particle.DUST, player.getLocation(), 25, 0.4, 0.2, 0.4, 0, PURPLE_DUST, true);
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.3f, 1.6f);
    }

    // Sound layout: the cast and charge-up are an evoker's spell winding up with a rising vex
    // whine, the launch
    // plays bells and a ghast shot, the flight is silent, and the pulse is an amethyst crack
    // followed by a
    // heartbeat that gives out.

    /** Falling, uneasy notes (pitch multipliers) used by the launch sound. */
    private static final float[] VOID_NOTES = {1.89f, 1.5f, 1.41f, 1.19f, 1.0f, 0.71f, 0.59f};

    /** The wind-up: an evoker's spell preparing, pitched low. */
    private void playCastSound(World w, Location at) {
        w.playSound(at, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 0.5f, 0.6f);
    }

    /** A rising vex whine, one per call (every 3 ticks), so the charge-up climbs until the blades fire. */
    private void playChargeNote(World w, Location at, int index) {
        float pitch = Math.min(1.4f, 0.6f + 0.2f * index);
        w.playSound(at, Sound.ENTITY_VEX_CHARGE, 0.5f, pitch);
    }

    private void playLaunchSound(World w, Location at) {
        w.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.45f, 0.6f);
        w.playSound(at, Sound.ENTITY_GHAST_SHOOT, 0.25f, 0.5f);
        w.playSound(at, Sound.BLOCK_BELL_USE, 0.4f, 0.5f);
        w.playSound(at, Sound.ITEM_TRIDENT_THROW, 0.5f, 0.5f);
        // The fall: bells sinking away from the caster as the blades leave.
        for (int i = 0; i < 5; i++) {
            final float pitch = VOID_NOTES[i];
            final float vol = 0.3f - 0.04f * i;
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                w.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, vol, pitch);
                                w.playSound(
                                        at,
                                        Sound.BLOCK_AMETHYST_CLUSTER_BREAK,
                                        vol * 0.5f,
                                        pitch * 0.5f);
                            },
                            2L * (i + 1));
        }
    }

    /** Currently silent. Called every 5 ticks while the blades fly. */
    private void playFlightHum(World w, Location at, int index) {
        float wobble = 0.5f + 0.08f * (index % 3);
    }

    private void playPulseSound(World w, Location at) {
        w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 0.6f);
        w.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 0.5f);
        // The heart gives out: three beats, slower and quieter each time.
        for (int i = 1; i <= 3; i++) {
            final float vol = 1.0f - 0.25f * i;
            final float pitch = 0.5f - 0.05f * i;
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> w.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, vol, pitch),
                            6L * i);
        }
    }

    private boolean isValidTarget(Player player, TeamManager teams, LivingEntity target) {
        if (target.equals(player) || AbilityTargets.isProtected(target)) return false;
        return !(target instanceof Player other
                && teams.isFriendly(player.getUniqueId(), other.getUniqueId()));
    }

    private void debuff(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, DEBUFF_TICKS, 1));
    }

    @Override
    public String getName() {
        return ChatColor.DARK_PURPLE + "Death's Void";
    }

    @Override
    public String getDescription() {
        return "Shoot corrupted blades that damages everything in its path and bursts into"
                   + " corrupted energy. Aim straight down at your feet and the explosion boosts you forward.";
    }
}
