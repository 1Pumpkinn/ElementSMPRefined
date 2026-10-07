package net.rose.elementSMPRefined.ability.main.upgraded.lava;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.visual.model.MagmaBeamVisual;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Magma Beam - hold still while a glowing orb of magma charges up in front of you, then
 * unleash a thick beam that follows your aim and burns everything it passes through.
 * <p>
 * The caster is slowed for the whole ability so it can't be used as a free running attack.
 * The beam stops at the first solid block and never changes real blocks. It is drawn with the
 * {@code elementsmp:magma_beam} resource-pack model via {@link MagmaBeamVisual} (a stretched
 * ItemDisplay) with particles on top, so it still reads if the pack isn't applied.
 */
public class MagmaBeamAbility extends BaseAbility {

    private static final int CHARGE_TICKS = 30;
    private static final int BEAM_TICKS = 30;
    private static final double RANGE = 25.0;
    /** Ticks between damage pulses while the beam is firing. */
    private static final int HIT_INTERVAL = 5;
    private static final double DAMAGE_PER_PULSE = 2.0;
    private static final double HIT_RADIUS = 1.0;
    private static final int BURN_TICKS = 60;
    private static final int SLOW_AMPLIFIER = 2;
    /** Roll added to the beam every tick, in radians (~26 degrees). Keep well under PI so interpolation spins the right way. */
    private static final float SPIN_PER_TICK = 0.45f;
    /** Radius of the particle spiral wrapped around the firing beam. */
    private static final double HELIX_RADIUS = 0.6;

    private static final Component ALREADY_CHARGING =
            Component.text("Magma Beam is already charging!", NamedTextColor.RED);

    private final ElementSMPRefined plugin;

