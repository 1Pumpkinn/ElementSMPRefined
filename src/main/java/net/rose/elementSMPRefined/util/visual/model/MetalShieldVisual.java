package net.rose.elementSMPRefined.util.visual.model;

import net.rose.elementSMPRefined.util.scan.GroundScan;
import net.rose.elementSMPRefined.items.builder.ModelItems;
import net.rose.elementSMPRefined.util.visual.PingCompensation;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MetalShieldVisual {

    public static final NamespacedKey METAL_SHIELD =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:metal_shield"));

    private static final float SCALE = 1.0f;

    private static final double RADIUS = 1.4;           // distance from the owner
    private static final double HEIGHT = 0.85;          // shield centre above the owner's feet
    private static final double BOB = 0.04;             // gentle vertical drift
    private static final double SPIN_DEGREES_PER_TICK = 6.0; // one lap every 3 seconds
    private static final double MAX_LEAD_BLOCKS = 2.5;

    private static final Set<MetalShieldVisual> ACTIVE = new HashSet<>();

    private record Orbiter(ItemDisplay display, double slotDegrees, int index) {}

    private final List<Orbiter> orbiters = new ArrayList<>();
    private final Player owner;
    private final PingCompensation.Tracker tracker;
    private int smoothing; // teleport window currently set on the shields, in ticks

    public MetalShieldVisual(Player owner, int count) {
        this.owner = owner;
        this.tracker = new PingCompensation.Tracker(owner, MAX_LEAD_BLOCKS);
        this.smoothing = PingCompensation.smoothingTicks(owner);
        Location start = owner.getLocation();

        // The base material is irrelevant - the item_model component replaces its model entirely.
        ItemStack modelItem = ModelItems.of(METAL_SHIELD);

        for (int i = 0; i < count; i++) {
            double slot = i * (360.0 / count);
            ItemDisplay display = start.getWorld().spawn(place(start, slot, 0, i), ItemDisplay.class, d -> {
                d.setItemStack(modelItem);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(new Transformation(
                        new Vector3f(), new Quaternionf(), new Vector3f(SCALE), new Quaternionf()));
                d.setTeleportDuration(smoothing);
                d.setPersistent(false);
                d.setInvulnerable(true);
            });
            orbiters.add(new Orbiter(display, slot, i));
        }
        ACTIVE.add(this);
    }

    public void update(int tick) {
        int wanted = PingCompensation.smoothingTicks(owner);
        Location centre = tracker.sample(wanted);
        if (!GroundScan.isLoaded(centre)) return;

        if (wanted != smoothing) {
            smoothing = wanted;
            for (Orbiter o : orbiters) {
                if (o.display().isValid()) o.display().setTeleportDuration(wanted);
            }
        }

        double spin = tick * SPIN_DEGREES_PER_TICK;
        for (Orbiter o : orbiters) {
            if (o.display().isValid()) {
                o.display().teleport(place(centre, o.slotDegrees() + spin, tick, o.index()));
            }
        }
    }

    public int count() {
        return orbiters.size();
    }

    public Location popNearest(Location from) {
        if (orbiters.isEmpty()) return null;

        Orbiter pick = orbiters.get(0);
        if (from != null) {
            double best = Double.MAX_VALUE;
            for (Orbiter o : orbiters) {
                Location at = o.display().getLocation();
                if (!at.getWorld().equals(from.getWorld())) continue;
                double d = at.distanceSquared(from);
                if (d < best) {
                    best = d;
                    pick = o;
                }
            }
        }

        Location at = pick.display().getLocation();
        if (pick.display().isValid()) pick.display().remove();
        orbiters.remove(pick);
        return at;
    }

    public void remove() {
        for (Orbiter o : orbiters) {
            if (o.display().isValid()) o.display().remove();
        }
        orbiters.clear();
        ACTIVE.remove(this);
    }

    public static void removeAll() {
        for (MetalShieldVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    private static Location place(Location owner, double angleDegrees, int tick, int index) {
        double a = Math.toRadians(angleDegrees);
        double radialX = Math.cos(a);
        double radialZ = Math.sin(a);
        double bob = Math.sin(tick * 0.12 + index * 2.1) * BOB;

        Location loc = new Location(owner.getWorld(),
                owner.getX() + radialX * RADIUS,
                owner.getY() + HEIGHT + bob,
                owner.getZ() + radialZ * RADIUS);
        loc.setYaw((float) Math.toDegrees(Math.atan2(-radialX, radialZ)));
        loc.setPitch(0f);
        return loc;
    }
}
