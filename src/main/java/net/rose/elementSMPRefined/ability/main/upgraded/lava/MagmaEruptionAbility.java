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
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Magma Eruption - look at an enemy and the ground under them cracks open. After a short,
 * dodgeable warning the spot erupts: everything in the blast takes damage, is set on fire
 * and is thrown into the air.
 * <p>
 * The eruption is purely visual + entity based (a growing magma {@link BlockDisplay} pillar
 * and particles) - it never changes real blocks, so nothing needs to be restored afterwards.
 */
public class MagmaEruptionAbility extends BaseAbility {

    private static final double RANGE = 30.0;
    private static final double RAY_SIZE = 0.8;
    /** Warning time before the blast. The spot is locked when cast, so moving away dodges it. */
    private static final int WARNING_TICKS = 14;
    private static final double RADIUS = 2.5;
    private static final double DAMAGE = 7.0;
    private static final double LAUNCH_VELOCITY = 1.0;
    private static final int BURN_TICKS = 80;

    private static final Component NO_TARGET = Component.text("No target in sight!", NamedTextColor.RED);
    private static final BlockData MAGMA = Material.MAGMA_BLOCK.createBlockData();

    /** Every live pillar, so plugin disable can sweep up anything still on screen. */
    private static final Set<BlockDisplay> ACTIVE_PILLARS = new HashSet<>();

    private final ElementSMPRefined plugin;

    public MagmaEruptionAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("lava_magma_eruption", ElementType.LAVA, 1, 20, 1, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        TrustManager trust = context.getTrustManager();

        LivingEntity target = findTarget(player, trust);
        if (target == null) {
            player.sendMessage(NO_TARGET);
            return false; // failed cast stays free (no cooldown)
        }

        startEruption(player, trust, groundBelow(target.getLocation()));
        return true;
    }

    /** First valid living entity under the crosshair, not hidden behind a wall. */
    private LivingEntity findTarget(Player player, TrustManager trust) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();

        RayTraceResult blocks = world.rayTraceBlocks(eye, dir, RANGE, FluidCollisionMode.NEVER, true);
        double maxDistance = blocks == null ? RANGE : blocks.getHitPosition().distance(eye.toVector());

        RayTraceResult hit = world.rayTraceEntities(eye, dir, maxDistance, RAY_SIZE,
                e -> e instanceof LivingEntity living && LavaTargets.isValid(player, living, trust));
        return hit == null ? null : (LivingEntity) hit.getHitEntity();
    }

    /** Snaps to the floor so an airborne target still gets an eruption on the ground below. */
    private Location groundBelow(Location from) {
        World world = from.getWorld();
        RayTraceResult down = world.rayTraceBlocks(from.clone().add(0, 0.5, 0), new Vector(0, -1, 0),
                12.0, FluidCollisionMode.NEVER, true);
        Location ground = down == null ? from.clone() : down.getHitPosition().toLocation(world);
        ground.add(0, 0.05, 0);
        ground.setYaw(0f);
        ground.setPitch(0f);
        return ground;
    }

    private void startEruption(Player caster, TrustManager trust, Location center) {
        World world = center.getWorld();
        world.playSound(center, Sound.BLOCK_LAVA_AMBIENT, 1.5f, 0.7f);
        world.playSound(center, Sound.BLOCK_LAVA_POP, 1.2f, 0.6f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= WARNING_TICKS) {
                    erupt(caster, trust, center);
                    cancel();
                    return;
                }

                // Warning: a glowing ring at the blast radius that tightens as the timer runs out.
                double progress = (double) ticks / WARNING_TICKS;
                double ringRadius = RADIUS * (1.0 - 0.25 * progress);
                for (int i = 0; i < 24; i++) {
                    double angle = i * (Math.PI * 2 / 24);
                    Location p = center.clone().add(Math.cos(angle) * ringRadius, 0.1, Math.sin(angle) * ringRadius);
                    world.spawnParticle(Particle.FLAME, p, 1, 0, 0, 0, 0.0, null, true);
                }
                if (ticks % 3 == 0) {
                    world.spawnParticle(Particle.LAVA, center.clone().add(0, 0.2, 0), 3, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0.0, null, true);
                    world.spawnParticle(Particle.BLOCK, center.clone().add(0, 0.2, 0), 8, RADIUS * 0.4, 0.05, RADIUS * 0.4, 0.0, MAGMA, true);
                }
                if (ticks % 5 == 0) {
                    world.playSound(center, Sound.BLOCK_LAVA_POP, 0.8f, 0.8f + (float) progress * 0.6f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void erupt(Player caster, TrustManager trust, Location center) {
        World world = center.getWorld();
        Location mid = center.clone().add(0, 1.0, 0);

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.6f);
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.4f, 0.5f);
        world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);

        world.spawnParticle(Particle.LAVA, mid, 40, 0.8, 1.2, 0.8, 0.0, null, true);
        world.spawnParticle(Particle.FLAME, mid, 70, 0.6, 1.6, 0.6, 0.12, null, true);
        world.spawnParticle(Particle.SMOKE, mid, 30, 0.7, 1.5, 0.7, 0.05, null, true);
        world.spawnParticle(Particle.BLOCK, mid, 60, 0.9, 1.4, 0.9, 0.0, MAGMA, true);

        spawnPillar(center);

        for (LivingEntity e : mid.getNearbyLivingEntities(RADIUS, 2.0, RADIUS)) {
            if (!LavaTargets.isValid(caster, e, trust)) continue;

            TrueDamage.of(DAMAGE).attacker(caster).ignoreIFrames(false).apply(e);
            e.setFireTicks(BURN_TICKS);
            e.setVelocity(new Vector(0, LAUNCH_VELOCITY, 0));
        }
    }

    // ---- magma pillar visual -------------------------------------------------------------

    private void spawnPillar(Location center) {
        World world = center.getWorld();
        BlockDisplay pillar = world.spawn(center, BlockDisplay.class, d -> {
            d.setBlock(MAGMA);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(pillarTransform(0.3f, 0.1f));
            d.setInterpolationDuration(4);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
        ACTIVE_PILLARS.add(pillar);

        // Grow (interpolates over 4 ticks), hold, shrink, remove.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!pillar.isValid()) return;
            pillar.setInterpolationDelay(0);
            pillar.setTransformation(pillarTransform((float) (RADIUS * 0.85), 3.2f));
        }, 1L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!pillar.isValid()) return;
            pillar.setInterpolationDuration(6);
            pillar.setInterpolationDelay(0);
            pillar.setTransformation(pillarTransform(0.2f, 0.1f));
        }, 12L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (pillar.isValid()) pillar.remove();
            ACTIVE_PILLARS.remove(pillar);
        }, 19L);
    }

    /** A block display's origin is a corner, so shift by half the width to centre it on the spot. */
    private static Transformation pillarTransform(float width, float height) {
        return new Transformation(
                new Vector3f(-width / 2f, 0f, -width / 2f),
                new Quaternionf(),
                new Vector3f(width, height, width),
                new Quaternionf());
    }

    /** Safety net for plugin disable - removes every pillar still on screen. */
    public static void removeAll() {
        for (BlockDisplay pillar : new ArrayList<>(ACTIVE_PILLARS)) {
            if (pillar.isValid()) pillar.remove();
        }
        ACTIVE_PILLARS.clear();
    }

    @Override
    public String getName() {
        return ChatColor.GOLD + "Magma Eruption";
    }

    @Override
    public String getDescription() {
        return "Look at an enemy to crack the ground beneath them. After a short warning it erupts, damaging, igniting and launching everything in the blast.";
    }
}
