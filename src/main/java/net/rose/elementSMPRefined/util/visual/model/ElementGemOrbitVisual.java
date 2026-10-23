package net.rose.elementSMPRefined.util.visual.model;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.items.builder.ModelItems;
import net.rose.elementSMPRefined.util.visual.PingCompensation;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * The reroll animation drawn with element gems ({@code elementsmp:<element>_gem} resource-pack
 * items): a ring of gems spins around the player, they get knocked out one by one, and the one
 * gem left - always the element the player actually rolled - glides to a stop in front of them.
 * <p>
 * Phases, driven by {@link #tick()} (call once per server tick):
 * <ol>
 *   <li>{@link Phase#SPIN} - the whole ring orbits, speeding up.</li>
 *   <li>{@link Phase#ELIMINATE} - every few ticks a random non-chosen gem pops, until one is left.</li>
 *   <li>{@link Phase#REVEAL} - the survivor glides from its orbit slot to a point in front of the player.</li>
 *   <li>{@link Phase#HOLD} - it hovers there, grown, so the player can read what they got.</li>
 *   <li>{@link Phase#DONE} - it bursts and everything is removed.</li>
 * </ol>
 * Always call {@link #remove()} if the roll is aborted (or {@link #removeAll()} on plugin disable
 * as a safety net) - gems are real entities and will otherwise hang around.
 */
public final class ElementGemOrbitVisual {

    public enum Phase { SPIN, ELIMINATE, REVEAL, HOLD, DONE }

    private static final float ORBIT_SCALE = 0.55f;     // gem texture fills a 1x1 block at scale 1
    private static final float REVEAL_SCALE = 1.1f;

    private static final double RADIUS = 1.5;
    private static final double HEIGHT = 1.0;           // gem centre above the player's feet
    private static final double BOB = 0.06;
    private static final double FRONT_DISTANCE = 1.8;   // how far ahead the chosen gem stops
    private static final double FRONT_HEIGHT = 1.35;    // roughly eye height

    /** Furthest the ring may be led ahead of the player to hide high ping, in blocks. */
    private static final double MAX_LEAD_BLOCKS = 2.5;

    private static final double SPIN_START_DEG = 6.0;   // degrees per tick
    private static final double SPIN_MAX_DEG = 22.0;

    /** Every live visual, so plugin disable can sweep up anything still on screen. */
    private static final Set<ElementGemOrbitVisual> ACTIVE = new HashSet<>();

    private record Gem(ItemDisplay display, double slotDegrees, int index, ElementType type) {}

    private final Player owner;
    private final Random random = new Random();
    private final List<Gem> gems = new ArrayList<>();
    private final Gem survivor;
    private final int initialCount;
    private final PingCompensation.Tracker tracker;
    private int smoothing;              // teleport window currently set on the gems, in ticks

    private Phase phase = Phase.SPIN;
    private int tick = 0;
    private int phaseTick = 0;
    private double angle = 0;           // current ring rotation, degrees
    private Location revealFrom = null; // survivor position when the glide started

    /**
     * @param ring   every element that appears on the ring (the chosen one must be among them)
     * @param chosen the element the player rolled - this gem is the one that survives
     */
    public ElementGemOrbitVisual(Player owner, ElementType[] ring, ElementType chosen) {
        this.owner = owner;
        this.tracker = new PingCompensation.Tracker(owner, MAX_LEAD_BLOCKS);
        this.smoothing = PingCompensation.smoothingTicks(owner);

        List<ElementType> types = new ArrayList<>();
        for (int pass = 0; pass < Constants.Animation.ORBIT_GEMS_PER_ELEMENT; pass++) {
            for (ElementType t : ring) types.add(t);
        }

        Collections.shuffle(types, random);
        if (!types.contains(chosen)) types.set(0, chosen);

        Location base = owner.getLocation();
        Gem pick = null;
        for (int i = 0; i < types.size(); i++) {
            ElementType type = types.get(i);
            double slot = i * (360.0 / types.size());
            Gem gem = new Gem(spawn(base, type, slot, i), slot, i, type);
            gems.add(gem);
            if (pick == null && type == chosen) pick = gem;
        }
        this.survivor = pick;
        this.initialCount = gems.size();
        ACTIVE.add(this);
    }

    public Phase phase() {
        return phase;
    }

    /** True from the moment the survivor has landed in front of the player. */
    public boolean hasRevealed() {
        return phase == Phase.HOLD || phase == Phase.DONE;
    }

    public boolean isDone() {
        return phase == Phase.DONE;
    }

    public void tick() {
        if (phase == Phase.DONE) return;
        if (!owner.isOnline()) {
            remove();
            return;
        }

        // Centre on where the player will be on THEIR screen when this update lands, and give
        // high-ping players a longer interpolation window to ride out packet jitter.
        int wanted = PingCompensation.smoothingTicks(owner);
        Location base = tracker.sample(wanted);
        if (!isLoaded(base)) return;
        applySmoothing(wanted);

        tick++;
        phaseTick++;

        switch (phase) {
            case SPIN -> {
                double t = Math.min(1.0, phaseTick / (double) Constants.Animation.ORBIT_SPIN_TICKS);
                angle += SPIN_START_DEG + (SPIN_MAX_DEG - SPIN_START_DEG) * t;
                orbitAll(base);
                if (phaseTick >= Constants.Animation.ORBIT_SPIN_TICKS) enter(Phase.ELIMINATE);
            }
            case ELIMINATE -> {
                angle += SPIN_MAX_DEG;
                orbitAll(base);
                if (phaseTick % Constants.Animation.ORBIT_ELIMINATE_INTERVAL_TICKS == 0) popRandomNonSurvivor();
                if (gems.size() == 1) {
                    revealFrom = survivor.display().getLocation();
                    enter(Phase.REVEAL);
                    growSurvivor();
                }
            }
            case REVEAL -> {
                double t = Math.min(1.0, phaseTick / (double) Constants.Animation.ORBIT_GLIDE_TICKS);
                double eased = 1 - Math.pow(1 - t, 3); // ease-out: fast at first, settles gently
                Location target = front(base, tracker.yaw(), tick);
                survivor.display().teleport(lerp(revealFrom, target, eased));
                if (t >= 1.0) {
                    base.getWorld().playSound(target, Sound.BLOCK_AMETHYST_BLOCK_RESONATE,
                            SoundCategory.PLAYERS, 1.0f, 1.4f);
                    base.getWorld().spawnParticle(Particle.END_ROD, target, 14, 0.25, 0.25, 0.25, 0.02);
                    enter(Phase.HOLD);
                }
            }
            case HOLD -> {
                survivor.display().teleport(front(base, tracker.yaw(), tick));
                if (tick % 4 == 0) {
                    base.getWorld().spawnParticle(Particle.END_ROD, survivor.display().getLocation(),
                            1, 0.2, 0.3, 0.2, 0.0);
                }
                if (phaseTick >= Constants.Animation.ORBIT_HOLD_TICKS) {
                    Location at = survivor.display().getLocation();
                    at.getWorld().spawnParticle(Particle.POOF, at, 12, 0.2, 0.2, 0.2, 0.05);
                    at.getWorld().playSound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE,
                            SoundCategory.PLAYERS, 0.6f, 1.6f);
                    remove();
                }
            }
            case DONE -> { }
        }
    }

    /** Removes every gem and unregisters. Safe to call more than once. */
    public void remove() {
        for (Gem g : gems) {
            if (g.display().isValid()) g.display().remove();
        }
        gems.clear();
        ACTIVE.remove(this);
        phase = Phase.DONE;
    }

    /** Safety net for plugin disable - removes every gem still on screen. */
    public static void removeAll() {
        for (ElementGemOrbitVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    /** Re-applies the teleport window to every gem, but only when the player's ping tier changed. */
    private void applySmoothing(int ticks) {
        if (ticks == smoothing) return;
        smoothing = ticks;
        for (Gem g : gems) {
            if (g.display().isValid()) g.display().setTeleportDuration(ticks);
        }
    }

    private void enter(Phase next) {
        phase = next;
        phaseTick = 0;
    }

    private void orbitAll(Location base) {
        for (Gem g : gems) {
            if (g.display().isValid()) {
                g.display().teleport(place(base, g.slotDegrees() + angle, tick, g.index()));
            }
        }
    }

    private void popRandomNonSurvivor() {
        List<Gem> candidates = new ArrayList<>(gems);
        candidates.remove(survivor);
        if (candidates.isEmpty()) return;

        Gem gone = candidates.get(random.nextInt(candidates.size()));
        Location at = gone.display().getLocation();
        if (gone.display().isValid()) gone.display().remove();
        gems.remove(gone);

        World w = at.getWorld();
        w.spawnParticle(Particle.POOF, at, 8, 0.15, 0.15, 0.15, 0.03);
        // Pitch climbs as the ring thins out, so the eliminations build tension.
        float pitch = 0.8f + 0.12f * (initialCount - gems.size());
        w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 0.8f, Math.min(pitch, 2.0f));
    }

    private void growSurvivor() {
        ItemDisplay d = survivor.display();
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Constants.Animation.ORBIT_GLIDE_TICKS);
        d.setTransformation(new Transformation(
                new Vector3f(), new Quaternionf(), new Vector3f(REVEAL_SCALE), new Quaternionf()));
    }

    private ItemDisplay spawn(Location base, ElementType type, double slot, int index) {
        NamespacedKey model = Objects.requireNonNull(
                NamespacedKey.fromString("elementsmp:" + type.name().toLowerCase() + "_gem"));
        // The base material is irrelevant - the item_model component replaces its model entirely.
        ItemStack stack = ModelItems.of(model);

        return base.getWorld().spawn(place(base, slot, 0, index), ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            // The gem art is a flat sprite - keep it facing whoever is looking at it.
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(), new Vector3f(ORBIT_SCALE), new Quaternionf()));
            d.setTeleportDuration(smoothing);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
    }

    /** A point on the ring around the player. */
    private static Location place(Location owner, double angleDegrees, int tick, int index) {
        double a = Math.toRadians(angleDegrees);
        double bob = Math.sin(tick * 0.15 + index * 2.1) * BOB;
        return new Location(owner.getWorld(),
                owner.getX() + Math.cos(a) * RADIUS,
                owner.getY() + HEIGHT + bob,
                owner.getZ() + Math.sin(a) * RADIUS);
    }

    /** The spot straight ahead of where the player is looking (horizontally), for the given (led) yaw. */
    private static Location front(Location owner, float yawDegrees, int tick) {
        double yaw = Math.toRadians(yawDegrees);
        double dx = -Math.sin(yaw);
        double dz = Math.cos(yaw);
        double bob = Math.sin(tick * 0.12) * 0.04;
        return new Location(owner.getWorld(),
                owner.getX() + dx * FRONT_DISTANCE,
                owner.getY() + FRONT_HEIGHT + bob,
                owner.getZ() + dz * FRONT_DISTANCE);
    }

    private static Location lerp(Location a, Location b, double t) {
        return new Location(b.getWorld(),
                a.getX() + (b.getX() - a.getX()) * t,
                a.getY() + (b.getY() - a.getY()) * t,
                a.getZ() + (b.getZ() - a.getZ()) * t);
    }

    /** Teleporting into an unloaded chunk would force a synchronous load - just skip that step instead. */
    private static boolean isLoaded(Location at) {
        return at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }
}
