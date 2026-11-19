package net.rose.elementSMPRefined.weapon.disc;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.item.builder.ModelItems;
import net.rose.elementSMPRefined.util.damage.AbilityDamage;
import net.rose.elementSMPRefined.util.damage.AbilityTargets;
import net.rose.elementSMPRefined.util.visual.PingCompensation;
import net.rose.elementevents.mythic.MythicBookProtection;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * One throw of the disc: out in a straight line, ricochet off walls, then home in on the thrower.
 * Port of the Fabric {@code ThrownDiscEntity}, with the same numbers.
 * <p>
 * Bukkit has no custom entities, so the disc is an {@link ItemDisplay} that this class moves every
 * tick. While it flies the real item is not in anyone's inventory; it is held here (and mirrored in
 * {@link DiscRegistry}) and is always handed back or dropped when the flight ends, whatever the
 * reason, because it is the only one that exists.
 */
final class ThrownDisc {
    private static final double WIDTH = 0.6;
    private static final double THROW_SPEED = 1.4;
    private static final double SPAWN_FORWARD = 0.5;
    private static final double BASE_RANGE = 5.0;
    private static final double RICOCHET_RANGE_BONUS = 2.0;
    private static final int MAX_RICOCHETS = 50;
    private static final double BOUNCE_SPEED_RETAIN = 0.6;
    private static final double MIN_BOUNCE_SPEED = 0.7;
    private static final int BOUNCE_COOLDOWN_TICKS = 3;
    private static final double RETURN_SPEED = 1.5;
    private static final double RETURN_TURN_RATE = 0.35;
    private static final double CATCH_DISTANCE = 1.4;
    private static final double HIT_MARGIN = 0.3;
    private static final int HIT_COOLDOWN_TICKS = 10;
    private static final int MAX_LIFETIME_TICKS = 200;
    private static final double FLIGHT_DIR_SMOOTHING = 0.5;

    private static final float SPIN_RADIANS_PER_TICK = (float) Math.toRadians(70.0);
    private static final double MAX_FLIGHT_PITCH = 75.0;
    /** The sprite is 14 of its 16 pixels wide, so scale it up until the disc is {@link #WIDTH}. */
    private static final float SCALE = (float) (WIDTH / (14.0 / 16.0));

    /** Blocks the disc never bounces off (it just stops and comes back). */
    private static final Set<Material> NO_RICOCHET =
            Set.of(
                    Material.SLIME_BLOCK,
                    Material.HONEY_BLOCK);

    private final ElementSMPRefined plugin;
    private final ThrowingDiscSystem system;
    private final UUID ownerId;
    private final DiscTier tier;
    private final World world;
    private final int homeSlot;
    private final Map<UUID, Integer> lastHitTick = new HashMap<>();

    private ItemStack stack;
    private ItemDisplay display;
    private BukkitTask task;

    private Vector pos;
    private Vector velocity;
    private Vector flightDir;
    private float spin;

    private boolean returning;
    private boolean finished;
    private int age;
    private int ricochets;
    private int lastBounceAge = -1000;
    private double rangeLeft = BASE_RANGE;

    private ThrownDisc(
            ThrowingDiscSystem system,
            Player owner,
            DiscTier tier,
            ItemStack stack,
            int homeSlot) {
        this.plugin = system.plugin();
        this.system = system;
        this.ownerId = owner.getUniqueId();
        this.tier = tier;
        this.stack = stack;
        this.homeSlot = homeSlot;
        this.world = owner.getWorld();
    }

    static ThrownDisc launch(
            ThrowingDiscSystem system, Player owner, DiscTier tier, ItemStack stack, int homeSlot) {
        ThrownDisc disc = new ThrownDisc(system, owner, tier, stack, homeSlot);
        disc.start(owner);
        return disc;
    }

    UUID ownerId() {
        return ownerId;
    }

    // ---- lifecycle --------------------------------------------------------------------------

    private void start(Player owner) {
        Location eye = owner.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Vector start = eye.toVector().add(look.clone().multiply(SPAWN_FORWARD));

        RayTraceResult obstruction =
                world.rayTraceBlocks(eye, look, SPAWN_FORWARD, FluidCollisionMode.NEVER, true);
        if (obstruction != null && obstruction.getHitBlock() != null) {
            start = obstruction.getHitPosition().subtract(look.clone().multiply(0.05));
        }

        this.pos = start;
        this.velocity = look.clone().multiply(THROW_SPEED);
        this.flightDir = look.clone();

        int smoothing = PingCompensation.smoothingTicks(owner, 2);
        this.display =
                world.spawn(
                        location(pos),
                        ItemDisplay.class,
                        d -> {
                            d.setItemStack(ModelItems.of(tier.model()));
                            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                            d.setBillboard(Display.Billboard.FIXED);
                            d.setBrightness(new Display.Brightness(15, 15));
                            d.setTeleportDuration(smoothing);
                            d.setInterpolationDuration(1);
                            d.setPersistent(false);
                            d.setInvulnerable(true);
                        });
        updateDisplay();

        world.playSound(
                owner.getLocation(), Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.0f, 1.0f);
        this.task = plugin.getTaskScheduler().runTimer(this::safeTick, 1L, 1L);
    }

