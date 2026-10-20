package net.rose.elementSMPRefined.util.visual.model;

import net.rose.elementSMPRefined.particles.FrostParticles;
import net.rose.elementSMPRefined.items.builder.ModelItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One Frost ice spike, drawn with the SAME resource-pack model as Icicle Barrage
 * ({@code elementsmp:icicle_tip}, the pointed-dripstone shape wearing the icicle texture) shown by an
 * {@link ItemDisplay}.
 * <p>
 * That model's sprite hangs point-DOWN (wide end at the top of the model, point 5 of 16 units below its
 * centre), so for a spike standing on the ground it is flipped upside down: wide end in the floor, point
 * in the air. Each spike gets a random facing and a little width jitter so a ring of them never looks
 * copy-pasted.
 * <p>
 * The whole life cycle lives in here: the spike bursts up out of the ground, holds, sinks back and
 * removes itself. Growth is a client-side interpolated transformation (same approach as
 * {@link TornadoVisual}), planted by the BASE of the model - position it at the ground contact point and
 * the visual works out where the display's centre goes.
 * <p>
 * Performance: every live spike is advanced by ONE shared repeating task (started with the first
 * spike, cancelled when the last one is gone) instead of three scheduled tasks per spike - a single
 * Ice Spikes cast spawns ~40 of them. The model item is cached ({@link ModelItems}) rather than
 * rebuilt for each entity.
 * <p>
 * Spikes remove themselves, but call {@link #removeAll()} on plugin disable as a safety net.
 */
public final class IceSpikeVisual {

    /** Same model Icicle Barrage fires. */
    private static final NamespacedKey MODEL = Objects.requireNonNull(NamespacedKey.fromString("elementsmp:icicle_tip"));

    /**
     * How much of the model's height the sprite actually fills: the point sits at pixel 3 and the wide end
     * at pixel 16, so 13 of 16 units. Used to turn a wanted visible height into a model scale.
     */
    private static final double SPRITE_FILL = 13.0 / 16.0;

    /** Width of a spike as a fraction of its visible height. */
    private static final double WIDTH_RATIO = 0.6;

    /** How far the wide end is sunk into the ground so there is never a gap under it, in blocks. */
    private static final double EMBED = 0.08;

    private static final int GROW_TICKS = 3;
    private static final int HOLD_TICKS = 40;
    private static final int SHRINK_TICKS = 6;

    // Ages (in ticks since the spike spawned) at which each stage starts.
    private static final int GROW_AT = 1;
    private static final int SHRINK_AT = GROW_AT + GROW_TICKS + HOLD_TICKS;
    private static final int REMOVE_AT = SHRINK_AT + SHRINK_TICKS + 2;

    /** Never scale to exactly zero - a degenerate transformation can render glitchy. */
    private static final float COLLAPSED = 0.05f;


    /** Every live spike, advanced by {@link #tickAll()}. */
    private static final List<Spike> ACTIVE = new ArrayList<>();
    private static BukkitTask ticker;

    private IceSpikeVisual() {}

    /** One spike's state. The pose numbers are fixed at spawn; only the growth changes. */
    private static final class Spike {
        final ItemDisplay display;
        final World world;
        final double baseX, baseY, baseZ;
        final float width, height, yaw;
        final int spawnTick;
        int stage = 0; // 0 = just spawned, 1 = growing/held, 2 = shrinking

        Spike(ItemDisplay display, Location base, float width, float height, float yaw) {
            this.display = display;
            this.world = base.getWorld();
            this.baseX = base.getX();
            this.baseY = base.getY();
            this.baseZ = base.getZ();
            this.width = width;
            this.height = height;
            this.yaw = yaw;
            this.spawnTick = Bukkit.getCurrentTick();
        }
    }

    /**
     * Spawns a spike and runs its whole life cycle.
     *
     * @param base   where the spike's bottom touches the ground
     * @param height how tall the visible spike should be, in blocks (ground to point)
     */
    public static void spawn(JavaPlugin plugin, Location base, double height) {
        World world = base.getWorld();
        if (world == null) return;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // Model-height scale so the visible sprite (13/16 of the model) comes out `height` blocks tall.
        float sy = (float) (height / SPRITE_FILL);
        float sw = (float) (height * WIDTH_RATIO * (0.9 + rnd.nextDouble() * 0.2));
        float yaw = (float) (rnd.nextDouble() * Math.PI * 2);

        // The model's centre sits on the display and its wide end is half a (scaled) model below that,
        // so the display goes half a model above the ground (less the small embed).
        Location centre = new Location(world, base.getX(), base.getY() + 0.5 * sy - EMBED, base.getZ(), 0f, 0f);

        // The base material is irrelevant - the item_model component replaces its model entirely.
        // Shared, cached stack: setItemStack copies it, so it is never mutated.
        ItemStack modelItem = ModelItems.of(MODEL);
        Transformation collapsed = pose(sw, sy, yaw, COLLAPSED);

        ItemDisplay display = world.spawn(centre, ItemDisplay.class, d -> {
            d.setItemStack(modelItem);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(collapsed);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });

        ACTIVE.add(new Spike(display, base, sw, sy, yaw));
        ensureTicker(plugin);

        // One reusable Location for both particle bursts.
        FrostParticles.iceSpikeRise(world, base.getX(), base.getY(), base.getZ());
    }

    private static void ensureTicker(JavaPlugin plugin) {
        if (ticker != null) return;
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, IceSpikeVisual::tickAll, 1L, 1L);
    }

    /** Advances every live spike one step; stops the shared task once none are left. */
    private static void tickAll() {
        int now = Bukkit.getCurrentTick();

        for (Iterator<Spike> it = ACTIVE.iterator(); it.hasNext(); ) {
            Spike s = it.next();

            if (!s.display.isValid()) { // killed or unloaded: it is simply gone
                it.remove();
                continue;
            }

            int age = now - s.spawnTick;

            if (age >= REMOVE_AT) {
                s.display.remove();
                it.remove();
                FrostParticles.iceSpikeShatter(s.world, s.baseX, s.baseY, s.baseZ);
            } else if (age >= SHRINK_AT) {
                if (s.stage < 2) {
                    s.stage = 2;
                    animate(s, SHRINK_TICKS, COLLAPSED);
                }
            } else if (age >= GROW_AT) {
                // Interpolation only animates if the transformation changes after the entity has been
                // sent to clients, so the growth starts a tick after the spawn.
                if (s.stage < 1) {
                    s.stage = 1;
                    animate(s, GROW_TICKS, 1.0f);
                }
            }
        }

        if (ACTIVE.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private static void animate(Spike s, int durationTicks, float growth) {
        s.display.setInterpolationDelay(0);
        s.display.setInterpolationDuration(durationTicks);
        s.display.setTransformation(pose(s.width, s.height, s.yaw, growth));
    }

    /** Safety net for plugin disable - removes every spike still on screen. */
    public static void removeAll() {
        for (Spike s : ACTIVE) {
            if (s.display.isValid()) s.display.remove();
        }
        ACTIVE.clear();
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    /**
     * Pose at a given growth. The model is flipped upside down (point up) and spun by {@code yaw}. Scaling
     * happens around the model's centre, so the display is dropped by however much the spike shrank to
     * keep its base planted on the ground. Width eases in more slowly than height so a half-grown spike
     * reads as rising, not as a squashed disc.
     *
     * @param growth 1.0 = fully grown, {@link #COLLAPSED} = sunk into the ground
     */
    private static Transformation pose(float width, float height, float yaw, float growth) {
        float w = width * (0.5f + 0.5f * growth);
        float h = height * growth;
        float lift = -0.5f * height * (1.0f - growth);
        // Flip first (point down -> point up), then spin around the vertical axis.
        Quaternionf rotation = new Quaternionf().rotateY(yaw).rotateX((float) Math.PI);
        return new Transformation(
                new Vector3f(0f, lift, 0f),
                rotation,
                new Vector3f(w, h, w),
                new Quaternionf());
    }
}
