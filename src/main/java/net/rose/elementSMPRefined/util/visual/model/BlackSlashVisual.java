package net.rose.elementSMPRefined.util.visual.model;

import net.rose.elementSMPRefined.items.builder.ModelItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A single black slash arc. The whole animation lives in one texture strip
 * ({@code black_slash.png} + its {@code .mcmeta}), so this is one model and one entity per slash.
 * The display removes itself once the animation has played through.
 */
public final class BlackSlashVisual {

    public static final NamespacedKey BLACK_SLASH =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:black_slash"));

    private static final int FRAME_COUNT = 7;
    private static final int TICKS_PER_FRAME = 2;
    private static final int LIFETIME_TICKS = FRAME_COUNT * TICKS_PER_FRAME + 1;
    private static final Vector3f MODEL_FORWARD = new Vector3f(0f, 0f, 1f);

    private static final Set<BlackSlashVisual> ACTIVE = new HashSet<>();

    private final ItemDisplay display;

    private BlackSlashVisual(Location at, Vector direction, float rollDegrees, float scale) {
        Vector3f dir = new Vector3f(
                (float) direction.getX(), (float) direction.getY(), (float) direction.getZ());
        if (dir.lengthSquared() < 1.0E-6f) {
            dir.set(MODEL_FORWARD);
        }
        dir.normalize();

        Quaternionf orientation = new Quaternionf().rotationTo(MODEL_FORWARD, dir);
        orientation = new Quaternionf().rotateAxis((float) Math.toRadians(rollDegrees), dir).mul(orientation);

        Transformation transformation = new Transformation(
                new Vector3f(),
                orientation,
                new Vector3f(scale),
                new Quaternionf());

        ItemStack modelItem = ModelItems.translucent(BLACK_SLASH);

        this.display = at.getWorld().spawn(
                new Location(at.getWorld(), at.getX(), at.getY(), at.getZ(), 0f, 0f), ItemDisplay.class, d -> {
                    d.setItemStack(modelItem);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    d.setBillboard(Display.Billboard.FIXED);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setTransformation(transformation);
                    d.setPersistent(false);
                    d.setInvulnerable(true);
                });
        ACTIVE.add(this);
    }

    /**
     * Plays one slash.
     *
     * @param at          center of the slash
     * @param direction   the way the slash faces (the arc's plane is perpendicular to this)
     * @param rollDegrees tilt of the arc around {@code direction}
     * @param scale       size multiplier (1 = about 2.4 blocks wide)
     */
    public static void play(JavaPlugin plugin, Location at, Vector direction, float rollDegrees, float scale) {
        if (at.getWorld() == null) return;
        BlackSlashVisual visual = new BlackSlashVisual(at, direction, rollDegrees, scale);
        Bukkit.getScheduler().runTaskLater(plugin, visual::remove, LIFETIME_TICKS);
    }

    public static void playLater(JavaPlugin plugin, long delayTicks, Location at, Vector direction,
                                 float rollDegrees, float scale) {
        Location frozen = at.clone();
        Vector facing = direction.clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> play(plugin, frozen, facing, rollDegrees, scale), delayTicks);
    }

    public void remove() {
        if (display.isValid()) {
            display.remove();
        }
        ACTIVE.remove(this);
    }

    public static void removeAll() {
        for (BlackSlashVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }
}
