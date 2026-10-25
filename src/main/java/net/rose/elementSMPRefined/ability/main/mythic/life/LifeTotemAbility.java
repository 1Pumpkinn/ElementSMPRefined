package net.rose.elementSMPRefined.ability.main.mythic.life;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.items.builder.ModelItems;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.particles.element.LifeParticles;
import net.rose.elementSMPRefined.util.scan.GroundScan;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Totem of Life - Life ability 1. The caster hurls a totem along an arc; it tumbles through the air,
 * sticks upright into the first ground it reaches, and then pulses Regeneration to the caster and
 * trusted players standing near it until it sinks away again (think Wynncraft's Shaman totem).
 *
 * <p>The totem is the {@code elementsmp:life_totem} resource-pack model (eyes glow) shown by an
 * {@link ItemDisplay}. No real blocks are touched, so nothing needs cleaning up apart from the display
 * itself, which {@link #removeAll()} sweeps on plugin disable.</p>
 */
public class LifeTotemAbility extends BaseAbility {

    private static final NamespacedKey MODEL =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:life_totem"));

    // Flight
    private static final double LAUNCH_SPEED = 1.1;      // blocks per tick
    private static final double LAUNCH_LIFT = 0.12;      // extra upward push so the throw arcs
    private static final double GRAVITY = 0.05;          // blocks per tick^2
    private static final int MAX_FLIGHT_TICKS = 60;
    private static final float SPIN_PER_TICK = 0.5f;     // radians, end over end
    private static final float FLIGHT_SCALE = 1.2f;

    // Planted
    private static final float PLANTED_SCALE = 1.6f;     // model is ~1.5 blocks tall at scale 1 (~2.7 planted)
    private static final double EMBED = 0.05;            // sink the base slightly so there is no gap
    private static final int GROW_TICKS = 4;
    private static final int SHRINK_TICKS = 8;
    private static final int DURATION_TICKS = 200;       // 10 seconds standing
    private static final float COLLAPSED = 0.05f;        // never scale to exactly zero

    // Aura
    private static final double RADIUS = Constants.Distance.LIFE_REGEN_RADIUS;
    private static final int PULSE_EVERY_TICKS = 20;
    private static final int PULSE_FIRST_TICK = 3;       // just after it finished planting
    private static final int REGEN_TICKS = 60;           // each pulse refreshes Regeneration II for 3s
    private static final int REGEN_AMPLIFIER = 1;
    private static final int RING_TICKS = 8;             // how long a pulse ring takes to expand

    /** Every live totem, so plugin disable can clear them. */
    private static final List<Totem> ACTIVE = new ArrayList<>();

    private final ElementSMPRefined plugin;

    public LifeTotemAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("life_totem", ElementType.LIFE, 1, 30, 1, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        World world = player.getWorld();

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector start = eye.toVector().add(dir.clone().multiply(0.8)).add(new Vector(0, -0.3, 0));
        Vector velocity = dir.clone().multiply(LAUNCH_SPEED).add(new Vector(0, LAUNCH_LIFT, 0));

        ItemStack modelItem = ModelItems.of(MODEL);
        ItemDisplay display = world.spawn(start.toLocation(world), ItemDisplay.class, d -> {
            d.setItemStack(modelItem);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setTransformation(flightPose(0f));
            d.setPersistent(false);
            d.setInvulnerable(true);
        });

        Totem totem = new Totem(world, display, start, velocity, player.getUniqueId(),
                context.getTrustManager(), eye.getYaw());
        ACTIVE.add(totem);
        totem.runTaskTimer(plugin, 1L, 1L);

        world.playSound(eye, Sound.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 1.5f, 1.4f);
        return true;
    }

    /** Safety net for plugin disable - removes every totem still on screen. */
    public static void removeAll() {
        for (Totem t : new ArrayList<>(ACTIVE)) {
            t.cancelAndRemove();
        }
        ACTIVE.clear();
    }

    // ---- poses ---------------------------------------------------------------------------------

    /** Tumbling pose while thrown: spun end over end around the X axis. */
    private static Transformation flightPose(float spin) {
        return new Transformation(
                new Vector3f(),
                new Quaternionf().rotateX(spin),
                new Vector3f(FLIGHT_SCALE),
                new Quaternionf());
    }

    /**
     * Planted pose at a given growth. Scaling happens around the model's centre, so the display is dropped
     * by however much the totem shrank to keep its base on the ground.
     *
     * @param growth 1.0 = fully risen, {@link #COLLAPSED} = sunk into the ground
     */
    private static Transformation plantedPose(float yaw, float growth) {
        float s = PLANTED_SCALE * growth;
        float lift = -0.5f * PLANTED_SCALE * (1.0f - growth);
        return new Transformation(
                new Vector3f(0f, lift, 0f),
                new Quaternionf().rotateY(yaw),
                new Vector3f(s, s, s),
                new Quaternionf());
    }

    // ---- one totem -----------------------------------------------------------------------------

    private final class Totem extends BukkitRunnable {
        private final World world;
        private final ItemDisplay display;
        private final Vector pos;
        private final Vector vel;
        private final UUID ownerId;
        private final TrustManager trust;
        private final float yaw;

        private boolean planted = false;
        private int flightTicks = 0;
        private float spin = 0f;

        private Location base;       // where the totem's base touches the ground
        private int plantedAge = 0;  // ticks since it stuck into the ground
        private int ringStart = -100;

        Totem(World world, ItemDisplay display, Vector pos, Vector vel, UUID ownerId, TrustManager trust, float yawDegrees) {
            this.world = world;
            this.display = display;
            this.pos = pos.clone();
            this.vel = vel.clone();
            this.ownerId = ownerId;
            this.trust = trust;
            this.yaw = (float) Math.toRadians(-yawDegrees + 180.0);
        }

        @Override
        public void run() {
            if (!display.isValid()) {
                finish();
                return;
            }
            if (planted) {
                tickPlanted();
            } else {
                tickFlight();
            }
        }

        private void tickFlight() {
            if (++flightTicks > MAX_FLIGHT_TICKS || pos.getY() < world.getMinHeight()) {
                fizzle();
                return;
            }

            vel.setY(vel.getY() - GRAVITY);
            double step = vel.length();
            RayTraceResult hit = world.rayTraceBlocks(pos.toLocation(world), vel.clone().normalize(), step,
                    FluidCollisionMode.NEVER, true);

            if (hit != null && hit.getHitPosition() != null) {
                plant(hit);
                return;
            }

            pos.add(vel);
            Location loc = pos.toLocation(world);
            display.teleport(loc);

            spin += SPIN_PER_TICK;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            display.setTransformation(flightPose(spin));

            world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 1, 0.15, 0.15, 0.15, 0.0, null, true);
        }

        private void plant(RayTraceResult hit) {
            Vector hp = hit.getHitPosition();
            BlockFace face = hit.getHitBlockFace();

            Location spot;
            if (face == BlockFace.UP) {
                spot = new Location(world, hp.getX(), hp.getY(), hp.getZ());
            } else {
                // Hit a wall or ceiling: slide out from it and drop to the floor below.
                double nx = hp.getX() + (face != null ? face.getModX() * 0.4 : 0);
                double nz = hp.getZ() + (face != null ? face.getModZ() * 0.4 : 0);
                Block ground = GroundScan.findGround(world, (int) Math.floor(nx), (int) Math.floor(nz),
                        (int) Math.floor(hp.getY()) + 1, 1, 12);
                if (ground == null) {
                    fizzle();
                    return;
                }
                spot = new Location(world, nx, ground.getY() + 1.0, nz);
            }

            base = spot;
            planted = true;
            plantedAge = 0;

            // Snap upright at the ground, collapsed, ready to rise next tick.
            display.setTeleportDuration(0);
            display.teleport(new Location(world, spot.getX(),
                    spot.getY() + 0.5 * PLANTED_SCALE - EMBED, spot.getZ()));
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(0);
            display.setTransformation(plantedPose(yaw, COLLAPSED));

            Block under = world.getBlockAt(spot.getBlockX(), (int) Math.floor(spot.getY() - 0.1), spot.getBlockZ());
            world.spawnParticle(Particle.BLOCK, spot.clone().add(0, 0.1, 0), 18, 0.35, 0.1, 0.35, 0.0,
                    under.getBlockData(), true);
            world.playSound(spot, Sound.BLOCK_STONE_PLACE, SoundCategory.PLAYERS, 1.2f, 0.7f);
            world.playSound(spot, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.6f);
        }

        private void tickPlanted() {
            plantedAge++;
            int t = plantedAge;

            if (t == 1) {
                // Interpolation only animates if the transformation changes after the entity was sent.
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(GROW_TICKS);
                display.setTransformation(plantedPose(yaw, 1.0f));
            }

            if (t >= PULSE_FIRST_TICK && (t - PULSE_FIRST_TICK) % PULSE_EVERY_TICKS == 0 && t < DURATION_TICKS) {
                pulse();
                ringStart = t;
            }

            if (t - ringStart < RING_TICKS) {
                drawRing(RADIUS * (t - ringStart + 1) / RING_TICKS);
            }

            if (t % 5 == 0 && t < DURATION_TICKS) {
                ambient();
            }

            if (t == DURATION_TICKS) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(SHRINK_TICKS);
                display.setTransformation(plantedPose(yaw, COLLAPSED));
                world.playSound(base, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.6f);
            } else if (t >= DURATION_TICKS + SHRINK_TICKS + 2) {
                world.spawnParticle(Particle.HAPPY_VILLAGER, base.clone().add(0, 0.6, 0), 10, 0.4, 0.5, 0.4, 0.0, null, true);
                finish();
            }
        }

        /** Regeneration to the caster and trusted players inside the aura. */
        private void pulse() {
            for (Player other : world.getNearbyPlayers(base, RADIUS)) {
                boolean owner = other.getUniqueId().equals(ownerId);
                boolean trusted = trust != null && trust.isTrusted(ownerId, other.getUniqueId());
                if (!owner && !trusted) continue;

                other.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, REGEN_TICKS,
                        REGEN_AMPLIFIER, true, true, true));
            }
            world.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 2.0f, 0.8f);
        }

        private void drawRing(double radius) {
            int points = 28;
            for (int i = 0; i < points; i++) {
                double a = (Math.PI * 2 * i) / points;
                Location p = base.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius);
                LifeParticles.totemRingPoint(world, p);
            }
        }

        /** Faint glow around the totem's head plus motes drifting up between pulses. */
        private void ambient() {
            world.spawnParticle(Particle.HAPPY_VILLAGER, base.clone().add(0, 1.8, 0), 2, 0.3, 0.3, 0.3, 0.0, null, true);
            double a = world.getGameTime() * 0.3;
            LifeParticles.totemRingPoint(world, base.clone().add(Math.cos(a) * RADIUS, 0.15, Math.sin(a) * RADIUS));
        }

        /** Thrown into the void / never found ground: just disappears. */
        private void fizzle() {
            world.spawnParticle(Particle.SMOKE, pos.toLocation(world), 8, 0.2, 0.2, 0.2, 0.02, null, true);
            finish();
        }

        private void finish() {
            cancelAndRemove();
            ACTIVE.remove(this);
        }

        void cancelAndRemove() {
            if (!isCancelled()) cancel();
            if (display.isValid()) display.remove();
        }
    }

    public String getName() {
        return ChatColor.GREEN + "Totem of Life";
    }

    public String getDescription() {
        return "Launch a totem that plants in the ground and regenerates you and trusted players nearby.";
    }
}