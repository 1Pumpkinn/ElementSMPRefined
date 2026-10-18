package net.rose.elementSMPRefined.ability.main.upgraded.frost;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.items.builder.ItemBuilder;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Icicle Barrage - Frost ability 2 (a beam of icicles). The caster channels a ball of ice in front of them (slowed while it
 * charges), then lets go: the ball turns into a beam, a dense stream of icicles fired down the line the caster is
 * looking along. Each one is a real projectile that hits the first thing in its path and either hurts it or
 * sticks in the wall.
 *
 * <p>The icicles are resource-pack models ({@code elementsmp:icicle_tip}, the vanilla pointed-dripstone shape wearing the icicle texture) shown by {@link ItemDisplay}s, so
 * this works on 26.2. No real blocks are ever changed, so there is nothing to clean up if the server stops.</p>
 */
public class FrostIcicleBurstAbility extends BaseAbility {

    private static final NamespacedKey ICICLE_MODEL = Objects.requireNonNull(NamespacedKey.fromString("elementsmp:icicle_tip"));

    // Charge
    private static final int CHARGE_TICKS = 30;
    private static final double ORB_DISTANCE = 1.8;     // how far in front of the eyes the orb floats
    private static final double ORB_DROP = 0.3;
    private static final float ORB_MIN_SIZE = 0.25f;
    private static final float ORB_MAX_SIZE = 0.9f;
    private static final int CHARGE_SLOW_AMPLIFIER = 2; // Slowness III while charging

    // Volley
    // A beam: a dense, nearly straight stream of icicles all fired down the same line.
    private static final int ICICLE_COUNT = 32;
    private static final int LAUNCH_TICKS = 8;          // the beam pours out over this long
    private static final double SPREAD = 0.015;         // aim jitter - tiny, so the beam stays tight
    private static final double SPEED = 2.2;            // blocks per tick
    private static final double GRAVITY = 0.0;          // flies dead straight
    private static final double MAX_RANGE = 40.0;
    private static final double HIT_RADIUS = 0.35;
    private static final double START_JITTER = 0.08;    // where around the orb each icicle appears

    // Icicle model: vanilla pointed-dripstone shape with the icicle texture, stretched long and thin.
    // This is the model axis that the point faces (the sprite's point is at the bottom, so -Y); if icicles
    // ever fly backwards, flip its sign.
    private static final Vector3f TIP_AXIS = new Vector3f(0f, -1f, 0f);
    private static final float ICICLE_WIDTH = 0.45f;
    private static final float ICICLE_LENGTH = 1.6f;
    /** The sprite's point is 5 of the model's 16 pixels below its centre, in blocks - a stuck icicle is pushed this far into the wall. */
    private static final double ICICLE_HALF_LENGTH = ICICLE_LENGTH * 5.0 / 16.0;

    // Hit
    private static final double DAMAGE = 1.5;           // per icicle
    private static final int MAX_HITS_PER_TARGET = 8;   // so a full beam is strong but not instant death
    private static final int SLOW_TICKS = 60;
    private static final int SLOW_AMPLIFIER = 1;        // Slowness II
    private static final int FREEZE_PER_HIT = 25;       // ticks of powder-snow freeze added per icicle

    // Icicles that miss and stick into a wall
    private static final int STUCK_TICKS = 40;
    private static final int STUCK_SHRINK_TICKS = 5;
    private static final float COLLAPSED = 0.05f;

    // Hard stop so a cast can never run forever
    private static final int MAX_TASK_TICKS = CHARGE_TICKS + LAUNCH_TICKS + (int) (MAX_RANGE / SPEED) + STUCK_TICKS + 40;

    // Every live display, so they can be swept up when the plugin shuts down or reloads
    private static final Set<Display> ACTIVE = new HashSet<>();

    private final ElementSMPRefined plugin;
    private final Set<UUID> activeCasters = new HashSet<>();

