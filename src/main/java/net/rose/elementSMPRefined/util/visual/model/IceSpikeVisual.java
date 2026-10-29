package net.rose.elementSMPRefined.util.visual.model;

import net.rose.elementSMPRefined.items.builder.ModelItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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

public final class IceSpikeVisual {

    private static final NamespacedKey MODEL = Objects.requireNonNull(NamespacedKey.fromString("elementsmp:icicle_tip"));

    private static final double SPRITE_FILL = 13.0 / 16.0;

    private static final double WIDTH_RATIO = 0.6;

    private static final double EMBED = 0.08;

    private static final int GROW_TICKS = 3;
    private static final int HOLD_TICKS = 40;
    private static final int SHRINK_TICKS = 6;

    // Ages (in ticks since the spike spawned) at which each stage starts.
    private static final int GROW_AT = 1;
    private static final int SHRINK_AT = GROW_AT + GROW_TICKS + HOLD_TICKS;
    private static final int REMOVE_AT = SHRINK_AT + SHRINK_TICKS + 2;

    private static final float COLLAPSED = 0.05f;

    private static final org.bukkit.block.data.BlockData PACKED_ICE = Material.PACKED_ICE.createBlockData();
    private static final org.bukkit.block.data.BlockData ICE = Material.ICE.createBlockData();

    private static final List<Spike> ACTIVE = new ArrayList<>();
    private static BukkitTask ticker;

    private IceSpikeVisual() {}

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
        Location fx = new Location(world, base.getX(), base.getY() + 0.1, base.getZ());
        world.spawnParticle(Particle.BLOCK, fx, 6, 0.3, 0.1, 0.3, 0.0,
                PACKED_ICE, true);
        fx.setY(base.getY() + 0.5);
        world.spawnParticle(Particle.SNOWFLAKE, fx, 6, 0.3, 0.4, 0.3, 0.02, null, true);
    }

    private static void ensureTicker(JavaPlugin plugin) {
        if (ticker != null) return;
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, IceSpikeVisual::tickAll, 1L, 1L);
    }

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
                s.world.spawnParticle(Particle.BLOCK, new Location(s.world, s.baseX, s.baseY + 0.5, s.baseZ),
                        10, 0.4, 0.5, 0.4, 0.0, ICE, true);
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