    private void safeTick() {
        if (finished) return;
        try {
            tick();
        } catch (Throwable t) {
            // Whatever went wrong, the disc must not be lost.
            plugin.getLogger().log(Level.SEVERE, "Throwing disc tick failed", t);
            end();
        }
    }

    private void tick() {
        age++;
        spin = (spin + SPIN_RADIANS_PER_TICK) % (float) (Math.PI * 2.0);
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null
                || !owner.isOnline()
                || owner.isDead()
                || !owner.getWorld().equals(world)
                || age > MAX_LIFETIME_TICKS
                || !display.isValid()
                || !world.isChunkLoaded(pos.getBlockX() >> 4, pos.getBlockZ() >> 4)) {
            end();
            return;
        }
        if (returning) {
            tickReturn(owner);
        } else {
            tickOutbound(owner);
        }
    }

    // ---- outbound ---------------------------------------------------------------------------

    private void tickOutbound(Player owner) {
        double speed = velocity.length();
        if (speed < 1.0E-3) {
            beginReturn();
            return;
        }
        Vector from = pos.clone();
        Vector dir = velocity.clone().multiply(1.0 / speed);

        RayTraceResult hit =
                world.rayTraceBlocks(
                        location(from), dir, speed, FluidCollisionMode.NEVER, true);
        boolean blocked = hit != null && hit.getHitBlock() != null;
        Vector end = blocked ? hit.getHitPosition().clone() : from.clone().add(velocity);

        hitEntitiesAlong(owner, from, end);
        rangeLeft -= from.distance(end);
        moveTo(end);

        if (blocked) {
            onBlockHit(hit);
        } else if (rangeLeft <= 0.0) {
            beginReturn();
        }
    }

    private void onBlockHit(RayTraceResult hit) {
        world.playSound(
                location(pos), Sound.ITEM_TRIDENT_HIT_GROUND, SoundCategory.PLAYERS, 1.0f, 1.0f);
        Material type = hit.getHitBlock().getType();
        BlockFace face = hit.getHitBlockFace();
        if (face == null
                || isNoRicochet(type)
                || ricochets >= MAX_RICOCHETS
                || age - lastBounceAge < BOUNCE_COOLDOWN_TICKS) {
            velocity = new Vector();
            beginReturn();
            return;
        }
        lastBounceAge = age;
        Vector normal = face.getDirection();
        Vector reflected =
                velocity.clone().subtract(normal.clone().multiply(2.0 * velocity.dot(normal)));
        reflected.multiply(BOUNCE_SPEED_RETAIN);
        if (reflected.length() < MIN_BOUNCE_SPEED) {
            reflected = reflected.normalize().multiply(MIN_BOUNCE_SPEED);
        }
        velocity = reflected;
        moveTo(pos.clone().add(normal.multiply(0.1)));
        ricochets++;
        rangeLeft += RICOCHET_RANGE_BONUS;

        world.spawnParticle(Particle.CRIT, location(pos), 4, 0.05, 0.05, 0.05, 0.15);
    }

    private static boolean isNoRicochet(Material type) {
        return NO_RICOCHET.contains(type)
                || Tag.WOOL.isTagged(type)
                || Tag.WOOL_CARPETS.isTagged(type)
                || Tag.BEDS.isTagged(type);
    }

    private void beginReturn() {
        returning = true;
        lastHitTick.clear();
        world.playSound(
                location(pos), Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 0.3f, 1.8f);
    }

    // ---- return -----------------------------------------------------------------------------

    private void tickReturn(Player owner) {
        Vector target =
                new Vector(
                        owner.getLocation().getX(),
                        owner.getLocation().getY() + owner.getHeight() * 0.6,
                        owner.getLocation().getZ());
        Vector toTarget = target.clone().subtract(pos);
        double distance = toTarget.length();
        if (distance <= CATCH_DISTANCE) {
            world.playSound(
                    location(pos), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.5f);
            end();
            return;
        }
        Vector desired = toTarget.multiply(RETURN_SPEED / distance);
        velocity = velocity.clone().add(desired.subtract(velocity).multiply(RETURN_TURN_RATE));

        Vector from = pos.clone();
        Vector end = velocity.length() >= distance ? target : from.clone().add(velocity);
        hitEntitiesAlong(owner, from, end);
        moveTo(end);
    }

    // ---- hitting ----------------------------------------------------------------------------

    private void hitEntitiesAlong(Player owner, Vector from, Vector to) {
        Vector segment = to.clone().subtract(from);
        double length = segment.length();
        BoundingBox sweep = BoundingBox.of(from, to).expand(HIT_MARGIN);

        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(sweep)) {
            if (entity instanceof LivingEntity living
                    && AbilityTargets.isValid(owner, living, plugin.getTeamManager())) {
                candidates.add(living);
            }
        }
        candidates.sort(
                Comparator.comparingDouble(e -> e.getLocation().toVector().distanceSquared(from)));

        for (LivingEntity target : candidates) {
            BoundingBox box = target.getBoundingBox().expand(HIT_MARGIN);
            boolean inside = box.contains(from);
            if (!inside
                    && (length < 1.0E-6
                            || box.rayTrace(from, segment.clone().multiply(1.0 / length), length)
                                    == null)) {
                continue;
            }
            Integer last = lastHitTick.get(target.getUniqueId());
            if (last != null && age - last < HIT_COOLDOWN_TICKS) continue;
            damage(owner, target);
        }
    }

    private void damage(Player owner, LivingEntity target) {
        lastHitTick.put(target.getUniqueId(), age);
        AbilityDamage.deal(target, "Throwing Disc", () -> target.damage(tier.damage(), owner));
        world.playSound(
                target.getLocation(), Sound.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    // ---- display ----------------------------------------------------------------------------

    private void moveTo(Vector next) {
        pos = next;
        // Smooth the facing so a bounce doesn't snap the tilt.
        Vector target =
                velocity.lengthSquared() > 1.0E-6 ? velocity.clone().normalize() : flightDir;
        Vector blended =
                flightDir
                        .clone()
                        .add(target.clone().subtract(flightDir).multiply(FLIGHT_DIR_SMOOTHING));
        if (blended.lengthSquared() > 1.0E-6) flightDir = blended.normalize();
        updateDisplay();
    }

    private void updateDisplay() {
        Quaternionf left = new Quaternionf();
        double horizontal = Math.hypot(flightDir.getX(), flightDir.getZ());
        if (horizontal > 1.0E-3) {
            float hx = (float) (flightDir.getX() / horizontal);
            float hz = (float) (flightDir.getZ() / horizontal);
            double pitch = Math.toDegrees(Math.atan2(flightDir.getY(), horizontal));
            pitch = Math.max(-MAX_FLIGHT_PITCH, Math.min(MAX_FLIGHT_PITCH, pitch));
            left.rotationAxis((float) Math.toRadians(-pitch), hz, 0f, -hx);
        }
        left.mul(new Quaternionf().rotationY(spin));
        // Lies the sprite flat before it is spun and tilted.
        Quaternionf right = new Quaternionf().rotationX((float) Math.toRadians(90.0));

        display.setTransformation(
                new Transformation(new Vector3f(), left, new Vector3f(SCALE), right));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.teleport(location(pos));
    }

    private Location location(Vector at) {
        return new Location(world, at.getX(), at.getY(), at.getZ(), 0f, 0f);
    }

    // ---- ending -----------------------------------------------------------------------------

    /**
     * Ends the flight right now and gives the disc back: into the thrower's inventory (their old
     * slot if it is free), dropped at their feet if that is full, dropped where the disc is if the
     * thrower is dead, or held for their next join if they are offline.
     */
    void end() {
        if (finished) return;
        finished = true;
        if (task != null) task.cancel();
        if (display != null && display.isValid()) display.remove();
        system.flightEnded(this);

        ItemStack disc = stack;
        stack = null;
        if (disc == null) return;

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            system.registry().queuePending(ownerId, disc);
        } else if (owner.isDead()) {
            drop(location(pos), disc);
        } else {
            giveBack(owner, disc);
        }
        system.registry().clearInFlight();
    }

    private void giveBack(Player owner, ItemStack disc) {
        PlayerInventory inventory = owner.getInventory();
        ItemStack occupant = inventory.getItem(homeSlot);
        if (occupant == null || occupant.isEmpty()) {
            inventory.setItem(homeSlot, disc);
            return;
        }
        for (ItemStack leftover : inventory.addItem(disc).values()) {
            drop(owner.getLocation().add(0, 0.5, 0), leftover);
        }
    }

    private static void drop(Location at, ItemStack disc) {
        at.getWorld()
                .dropItem(
                        at,
                        disc,
                        (Item item) -> {
                            item.setVelocity(new Vector());
                            MythicBookProtection.protect(item);
                        });
    }

    /** Takes one durability off the disc for a throw, but never lets the only disc break. */
    static void wear(ItemStack disc) {
        if (!(disc.getItemMeta() instanceof Damageable meta)) return;
        int max = disc.getType().getMaxDurability();
        meta.setDamage(Math.min(meta.getDamage() + 1, Math.max(0, max - 1)));
        disc.setItemMeta(meta);
    }
}
