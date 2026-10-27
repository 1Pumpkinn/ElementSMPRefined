package net.rose.elementSMPRefined.ability.main.upgraded.frost;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.items.builder.ModelItems;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.visual.PingCompensation;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Icicle Barrage - Frost ability 1, built the same way as Magma Beam: hold still (slowed) while the
 * attack charges in front of you, then unleash it along your aim.
 * <p>
 * Charging: frost is pulled in towards a point just ahead of the caster and, one by one, icicles
 * materialise in a slowly spinning ring around it, all pointing the way the caster is looking. The ring
 * follows the caster's aim the whole time.
 * <p>
 * Firing: once the ring is full, the icicles are released ONE AT A TIME, {@link #FIRE_INTERVAL_TICKS}
 * ticks apart. Each one is a real projectile fired at whatever the caster is looking at
 * at that moment; it hits the first thing in its path and either hurts it or sticks in the wall.
 * <p>
 * The icicles are resource-pack models ({@code elementsmp:icicle_tip}, the same model Ice Spikes uses)
 * shown by {@link ItemDisplay}s. No real blocks are ever changed, so there is nothing to clean up if the
 * server stops.
 */
public class FrostIcicleBarrageAbility extends BaseAbility {

    private static final NamespacedKey ICICLE_MODEL = Objects.requireNonNull(NamespacedKey.fromString("elementsmp:icicle_tip"));

    private static final Component ALREADY_ACTIVE =
            Component.text("Icicle Barrage is already active!", NamedTextColor.RED);

    // Charge
    private static final int CHARGE_TICKS = 14;
    private static final int SLOW_AMPLIFIER_CASTER = 2;     // Slowness III for the whole cast, like Magma Beam
    private static final double ORB_DISTANCE = 3.0;         // how far in front of the eyes the ring floats
    private static final double ORB_DROP = 0.2;
    private static final double RING_RADIUS = 1.1;
    private static final float RING_SPIN_PER_TICK = 0.2f;   // radians; the ring turns around the aim
    private static final float LOADED_SCALE = 0.5f;         // size of an icicle hovering in the ring (1.0 = flying size)
    private static final int APPEAR_TICKS = 3;              // an icicle grows in over this long once loaded
    private static final int CHARGE_SPARKS = 1;             // frost pulled towards the ring each tick

    // Volley
    private static final int ICICLE_COUNT = 6;
    private static final int FIRE_INTERVAL_TICKS = 8;       // ticks between icicles (20 = 1 second)
    private static final double SPEED = 2.8;                // blocks per tick
    private static final double MAX_RANGE = 40.0;
    private static final double HIT_RADIUS = 0.6;
    private static final int LAUNCH_GROW_TICKS = 3;         // loaded size -> full size as it leaves the ring
    private static final int MAX_SMOOTHING_TICKS = 2;       // cap on ping smoothing; more makes the model trail its hitbox
    private static final double AIM_ASSIST_RADIUS = 0.3;    // how forgiving the "what am I looking at" check is

    // Icicle model: vanilla pointed-dripstone shape with the icicle texture, stretched long and thin.
    // This is the model axis that the point faces (the sprite's point is at the bottom, so -Y); if icicles
    // ever fly backwards, flip its sign.
    private static final Vector3f TIP_AXIS = new Vector3f(0f, -1f, 0f);
    private static final float ICICLE_WIDTH = 1.0f;
    private static final float ICICLE_LENGTH = 3.2f;
    /** The sprite's point is 5 of the model's 16 pixels below its centre, in blocks - a stuck icicle is pushed this far into the wall. */
    private static final double ICICLE_HALF_LENGTH = ICICLE_LENGTH * 5.0 / 16.0;

    // Hit
    private static final double DAMAGE = 4.0;               // per icicle (6 icicles = 12 if they all land)
    private static final int SLOW_TICKS = 60;
    private static final int SLOW_AMPLIFIER = 1;            // Slowness II on the target
    private static final int FREEZE_PER_HIT = 60;           // ticks of powder-snow freeze added per icicle

    // Icicles that miss and stick into a wall
    private static final int STUCK_TICKS = 40;
    private static final int STUCK_SHRINK_TICKS = 5;
    private static final float COLLAPSED = 0.05f;
    private static final float STUCK_SCALE = 0.55f;         // a planted icicle is smaller than a flying one
    private static final double STUCK_EMBED = 0.12;         // how far the point sits inside the surface
    private static final double STUCK_STRAIGHTEN = 0.7;     // 0 = keep the flight angle, 1 = point straight into the wall

    // Hard stop so a cast can never run forever
    private static final int MAX_TASK_TICKS = CHARGE_TICKS + (ICICLE_COUNT - 1) * FIRE_INTERVAL_TICKS
            + (int) (MAX_RANGE / SPEED) + STUCK_TICKS + 40;

    // Block data for the particle bursts, built once instead of on every hit / launch / stick.
    private static final org.bukkit.block.data.BlockData PACKED_ICE_DATA = Material.PACKED_ICE.createBlockData();
    private static final org.bukkit.block.data.BlockData ICE_DATA = Material.ICE.createBlockData();

    // Every live display, so they can be swept up when the plugin shuts down or reloads
    private static final Set<Display> ACTIVE = new HashSet<>();

    private final ElementSMPRefined plugin;

    public FrostIcicleBarrageAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("frost_icicle_barrage", ElementType.FROST, 1, 60, 1, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    /** Removes every icicle still on screen. Call from the plugin's disable hook. */
    public static void removeAll() {
        for (Display display : new ArrayList<>(ACTIVE)) {
            if (display.isValid()) display.remove();
        }
        ACTIVE.clear();
    }

    @Override
    public boolean execute(ElementContext context) {
        Player caster = context.getPlayer();

        if (isActiveFor(caster)) {
            caster.sendMessage(ALREADY_ACTIVE);
            return false;
        }

        setActive(caster, true);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 1.2f, 0.5f);
        int slowTicks = CHARGE_TICKS + (ICICLE_COUNT - 1) * FIRE_INTERVAL_TICKS + 5;
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks,
                SLOW_AMPLIFIER_CASTER, false, false, true));

        new Cast(caster, context.getTrustManager()).runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /** One icicle: first hovering in the ring, then (once launched) in flight. */
    private static final class Icicle {
        final ItemDisplay display;
        final int appearTick;
        Location pos;
        Vector velocity = new Vector();
        boolean inRing = true;
        double travelled = 0;
        int stuckLeft = -1; // -1 = still flying
        Vector stuckDir;    // direction the point faces once planted

        Icicle(ItemDisplay display, Location pos, int appearTick) {
            this.display = display;
            this.pos = pos;
            this.appearTick = appearTick;
        }
    }

    /** One cast: charges a ring in front of the caster, fires it one icicle at a time, then runs until every icicle is gone. */
    private final class Cast extends BukkitRunnable {
        private final Player caster;
        private final TrustManager trust;
        private final World world;
        private final ItemStack icicleItem = ModelItems.of(ICICLE_MODEL);
        private final Icicle[] slots = new Icicle[ICICLE_COUNT];
        private final List<Icicle> flying = new ArrayList<>();

        private int ticks = 0;
        private int spawned = 0;
        private int launched = 0;
        private float spin = 0f;

        Cast(Player caster, TrustManager trust) {
            this.caster = caster;
            this.trust = trust;
            this.world = caster.getWorld();
        }

        @Override
        public void run() {
            // !isActiveFor: BaseElement.clearEffects (element change) deactivates the ability.
            if (!caster.isOnline() || caster.isDead() || !isActiveFor(caster)
                    || !caster.getWorld().equals(world) || ticks > MAX_TASK_TICKS) {
                end();
                return;
            }

            // /tick freeze: the scheduler keeps running but entities stop updating, so without this the
            // icicles would fly on, hit their range limit and vanish while the world looks paused. Hold
            // everything exactly where it is (the tick counter included); /tick step still advances it.
            if (isTickFrozen()) return;

            try {
                Location eye = caster.getEyeLocation();
                Location orb = orbSpot(eye);
                Vector aim = eye.getDirection().normalize();
                Vector[] axes = axes(aim);

                if (ticks < CHARGE_TICKS) {
                    chargeEffects(orb);
                } else {
                    ringAura(orb);
                }

                // Load the next icicle into the ring whenever its turn comes up.
                while (spawned < ICICLE_COUNT && ticks >= appearTick(spawned)) {
                    loadNext(orb, aim, axes);
                }

                // Release one icicle every FIRE_INTERVAL_TICKS once the ring is full.
                int fireTick = ticks - CHARGE_TICKS;
                if (fireTick >= 0 && fireTick % FIRE_INTERVAL_TICKS == 0 && launched < ICICLE_COUNT) {
                    launchNext(aim);
                }

                updateRing(orb, aim, axes);

                for (Iterator<Icicle> it = flying.iterator(); it.hasNext(); ) {
                    if (updateIcicle(it.next())) it.remove();
                }

                if (launched >= ICICLE_COUNT && flying.isEmpty()) {
                    end();
                }
            } finally {
                ticks++;
                spin += RING_SPIN_PER_TICK;
            }
        }

        /** Tick at which a given ring slot gets its icicle, spread evenly across the charge. */
        private int appearTick(int slot) {
            return Math.round(slot * (float) CHARGE_TICKS / ICICLE_COUNT);
        }

        /** Frost is pulled in towards the ring while it fills up. */
        private void chargeEffects(Location orb) {
            double progress = (double) ticks / CHARGE_TICKS;

            // Kept sparse (and close in) so the ring stays easy to see through.
            if (ticks % 2 == 0) {
                double r = 1.8 * (1.0 - progress) + 0.4;
                for (int i = 0; i < CHARGE_SPARKS; i++) {
                    Vector offset = Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize().multiply(r);
                    world.spawnParticle(Particle.SNOWFLAKE, orb.clone().add(offset), 1, 0, 0, 0, 0.0, null, true);
                }
            }
            if (ticks % 4 == 0) {
                world.playSound(orb, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, (float) (0.5 + progress * 1.2));
            }
        }

        /** A light frost haze while the loaded ring waits for its turn to fire. */
        private void ringAura(Location orb) {
            if (ticks % 4 == 0) {
                world.spawnParticle(Particle.SNOWFLAKE, orb, 1, 0.3, 0.3, 0.3, 0.0, null, true);
            }
        }

        /** Materialises the next icicle in the ring, tiny at first - updateRing grows it in. */
        private void loadNext(Location orb, Vector aim, Vector[] axes) {
            int slot = spawned++;
            Location pos = ringSpot(orb, axes, slot, 0f);

            ItemDisplay display = world.spawn(pos, ItemDisplay.class, d -> {
                d.setItemStack(icicleItem);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(pose(aim, COLLAPSED));
                d.setTeleportDuration(1);
                d.setPersistent(false);
                d.setInvulnerable(true);
            });
            ACTIVE.add(display);
            slots[slot] = new Icicle(display, pos, ticks);

            double progress = Math.min(1.0, (double) ticks / CHARGE_TICKS);
            world.spawnParticle(Particle.SNOWFLAKE, pos, 2, 0.1, 0.1, 0.1, 0.02, null, true);
            world.playSound(pos, Sound.BLOCK_GLASS_PLACE, 0.8f, (float) (0.6 + progress * 0.9));
        }

        /** Keeps every icicle still in the ring hovering around the orb, pointing where the caster looks. */
        private void updateRing(Location orb, Vector aim, Vector[] axes) {
            for (int i = 0; i < ICICLE_COUNT; i++) {
                Icicle ic = slots[i];
                if (ic == null || !ic.inRing) continue;
                if (!ic.display.isValid()) {
                    ic.inRing = false; // killed or unloaded: it is simply gone
                    ACTIVE.remove(ic.display);
                    continue;
                }

                float grow = Math.min(1.0f, (ticks - ic.appearTick + 1) / (float) APPEAR_TICKS);
                Location pos = ringSpot(orb, axes, i, grow);
                ic.pos = pos;

                ic.display.setInterpolationDelay(0);
                ic.display.setInterpolationDuration(1);
                ic.display.setTransformation(pose(aim, Math.max(COLLAPSED, LOADED_SCALE * grow)));
                ic.display.teleport(pos);
            }
        }

        /** Where a ring slot sits: spread around the aim axis, spinning slowly, easing outwards as it grows in. */
        private Location ringSpot(Location orb, Vector[] axes, int slot, float grow) {
            double angle = spin + Math.PI * 2 * slot / ICICLE_COUNT;
            double r = RING_RADIUS * (0.4 + 0.6 * grow);
            return orb.clone()
                    .add(axes[0].clone().multiply(Math.cos(angle) * r))
                    .add(axes[1].clone().multiply(Math.sin(angle) * r));
        }

        /** Lets the next icicle in the ring go, fired at whatever the caster is looking at right now. */
        private void launchNext(Vector aim) {
            int slot = launched++;
            if (launched >= ICICLE_COUNT) releaseCaster(); // last one is away: the caster can move again

            Icicle ic = slots[slot];
            if (ic == null || !ic.inRing || !ic.display.isValid()) return;
            ic.inRing = false;

            // Aim at the thing under the crosshair (entity or block), not just parallel to the eye line,
            // because the icicle starts a little off to the side in the ring.
            Location eye = caster.getEyeLocation();
            RayTraceResult sight = world.rayTrace(eye, aim, MAX_RANGE, FluidCollisionMode.NEVER, true,
                    AIM_ASSIST_RADIUS, this::isTarget);
            Vector target = sight != null
                    ? sight.getHitPosition()
                    : eye.toVector().add(aim.clone().multiply(MAX_RANGE));

            Vector dir = target.clone().subtract(ic.pos.toVector());
            // Something right in the caster's face would send the icicle backwards - fall back to the aim line.
            if (dir.lengthSquared() < 1.0E-4 || dir.clone().normalize().dot(aim) < 0.2) {
                dir = aim.clone();
            }
            ic.velocity = dir.normalize().multiply(SPEED);

            // The flight is a dead-straight line, so the pose is set exactly once here (it also grows the
            // icicle from its hovering size to full size over a few ticks) and only the position changes
            // after that. Re-sending the pose every tick restarted its interpolation and made it judder.
            ic.display.setTeleportDuration(PingCompensation.smoothingTicks(caster, MAX_SMOOTHING_TICKS));
            ic.display.setInterpolationDelay(0);
            ic.display.setInterpolationDuration(LAUNCH_GROW_TICKS);
            ic.display.setTransformation(pose(ic.velocity, 1.0f));

            world.spawnParticle(Particle.CLOUD, ic.pos, 3, 0.15, 0.15, 0.15, 0.04, null, true);
            world.spawnParticle(Particle.BLOCK, ic.pos, 4, 0.15, 0.15, 0.15, 0.0, PACKED_ICE_DATA, true);
            world.playSound(ic.pos, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.9f);
            world.playSound(ic.pos, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 1.1f);
            world.playSound(ic.pos, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.4f);

            flying.add(ic);
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
                    // Sinks back into the wall while it shrinks instead of hanging in the air.
                    ic.display.setTransformation(pose(ic.stuckDir, COLLAPSED,
                            (float) (ICICLE_HALF_LENGTH * STUCK_SCALE)));
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
                    hitEntity(target, hit.getHitPosition(), direction);
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
            BlockFace face = hit.getHitBlockFace();

            // The icicle arrives at whatever angle it was fired (it starts off to the side of the ring),
            // which looks lopsided when planted. Pull it most of the way towards pointing straight into
            // the surface so it looks stuck in, like an arrow.
            Vector dir = ic.velocity.clone().normalize();
            if (face != null) {
                Vector inward = face.getDirection().multiply(-1);
                Vector blended = dir.clone().multiply(1.0 - STUCK_STRAIGHTEN).add(inward.multiply(STUCK_STRAIGHTEN));
                if (blended.lengthSquared() > 1.0E-6) dir = blended.normalize();
            }
            ic.stuckDir = dir;

            // Smaller than the flying size, with the point just inside the surface.
            double halfLength = ICICLE_HALF_LENGTH * STUCK_SCALE;
            Location embedded = new Location(world, at.getX(), at.getY(), at.getZ())
                    .subtract(dir.clone().multiply(halfLength - STUCK_EMBED));

            // Snap straight to the final pose (no interpolation from the flying pose), then place it.
            ic.display.setInterpolationDelay(0);
            ic.display.setInterpolationDuration(0);
            ic.display.setTeleportDuration(0);
            ic.display.setTransformation(pose(dir, STUCK_SCALE));
            ic.display.teleport(embedded);
            ic.stuckLeft = STUCK_TICKS;

            Location puff = new Location(world, at.getX(), at.getY(), at.getZ());
            if (face != null) puff.add(face.getDirection().multiply(0.1));
            world.spawnParticle(Particle.BLOCK, puff, 6, 0.15, 0.15, 0.15, 0.0, ICE_DATA, true);
            world.playSound(puff, Sound.BLOCK_GLASS_HIT, 0.8f, 1.3f);
        }

        private void hitEntity(LivingEntity target, Vector at, Vector direction) {
            // A raised shield stops the icicle: no damage, no freeze/slow, and the shield takes the wear.
            boolean landed = TrueDamage.of(DAMAGE).attacker(caster).ability(getName())
                    .blockableByShield(direction).apply(target);

            if (landed) {
                target.setFreezeTicks(Math.min(target.getMaxFreezeTicks() + 40, target.getFreezeTicks() + FREEZE_PER_HIT));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER, false, true, true));
            }

            Location spot = new Location(world, at.getX(), at.getY(), at.getZ());
            world.spawnParticle(Particle.SNOWFLAKE, spot, 14, 0.25, 0.25, 0.25, 0.05, null, true);
            world.spawnParticle(Particle.BLOCK, spot, 8, 0.2, 0.2, 0.2, 0.0, ICE_DATA, true);
            world.playSound(spot, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.2f);
        }

        /** Entities an icicle can hit. */
        private boolean isTarget(Entity entity) {
            if (!(entity instanceof LivingEntity living)) return false;
            if (entity.equals(caster) || entity instanceof ArmorStand) return false;
            if (entity instanceof Player other) {
                if (other.getGameMode() == GameMode.SPECTATOR) return false;
                if (trust != null && trust.isTrusted(caster.getUniqueId(), other.getUniqueId())) return false;
            }
            return living.isValid();
        }

        /** Takes the slowness back off the caster (only the one this cast applied). Safe to call twice. */
        private void releaseCaster() {
            PotionEffect slow = caster.getPotionEffect(PotionEffectType.SLOWNESS);
            if (slow != null && slow.getAmplifier() == SLOW_AMPLIFIER_CASTER) {
                caster.removePotionEffect(PotionEffectType.SLOWNESS);
            }
        }

        /** Every exit path comes through here, so no icicle or slowness can outlive the ability. */
        private void end() {
            setActive(caster, false);
            releaseCaster();
            for (Icicle ic : slots) {
                if (ic != null) remove(ic);
            }
            for (Icicle ic : flying) remove(ic);
            flying.clear();
            cancel();
        }
    }

    /** True while {@code /tick freeze} is active and no {@code /tick step} is being run. */
    private static boolean isTickFrozen() {
        var tickManager = Bukkit.getServerTickManager();
        return tickManager.isFrozen() && !tickManager.isStepping();
    }

    /** Where the ring floats: ahead of the caster's eyes, a little low. */
    private static Location orbSpot(Location eyeLocation) {
        Location eye = eyeLocation.clone();
        Location spot = eye.add(eye.getDirection().multiply(ORB_DISTANCE)).subtract(0, ORB_DROP, 0);
        // A display entity is also rotated by its own yaw/pitch, on top of its transformation. The eye
        // location carries the caster's look direction, so zero it or every model gets turned twice.
        spot.setYaw(0f);
        spot.setPitch(0f);
        return spot;
    }

    /** Two unit vectors perpendicular to the aim and to each other, for laying things out around it. */
    private static Vector[] axes(Vector aim) {
        Vector right = aim.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-6) right = new Vector(1, 0, 0); // looking straight up/down
        right.normalize();
        Vector up = right.clone().crossProduct(aim).normalize();
        return new Vector[]{right, up};
    }

    /** Points the model's tip (TIP_AXIS) along the given direction. Scale 0..1 shrinks it away. */
    private static Transformation pose(Vector direction, float scale) {
        return pose(direction, scale, 0f);
    }

    /** Same, but also shoves the model {@code push} blocks along the direction it points (used to sink it into a wall). */
    private static Transformation pose(Vector direction, float scale, float push) {
        Vector d = direction.clone().normalize();
        Quaternionf facing = new Quaternionf().rotationTo(TIP_AXIS,
                new Vector3f((float) d.getX(), (float) d.getY(), (float) d.getZ()));
        return new Transformation(
                new Vector3f((float) d.getX() * push, (float) d.getY() * push, (float) d.getZ() * push),
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
        return "Charge a ring of icicles, then fire them at what you're looking at. They damage and slow enemies. You're slowed while casting it.";
    }
}