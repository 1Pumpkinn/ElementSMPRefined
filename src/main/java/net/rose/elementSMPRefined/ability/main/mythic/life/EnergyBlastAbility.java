package net.rose.elementSMPRefined.ability.main.mythic.life;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.particles.element.LifeParticles;
import net.rose.elementSMPRefined.util.damage.AbilityTargets;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.visual.model.EnergyBlastVisual;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Life ability 2: charge a green energy orb in front of you, then release a piercing beam along your aim.
 * <p>
 * The aim is locked at the moment of release, so the beam stays put while it fades out. It stops at the
 * first block and hits every valid target in its path once (a raised shield stops the hit). The look comes
 * from {@link EnergyBlastVisual} - a green beam with a white core, drawn by the pack's core shader.
 */
public class EnergyBlastAbility extends BaseAbility {

    private static final int CHARGE_TICKS = 14;          // orb grows at the muzzle
    private static final int BEAM_TICKS = 12;            // how long the released beam lingers
    private static final double RANGE = 32.0;
    private static final double HIT_RADIUS = 0.9;        // how far from the beam's axis a target still counts
    private static final double DAMAGE = 8.0;            // 4 hearts, true damage
    private static final double KNOCKBACK = 1.1;
    private static final double KNOCKBACK_LIFT = 0.35;

    private static final float BEAM_THICKNESS = 3.0f;
    // The beam's cross-section is 1.5 blocks * thickness wide and the flare texture uses the same radial
    // profile, so a flare of that width has exactly the beam's thickness and colour (no step at the muzzle).
    private static final float BEAM_WIDTH = 1.5f * BEAM_THICKNESS;
    private static final float ORB_MIN = 0.6f;
    private static final float ORB_MAX = 2.6f;           // fully charged orb (quad width in blocks)
    private static final float FLARE_BURST = 0.7f * BEAM_WIDTH; // muzzle flare the instant the beam opens
    private static final float ORB_PULSE = 0.06f;        // +-6% throb while charging
    private static final double WALL_EMBED = 0.3;       // how far the beam sinks into the block it hits
    private static final float SPIN_PER_TICK = 0.35f;
    private static final double SPARK_STEP = 1.5;        // blocks between spark particles along the beam

    private static final Component ALREADY_CHARGING =
            Component.text("Energy Blast is already charging!", NamedTextColor.RED);

    private final ElementSMPRefined plugin;