    public FrostIcicleBurstAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("frost_icicle_barrage", ElementType.FROST, 2, 60, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    /** Removes every orb and icicle still on screen. Call from the plugin's disable hook. */
    public static void removeAll() {
        for (Display display : new ArrayList<>(ACTIVE)) {
            if (display.isValid()) display.remove();
        }
        ACTIVE.clear();
    }

    @Override
    public boolean execute(ElementContext context) {
        Player caster = context.getPlayer();
        if (!activeCasters.add(caster.getUniqueId())) return false;

        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 1.2f, 0.5f);
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, CHARGE_TICKS + 2,
                CHARGE_SLOW_AMPLIFIER, false, false, true));

        new Cast(caster, context.getTrustManager()).runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /** One icicle in flight. */
    private static final class Icicle {
        final ItemDisplay display;
        final Location pos;
        final Vector velocity;
        double travelled = 0;
        int stuckLeft = -1; // -1 = still flying

        Icicle(ItemDisplay display, Location pos, Vector velocity) {
            this.display = display;
            this.pos = pos;
            this.velocity = velocity;
        }
    }

    /** One cast: charges in front of the caster, pours out the beam, then runs until every icicle is gone. */
    private final class Cast extends BukkitRunnable {
        private final Player caster;
        private final TrustManager trust;
        private final World world;
        private final BlockDisplay orb;
        private final ItemStack icicleItem = ItemBuilder.of(Material.STICK).itemModel(ICICLE_MODEL).build();
        private final List<Icicle> icicles = new ArrayList<>();
        private final Map<UUID, Integer> hitCounts = new HashMap<>();

        private int ticks = 0;
        private int launched = 0;

        Cast(Player caster, TrustManager trust) {
            this.caster = caster;
            this.trust = trust;
            this.world = caster.getWorld();

            this.orb = world.spawn(orbSpot(caster), BlockDisplay.class, d -> {
                d.setBlock(Material.BLUE_ICE.createBlockData());
                d.setBillboard(Display.Billboard.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(orbPose(ORB_MIN_SIZE, 0));
                d.setTeleportDuration(1);
                d.setPersistent(false);
                d.setInvulnerable(true);
            });
            ACTIVE.add(orb);
        }

        @Override
        public void run() {
            if (!caster.isOnline() || ticks > MAX_TASK_TICKS) {
                end();
                return;
            }

            // /tick freeze: the scheduler keeps running but entities stop updating, so without this the
            // icicles would fly on, hit their range limit and vanish while the world looks paused. Hold
            // everything exactly where it is (the tick counter included); /tick step still advances it.
            if (isTickFrozen()) return;

            try {
                if (ticks < CHARGE_TICKS) {
                    charge();
                } else {
                    fire();
                }
            } finally {
                ticks++;
            }
        }

        private void charge() {
            if (caster.isDead() || !caster.getWorld().equals(world) || !orb.isValid()) {
                end();
                return;
            }

            double progress = (double) ticks / CHARGE_TICKS;
            float size = (float) (ORB_MIN_SIZE + (ORB_MAX_SIZE - ORB_MIN_SIZE) * progress);

            Location pos = orbSpot(caster);
            orb.setInterpolationDelay(0);
            orb.setInterpolationDuration(1);
            orb.setTransformation(orbPose(size, ticks));
            orb.teleport(pos);

            // Frost spirals inward toward the orb as it fills up.
            double r = 1.6 * (1.0 - progress) + 0.3;
            for (int i = 0; i < 4; i++) {
                double angle = (Math.PI * 2 * i / 4) + ticks * 0.45;
                world.spawnParticle(Particle.SNOWFLAKE,
                        pos.clone().add(Math.cos(angle) * r, Math.sin(angle * 1.3) * r * 0.5, Math.sin(angle) * r),
                        1, 0.02, 0.02, 0.02, 0.0, null, true);
            }
            if (ticks % 4 == 0) {
                world.playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, (float) (0.5 + progress * 1.2));
            }
        }

        /** Pours the beam out over LAUNCH_TICKS, then just flies whatever is left. */
        private void fire() {
            int fireTick = ticks - CHARGE_TICKS;

            if (fireTick == 0) {
                caster.swingMainHand();
                Location pos = orbSpot(caster);
                world.spawnParticle(Particle.CLOUD, pos, 15, 0.3, 0.3, 0.3, 0.06, null, true);
                world.spawnParticle(Particle.BLOCK, pos, 25, 0.3, 0.3, 0.3, 0.0, Material.PACKED_ICE.createBlockData(), true);
                world.playSound(pos, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
                world.playSound(pos, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2f, 0.8f);
            }

            if (fireTick < LAUNCH_TICKS && caster.isOnline() && !caster.isDead() && caster.getWorld().equals(world)) {
                // The orb follows the caster and shrinks as it empties.
                if (orb.isValid()) {
                    float size = ORB_MAX_SIZE * (1.0f - (float) (fireTick + 1) / LAUNCH_TICKS);
                    orb.setInterpolationDelay(0);
                    orb.setInterpolationDuration(1);
                    orb.setTransformation(orbPose(Math.max(size, COLLAPSED), ticks));
                    orb.teleport(orbSpot(caster));
                }

                int target = Math.round((fireTick + 1) * (float) ICICLE_COUNT / LAUNCH_TICKS);
                while (launched < target) {
                    launchOne();
                    launched++;
                }
                if (fireTick % 2 == 0) {
                    world.playSound(orbSpot(caster), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.4f);
                }
            } else if (orb.isValid()) {
                orb.remove();
                ACTIVE.remove(orb);
            }

            for (Iterator<Icicle> it = icicles.iterator(); it.hasNext(); ) {
                if (updateIcicle(it.next())) it.remove();
            }

            if (launched >= ICICLE_COUNT && icicles.isEmpty()) {
                end();
            }
        }

        /** Spawns one icicle at a random spot around the orb, aimed where the caster is looking right now. */
        private void launchOne() {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();

            Vector aim = caster.getEyeLocation().getDirection().normalize();
            Vector right = aim.clone().crossProduct(new Vector(0, 1, 0));
            if (right.lengthSquared() < 1.0E-6) right = new Vector(1, 0, 0); // looking straight up/down
            right.normalize();
            Vector up = right.clone().crossProduct(aim).normalize();

            // A hair of random offset so the icicles do not stack perfectly inside each other.
            double angle = rnd.nextDouble() * Math.PI * 2;
            double radius = SPREAD * Math.sqrt(rnd.nextDouble());
            Vector dir = aim.clone()
                    .add(right.multiply(Math.cos(angle) * radius))
                    .add(up.multiply(Math.sin(angle) * radius))
                    .normalize();

            Location start = orbSpot(caster).add(
                    (rnd.nextDouble() - 0.5) * START_JITTER,
                    (rnd.nextDouble() - 0.5) * START_JITTER,
                    (rnd.nextDouble() - 0.5) * START_JITTER);
            Vector velocity = dir.multiply(SPEED * (0.9 + rnd.nextDouble() * 0.2));

            ItemDisplay display = world.spawn(start, ItemDisplay.class, d -> {
                d.setItemStack(icicleItem);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(pose(velocity, 1.0f));
                d.setTeleportDuration(1);
                d.setPersistent(false);
                d.setInvulnerable(true);
            });
            ACTIVE.add(display);
            icicles.add(new Icicle(display, start, velocity));
        }

        /** Advances one icicle a tick. Returns true once it is finished and removed. */
        private boolean updateIcicle(Icicle ic) {
            if (!ic.display.isValid()) {
                ACTIVE.remove(ic.display);
                return true;
            }

            // Stuck in a wall: wait, shrink away, vanish.
            if (ic.stuckLeft >= 0) {
                if (ic.stuckLeft == STUCK_SHRINK_TICKS) {
                    ic.display.setInterpolationDelay(0);
                    ic.display.setInterpolationDuration(STUCK_SHRINK_TICKS);
                    ic.display.setTransformation(pose(ic.velocity, COLLAPSED));
                }
                if (ic.stuckLeft-- <= 0) {
                    remove(ic);
                    return true;
                }
                return false;
            }

            Vector direction = ic.velocity.clone().normalize();
            double step = ic.velocity.length();

            RayTraceResult hit = world.rayTrace(ic.pos, direction, step, FluidCollisionMode.NEVER, true,
                    HIT_RADIUS, this::isTarget);

            if (hit != null) {
                if (hit.getHitEntity() instanceof LivingEntity target) {
                    hitEntity(target, hit.getHitPosition());
                    remove(ic);
                    return true;
                }
                if (hit.getHitBlock() != null) {
                    stickInto(ic, hit);
                    return false;
                }
            }

            ic.pos.add(ic.velocity);
            ic.travelled += step;
            ic.velocity.setY(ic.velocity.getY() - GRAVITY);

            ic.display.setInterpolationDelay(0);
            ic.display.setInterpolationDuration(1);
            ic.display.setTransformation(pose(ic.velocity, 1.0f));
            ic.display.teleport(ic.pos);

            world.spawnParticle(Particle.SNOWFLAKE, ic.pos, 1, 0.05, 0.05, 0.05, 0.0, null, true);

            if (ic.travelled >= MAX_RANGE) {
                remove(ic);
                return true;
            }
            return false;
        }

        private void remove(Icicle ic) {
            if (ic.display.isValid()) ic.display.remove();
            ACTIVE.remove(ic.display);
        }

        private void stickInto(Icicle ic, RayTraceResult hit) {
            Vector at = hit.getHitPosition();
            Vector dir = ic.velocity.clone().normalize();
            // Centre the model so its point sits just inside the surface.
            Location embedded = new Location(world, at.getX(), at.getY(), at.getZ())
                    .subtract(dir.clone().multiply(ICICLE_HALF_LENGTH - 0.15));

            ic.display.setInterpolationDelay(0);
            ic.display.setInterpolationDuration(0);
            ic.display.teleport(embedded);
            ic.stuckLeft = STUCK_TICKS;

            BlockFace face = hit.getHitBlockFace();
            Location puff = new Location(world, at.getX(), at.getY(), at.getZ());
            if (face != null) puff.add(face.getDirection().multiply(0.1));
            world.spawnParticle(Particle.BLOCK, puff, 4, 0.1, 0.1, 0.1, 0.0, Material.ICE.createBlockData(), true);
            world.playSound(puff, Sound.BLOCK_GLASS_HIT, 0.6f, 1.5f);
        }

        private void hitEntity(LivingEntity target, Vector at) {
            hitCounts.merge(target.getUniqueId(), 1, Integer::sum);

            TrueDamage.of(DAMAGE).attacker(caster).ability(getName()).apply(target);

            target.setFreezeTicks(Math.min(target.getMaxFreezeTicks() + 40, target.getFreezeTicks() + FREEZE_PER_HIT));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER, false, true, true));

            Location spot = new Location(world, at.getX(), at.getY(), at.getZ());
            world.spawnParticle(Particle.SNOWFLAKE, spot, 6, 0.15, 0.15, 0.15, 0.04, null, true);
            world.spawnParticle(Particle.BLOCK, spot, 4, 0.1, 0.1, 0.1, 0.0, Material.ICE.createBlockData(), true);
            world.playSound(spot, Sound.BLOCK_GLASS_BREAK, 0.7f, 1.4f);
        }

        /** Entities an icicle can hit. A target that has already taken its cap of icicles is let through. */
        private boolean isTarget(Entity entity) {
            if (!(entity instanceof LivingEntity living)) return false;
            if (entity.equals(caster) || entity instanceof ArmorStand) return false;
            if (entity instanceof Player other) {
                if (other.getGameMode() == GameMode.SPECTATOR) return false;
                if (trust != null && trust.isTrusted(caster.getUniqueId(), other.getUniqueId())) return false;
            }
            if (hitCounts.getOrDefault(entity.getUniqueId(), 0) >= MAX_HITS_PER_TARGET) return false;
            return living.isValid();
        }

        private void end() {
            activeCasters.remove(caster.getUniqueId());
            if (orb.isValid()) orb.remove();
            ACTIVE.remove(orb);
            for (Icicle ic : icicles) remove(ic);
            icicles.clear();
            cancel();
        }
    }

    /** True while {@code /tick freeze} is active and no {@code /tick step} is being run. */
    private static boolean isTickFrozen() {
        var tickManager = Bukkit.getServerTickManager();
        return tickManager.isFrozen() && !tickManager.isStepping();
    }

    /** Where the charging orb floats: just in front of the caster's eyes, a little low. */
    private static Location orbSpot(Player caster) {
        Location eye = caster.getEyeLocation();
        Location spot = eye.add(eye.getDirection().multiply(ORB_DISTANCE)).subtract(0, ORB_DROP, 0);
        // A display entity is also rotated by its own yaw/pitch, on top of its transformation. The eye
        // location carries the caster's look direction, so zero it or every model gets turned twice.
        spot.setYaw(0f);
        spot.setPitch(0f);
        return spot;
    }

    /** Spinning, centred pose for the orb: a BlockDisplay rotates about its corner, so offset it back to its middle. */
    private static Transformation orbPose(float size, int tick) {
        Quaternionf spin = new Quaternionf().rotateY(tick * 0.3f).rotateX(tick * 0.2f);
        Vector3f offset = new Vector3f(size / 2f).rotate(spin).negate();
        return new Transformation(offset, spin, new Vector3f(size), new Quaternionf());
    }

    /** Points the model's tip (TIP_AXIS) along the flight direction. Scale 0..1 shrinks it away. */
    private static Transformation pose(Vector direction, float scale) {
        Vector d = direction.clone().normalize();
        Quaternionf facing = new Quaternionf().rotationTo(TIP_AXIS,
                new Vector3f((float) d.getX(), (float) d.getY(), (float) d.getZ()));
        return new Transformation(
                new Vector3f(),
                facing,
                new Vector3f(ICICLE_WIDTH * scale, ICICLE_LENGTH * scale, ICICLE_WIDTH * scale),
                new Quaternionf());
    }

    @Override
    public String getName() {
        return ChatColor.AQUA + "Icicle Barrage";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Charge a ball of ice, then fire a beam of icicles that damage, slow and freeze whatever they hit.";
    }
}