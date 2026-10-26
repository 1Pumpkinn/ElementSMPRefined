package net.rose.elementSMPRefined.util.visual.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * A block of ice encasing a player or mob, drawn with a vanilla {@link BlockDisplay} (no resource
 * pack needed). It is sized from the target's bounding box, follows the target every tick while it
 * lasts, and shatters (removes itself with a glass-break sound) when the time is up or the target
 * dies / leaves.
 * <p>
 * Always keep the returned handle's lifetime short and call {@link #removeAll()} on plugin disable
 * as a safety net - the displays are real entities.
 */
public final class IceBlockVisual {

    /** Extra room around the target's hitbox so the ice reads as a block around them, not a skin on them. */
    private static final double PADDING = 0.3;

    /** Every live block, so plugin disable can sweep up anything still on screen. */
    private static final Set<IceBlockVisual> ACTIVE = new HashSet<>();

    private final BlockDisplay display;
    private final LivingEntity target;
    private boolean removed = false;

    private IceBlockVisual(JavaPlugin plugin, LivingEntity target, int ticks) {
        this.target = target;

        BoundingBox box = target.getBoundingBox();
        float width = (float) (Math.max(box.getWidthX(), box.getWidthZ()) + PADDING);
        float height = (float) (box.getHeight() + PADDING);

        Location feet = target.getLocation();
        this.display = feet.getWorld().spawn(feet, BlockDisplay.class, d -> {
            d.setBlock(Material.ICE.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            // A block display's origin is the block's corner, so shift it back by half its width to
            // centre it on the target, and down by half the padding so it surrounds the feet too.
            d.setTransformation(new Transformation(
                    new Vector3f(-width / 2f, (float) (-PADDING / 2.0), -width / 2f),
                    new Quaternionf(),
                    new Vector3f(width, height, width),
                    new Quaternionf()));
            d.setTeleportDuration(1);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
        ACTIVE.add(this);

        target.getWorld().playSound(feet, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 1.0f);

        new BukkitRunnable() {
            int age = 0;

            @Override
            public void run() {
                if (removed) {
                    cancel();
                    return;
                }
                if (!target.isValid() || target.isDead() || age >= ticks) {
                    shatter();
                    cancel();
                    return;
                }
                display.teleport(target.getLocation());
                age++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Encases {@code target} in ice for {@code ticks} ticks. */
    public static IceBlockVisual encase(JavaPlugin plugin, LivingEntity target, int ticks) {
        return new IceBlockVisual(plugin, target, ticks);
    }

    private void shatter() {
        if (removed) return;
        if (target.isValid()) {
            World world = target.getWorld();
            world.playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.2f);
        }
        remove();
    }

    /** Removes the display straight away, no sound. */
    public void remove() {
        removed = true;
        if (display.isValid()) display.remove();
        ACTIVE.remove(this);
    }

    /** Safety net for plugin disable - removes every ice block still on screen. */
    public static void removeAll() {
        for (IceBlockVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }
}