    public EnergyBlastAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("energy_blast", ElementType.LIFE, 2, Constants.Cooldown.ABILITY_2_SECONDS, 2, configManager);
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
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.8f);

        new BukkitRunnable() {
            final EnergyBlastVisual visual = new EnergyBlastVisual(player);
            int tick = 0;
            float roll = 0f;

            // Locked in when the blast is released.
            Location fireStart;
            Vector fireDir;
            double fireLength;
            Location fireImpact; // null if the beam ran out of range instead of hitting a block

            @Override
            public synchronized void cancel() throws IllegalStateException {
                visual.remove();
                setActive(player, false);
                super.cancel();
            }

            @Override
            public void run() {
                // !isActiveFor: BaseElement.clearEffects (element change) deactivates the ability.
                if (!player.isOnline() || player.isDead() || !isActiveFor(player)) {
                    cancel();
                    return;
                }

                if (tick < CHARGE_TICKS) {
                    charge(player, visual, tick, roll);
                } else {
                    if (tick == CHARGE_TICKS) {
                        release(player, trust);
                    }
                    int beamTick = tick - CHARGE_TICKS;
                    if (beamTick >= BEAM_TICKS) {
                        cancel();
                        return;
                    }
                    drawBeam(player.getWorld(), visual, beamTick, roll);
                }

                tick++;
                roll += SPIN_PER_TICK;
            }

            private void release(Player caster, TrustManager trust) {
                World w = caster.getWorld();
                Location eye = caster.getEyeLocation();
                fireDir = eye.getDirection().normalize();
                fireStart = muzzle(eye, fireDir);

                RayTraceResult blockHit = w.rayTraceBlocks(eye, fireDir, RANGE, FluidCollisionMode.NEVER, true);
                double length = blockHit == null ? RANGE : blockHit.getHitPosition().distance(eye.toVector());
                Vector end = eye.toVector().add(fireDir.clone().multiply(length));
                fireLength = Math.max(0.0, end.clone().subtract(fireStart.toVector()).length());
                if (blockHit != null) {
                    fireLength += WALL_EMBED; // run the beam into the wall so it ends flush, with no fade-out gap
                }
                fireImpact = blockHit == null ? null : end.toLocation(w);

                w.playSound(fireStart, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.5f);
                w.playSound(fireStart, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.9f);
                LifeParticles.energyBlastMuzzle(w, fireStart);
                if (fireImpact != null) {
                    LifeParticles.energyBlastImpact(w, fireImpact);
                    w.playSound(fireImpact, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
                }

                strike(caster, trust, eye, fireDir, length);
            }

            private void drawBeam(World w, EnergyBlastVisual visual, int beamTick, float roll) {
                if (fireLength < 0.5) return;

                // Snaps open, then thins out and disappears over the rest of its life.
                float life = (float) beamTick / BEAM_TICKS;
                float open = Math.min(1f, (beamTick + 1) / 2f);
                float fade = 1f - life * life;
                float thickness = BEAM_THICKNESS * open * Math.max(0.05f, fade);

                visual.updateBeam(fireStart, fireDir, fireLength, thickness, roll);
                // The muzzle flare continues from the charged orb's size, flashes to FLARE_BURST as the beam
                // opens, then collapses faster than the beam so it doesn't linger as a ball.
                float flareFade = (1f - life) * (1f - life);
                float flare = ORB_MAX + (FLARE_BURST - ORB_MAX) * open;
                flare *= Math.max(0.05f, flareFade);
                visual.updateMuzzle(fireStart, flare, 0f);

                // A few sparks along the beam, so it still reads without the resource pack.
                if (beamTick % 2 == 0) {
                    Location p = fireStart.clone();
                    for (double d = 0; d < fireLength; d += SPARK_STEP) {
                        p.setX(fireStart.getX() + fireDir.getX() * d);
                        p.setY(fireStart.getY() + fireDir.getY() * d);
                        p.setZ(fireStart.getZ() + fireDir.getZ() * d);
                        LifeParticles.energyBlastSpark(w, p);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    private void charge(Player player, EnergyBlastVisual visual, int tick, float roll) {
        World w = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location orb = muzzle(eye, dir);

        double progress = (double) (tick + 1) / CHARGE_TICKS;
        // Ease-out: swells quickly, then settles into full size. A faint throb keeps it alive. No spin:
        // the flare is round, so rotating it only makes the star spikes wobble.
        float eased = 1f - (float) Math.pow(1.0 - progress, 3);
        float pulse = 1f + ORB_PULSE * (float) Math.sin(tick * 1.3);
        visual.updateMuzzle(orb, (ORB_MIN + (ORB_MAX - ORB_MIN) * eased) * pulse, 0f);

        // Sparks drawn into the orb from all around.
        double radius = 2.2 * (1.0 - progress) + 0.3;
        Vector offset = Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize().multiply(radius);
        LifeParticles.energyBlastCharge(w, orb.clone().add(offset));

        if (tick % 4 == 0) {
            w.playSound(orb, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.8f + (float) progress);
        }
    }

    /** The beam starts a little ahead of and below the eyes so it doesn't blot out the caster's view. */
    private static Location muzzle(Location eye, Vector dir) {
        return eye.clone().add(0, -0.6, 0).add(dir.clone().multiply(2.5));
    }

    private void strike(Player caster, TrustManager trust, Location eye, Vector dir, double length) {
        if (length < 1.0) return;
        World w = caster.getWorld();

        // One query over the whole beam, then an exact "does the beam pass within HIT_RADIUS" test per entity.
        Vector from = eye.toVector().add(dir.clone().multiply(1.0));
        double reach = length - 1.0;
        Vector to = from.clone().add(dir.clone().multiply(reach));
        BoundingBox corridor = BoundingBox.of(from, to).expand(HIT_RADIUS);

        Set<UUID> hit = new HashSet<>();
        for (Entity nearby : w.getNearbyEntities(corridor, candidate -> candidate instanceof LivingEntity)) {
            LivingEntity e = (LivingEntity) nearby;
            if (!AbilityTargets.isValid(caster, e, trust) || hit.contains(e.getUniqueId())) continue;

            BoundingBox grown = e.getBoundingBox().expand(HIT_RADIUS);
            if (!grown.contains(from) && grown.rayTrace(from, dir, reach) == null) continue;
            hit.add(e.getUniqueId());

            boolean landed = TrueDamage.of(DAMAGE).attacker(caster).ability(getName()).ignoreIFrames(true)
                    .blockableByShield(dir).apply(e);
            if (landed) {
                e.setVelocity(e.getVelocity().add(dir.clone().multiply(KNOCKBACK).setY(KNOCKBACK_LIFT)));
                LifeParticles.energyBlastHit(w, e.getBoundingBox().getCenter().toLocation(w));
            }
        }
    }

    @Override
    public String getName() {
        return ChatColor.GREEN + "Energy Blast";
    }

    @Override
    public String getDescription() {
        return "Charge an orb of life energy, then fire a piercing green beam where you aim.";
    }
}
