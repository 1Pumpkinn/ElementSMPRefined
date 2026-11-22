package net.rose.elementSMPRefined.util.scan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Open-space check for abilities that spawn something with a body (a core, a funnel, a ring of
 * spikes). Cast it before the ability commits to anything and return {@code false} from
 * {@code execute} on failure, so a cramped cast stays free (no cooldown).
 */
public final class SpaceCheck {

    private SpaceCheck() {}

    /**
     * True if every block in the box is passable: {@code radius} blocks each way horizontally of
     * {@code base}, from {@code base}'s Y up {@code height} blocks. Unloaded chunks count as no
     * room, and no chunk is loaded to find out.
     */
    public static boolean hasRoom(Location base, double radius, double height) {
        World world = base.getWorld();
        final double eps = 1.0E-6;
        int minX = (int) Math.floor(base.getX() - radius + eps);
        int maxX = (int) Math.floor(base.getX() + radius - eps);
        int minZ = (int) Math.floor(base.getZ() - radius + eps);
        int maxZ = (int) Math.floor(base.getZ() + radius - eps);
        int minY = Math.max(world.getMinHeight(), (int) Math.floor(base.getY() + eps));
        int maxY =
                Math.min(world.getMaxHeight() - 1, (int) Math.floor(base.getY() + height - eps));

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
                for (int y = minY; y <= maxY; y++) {
                    if (!world.getBlockAt(x, y, z).isPassable()) return false;
                }
            }
        }
        return true;
    }

    /** Tells the caster there is no room for {@code abilityName} and plays a short deny sound. */
    public static void deny(Player caster, String abilityName) {
        caster.sendMessage(
                Component.text("Not enough space for " + abilityName + ".", NamedTextColor.GRAY));
        caster.playSound(caster.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
    }
}