    public MagmaBeamAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("lava_magma_beam", ElementType.LAVA, 2, 45, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        TrustManager trust = context.getTrustManager();

        if (isActiveFor(player)) {
            player.sendMessage(ALREADY_CHARGING);
            return false;
        }

        setActive(player, true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                CHARGE_TICKS + BEAM_TICKS + 5, SLOW_AMPLIFIER, false, false, false));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.2f);

        new BukkitRunnable() {
            final MagmaBeamVisual visual = new MagmaBeamVisual();
            int tick = 0;
            float roll = 0f;

            /** Every exit path calls cancel(), so the beam entity and slowness can never outlive the ability. */
            @Override
            public synchronized void cancel() throws IllegalStateException {
                visual.remove();
                setActive(player, false);
                PotionEffect slow = player.getPotionEffect(PotionEffectType.SLOWNESS);
                if (slow != null && slow.getAmplifier() == SLOW_AMPLIFIER) {
                    player.removePotionEffect(PotionEffectType.SLOWNESS);
                }
                super.cancel();
            }

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }

                Location eye = player.getEyeLocation();
                Vector dir = eye.getDirection().normalize();
                // Starts a little ahead of and below the eyes so it doesn't blot out your view.
                Location start = eye.clone().add(0, -0.3, 0).add(dir.clone().multiply(1.0));

                if (tick < CHARGE_TICKS) {
                    charge(player, visual, start, dir, tick, roll);
                } else {
                    if (tick == CHARGE_TICKS) {
                        World w = player.getWorld();
                        w.playSound(start, Sound.ENTITY_BLAZE_SHOOT, 1.6f, 0.6f);
                        w.playSound(start, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
                    }
                    fire(player, trust, visual, eye, start, dir, tick - CHARGE_TICKS, roll);
                    if (tick >= CHARGE_TICKS + BEAM_TICKS) {
                        cancel();
                        return;
                    }
                }
                tick++;
                roll += SPIN_PER_TICK;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    /** Orb swells in front of the caster while sparks are pulled in towards it. */
    private void charge(Player player, MagmaBeamVisual visual, Location start, Vector dir, int tick, float roll) {
        World w = player.getWorld();
        double progress = (double) tick / CHARGE_TICKS;
        Location orb = start.clone().add(dir.clone().multiply(0.4));

        visual.update(orb.clone().subtract(dir.clone().multiply(0.2)), dir, 0.4 + 0.8 * progress, (float) (0.4 + 1.0 * progress), roll);

        double radius = 2.6 * (1.0 - progress) + 0.3;
        for (int i = 0; i < 3; i++) {
            Vector offset = Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize().multiply(radius);
            w.spawnParticle(Particle.FLAME, orb.clone().add(offset), 1, 0, 0, 0, 0.0, null, true);
        }
        w.spawnParticle(Particle.LAVA, orb, 1, 0.2, 0.2, 0.2, 0.0, null, true);

        if (tick % 5 == 0) {
            w.playSound(orb, Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.6f + (float) progress);
        }
    }

    /** One tick of the firing beam: re-aim, draw, and (every few ticks) burn what it crosses. */
    private void fire(Player player, TrustManager trust, MagmaBeamVisual visual,
                      Location eye, Location start, Vector dir, int beamTick, float roll) {
        World w = player.getWorld();

        // Stop at the first solid block.
        RayTraceResult blockHit = w.rayTraceBlocks(eye, dir, RANGE, FluidCollisionMode.NEVER, true);
        double length = blockHit == null ? RANGE : blockHit.getHitPosition().distance(eye.toVector());
        Vector end = eye.toVector().add(dir.clone().multiply(length));

        // Draw from the (offset) start to the true end point so the tip lands exactly where the ray stopped.
        Vector delta = end.clone().subtract(start.toVector());
        double visualLength = delta.length();
        if (visualLength > 0.5) {
            float pulse = (float) (1.0 + 0.12 * Math.sin(beamTick * 1.3));
            visual.update(start, delta, visualLength, 1.3f * pulse, roll);

            // Particle sheath so the beam still reads without the resource pack.
            Vector step = delta.clone().normalize();

            // Two flame strands spiralling around the beam, turning with it.
            Vector side = step.clone().crossProduct(new Vector(0, 1, 0));
            if (side.lengthSquared() < 1.0E-6) side = new Vector(1, 0, 0);
            side.normalize();
            Vector up = step.clone().crossProduct(side).normalize();
            for (double d = 0; d < visualLength; d += 1.0) {
                double angle = roll + d * 0.9;
                for (int strand = 0; strand < 2; strand++) {
                    double a = angle + strand * Math.PI;
                    Vector offset = side.clone().multiply(Math.cos(a) * HELIX_RADIUS)
                            .add(up.clone().multiply(Math.sin(a) * HELIX_RADIUS));
                    w.spawnParticle(Particle.FLAME, start.clone().add(step.clone().multiply(d)).add(offset),
                            1, 0, 0, 0, 0.0, null, true);
                }
            }

            for (double d = 0; d < visualLength; d += 1.5) {
                Location p = start.clone().add(step.clone().multiply(d));
                w.spawnParticle(Particle.FLAME, p, 1, 0.12, 0.12, 0.12, 0.0, null, true);
                if (((int) (d / 1.5) + beamTick) % 3 == 0) {
                    w.spawnParticle(Particle.LAVA, p, 1, 0.2, 0.2, 0.2, 0.0, null, true);
                }
            }
        }

        if (blockHit != null) {
            Location impact = blockHit.getHitPosition().toLocation(w);
            w.spawnParticle(Particle.LAVA, impact, 2, 0.2, 0.2, 0.2, 0.0, null, true);
            w.spawnParticle(Particle.SMOKE, impact, 3, 0.2, 0.2, 0.2, 0.02, null, true);
        }

        if (beamTick % 6 == 0) {
            w.playSound(start, Sound.ENTITY_BLAZE_BURN, 1.0f, 0.7f);
        }

        if (beamTick % HIT_INTERVAL != 0) return;

        // One pulse per target per interval, even if the sample spheres overlap.
        Set<UUID> hit = new HashSet<>();
        for (double d = 1.0; d <= length; d += 0.75) {
            Location point = eye.clone().add(dir.clone().multiply(d));
            for (LivingEntity e : point.getNearbyLivingEntities(HIT_RADIUS)) {
                if (!LavaTargets.isValid(player, e, trust) || !hit.add(e.getUniqueId())) continue;

                TrueDamage.of(DAMAGE_PER_PULSE).attacker(player).ignoreIFrames(true).apply(e);
                e.setFireTicks(BURN_TICKS);
                w.spawnParticle(Particle.FLAME, e.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.03, null, true);
            }
        }
    }

    @Override
    public String getName() {
        return ChatColor.GOLD + "Magma Beam";
    }

    @Override
    public String getDescription() {
        return "Charge a ball of magma, then fire a beam that follows your aim and burns everything it crosses. You are slowed while charging and firing.";
    }
}