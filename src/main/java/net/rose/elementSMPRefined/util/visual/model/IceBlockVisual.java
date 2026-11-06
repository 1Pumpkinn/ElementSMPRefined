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

public final class IceBlockVisual {

    private static final double PADDING = 0.3;

    private static final Set<IceBlockVisual> ACTIVE = new HashSet<>();

    private final BlockDisplay display;
    private final BlockDisplay inside;
    private final LivingEntity target;
    private boolean removed = false;

    private IceBlockVisual(JavaPlugin plugin, LivingEntity target, int ticks) {
        this.target = target;

        BoundingBox box = target.getBoundingBox();
        float width = (float) (Math.max(box.getWidthX(), box.getWidthZ()) + PADDING);
        float height = (float) (box.getHeight() + PADDING);

        Location feet = fixedRotation(target.getLocation());
        // A block display's origin is the block's corner, so shift it back by half its width to
        // centre it on the target, and down by half the padding so it surrounds the feet too.
        this.display =
                spawnBox(
                        feet,
                        new Transformation(
                                new Vector3f(-width / 2f, (float) (-PADDING / 2.0), -width / 2f),
                                new Quaternionf(),
                                new Vector3f(width, height, width),
                                new Quaternionf()));

        // Blocks are only drawn from the outside, so the frozen player's own camera (inside the
        // box)
        // would see nothing. A second, slightly smaller box with negative scale is turned inside
        // out,
        // so its faces point inwards and are drawn from the inside. With a negative scale the box
        // extends the other way from its origin, so the translation is flipped to land on the same
        // spot.
        float innerWidth = width - 0.02f;
        float innerHeight = height - 0.02f;
        this.inside =
                spawnBox(
                        feet,
                        new Transformation(
                                new Vector3f(
                                        innerWidth / 2f,
                                        innerHeight - (float) (PADDING / 2.0) + 0.01f,
                                        innerWidth / 2f),
                                new Quaternionf(),
                                new Vector3f(-innerWidth, -innerHeight, -innerWidth),
                                new Quaternionf()));
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
                Location at = fixedRotation(target.getLocation());
                display.teleport(at);
                inside.teleport(at);
                age++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static BlockDisplay spawnBox(Location at, Transformation transformation) {
        return at.getWorld()
                .spawn(
                        at,
                        BlockDisplay.class,
                        d -> {
                            d.setBlock(Material.ICE.createBlockData());
                            d.setBrightness(new Display.Brightness(15, 15));
                            d.setTransformation(transformation);
                            d.setBillboard(Display.Billboard.FIXED);
                            d.setTeleportDuration(1);
                            d.setPersistent(false);
                            d.setInvulnerable(true);
                        });
    }

    private static Location fixedRotation(Location loc) {
        Location copy = loc.clone();
        copy.setYaw(0f);
        copy.setPitch(0f);
        return copy;
    }

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

    public void remove() {
        removed = true;
        if (display.isValid()) display.remove();
        if (inside.isValid()) inside.remove();
        ACTIVE.remove(this);
    }

    public static void removeAll() {
        for (IceBlockVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }
}
