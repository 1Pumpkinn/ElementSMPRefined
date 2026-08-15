package hs.elementSMPRefined.API;

import org.bukkit.ChatColor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Centralized registry for element information.
 * All element descriptions, abilities, and benefits in one place.
 */
public final class ElementInfoRegistry {
    private static final Map<ElementType, ElementInfo> INFO = new EnumMap<>(ElementType.class);

    static {
        // Air
        INFO.put(ElementType.AIR, new ElementInfo(
                "Air",
                ChatColor.WHITE,
                "Masters of the sky and wind",
                List.of(
                        "No fall damage",
                        "5% chance to apply Slow Falling to enemies (Upgrade II)"
                ),
                new ElementInfo.AbilityInfo("Air Blast", "Push enemies away with a gust of wind", 50, 1),
                new ElementInfo.AbilityInfo("Air Dash", "Dash forward swiftly, pushing enemies aside", 75, 1)
        ));

        // Fire
        INFO.put(ElementType.FIRE, new ElementInfo(
                "Fire",
                ChatColor.RED,
                "Wielders of flame and destruction",
                List.of(
                        "Immune to fire/lava damage",
                        "Apply Fire Aspect to all attacks (Upgrade II)"
                ),
                new ElementInfo.AbilityInfo("Fireball", "Launch an explosive fireball", 50, 1),
                new ElementInfo.AbilityInfo("Meteor Shower", "Rain down fireballs from the sky", 75, 2)
        ));

        // Water
        INFO.put(ElementType.WATER, new ElementInfo(
                "Water",
                ChatColor.BLUE,
                "Controllers of water and ocean currents",
                List.of(
                        "Infinite Water Breathing",
                        "Conduit Power permanently"
                ),
                new ElementInfo.AbilityInfo("Water Geyser", "Launch enemies upward with water pressure", 75, 1),
                new ElementInfo.AbilityInfo("Water Beam", "Fire a damaging water beam", 50, 2)
        ));

        // Earth
        INFO.put(ElementType.EARTH, new ElementInfo(
                "Earth",
                ChatColor.YELLOW,
                "Masters of stone and terrain",
                List.of(
                        "Hero of The Village",
                        "Double ore drops (Upgrade II)"
                ),
                new ElementInfo.AbilityInfo("Earth Tunnel", "Dig tunnels through stone and dirt", 50, 1),
                new ElementInfo.AbilityInfo("Mob Charm", "Charm mobs to follow you", 75, 1)
        ));

        // Life
        INFO.put(ElementType.LIFE, new ElementInfo(
                "Life",
                ChatColor.GREEN,
                "Healers with power over vitality",
                List.of(
                        "15 hearts total",
                        "Regeneration I",
                        "Crops grow faster (Upgrade II)"
                ),
                new ElementInfo.AbilityInfo("Regeneration Aura", "Heals you and allies around you", 50, 1),
                new ElementInfo.AbilityInfo("Healing Beam", "Heal an ally directly", 75, 1)
        ));

        // Death
        INFO.put(ElementType.DEATH, new ElementInfo(
                "Death",
                ChatColor.DARK_PURPLE,
                "Masters of decay and darkness",
                List.of(
                        "Permanent Night Vision",
                        "Raw/undead foods heal you"
                ),
                new ElementInfo.AbilityInfo("Summon Undead", "Summon undead ally for 30s", 50, 1),
                new ElementInfo.AbilityInfo("Wither Skull", "Fire an explosive wither skull", 75, 2)
        ));

        // Metal
        INFO.put(ElementType.METAL, new ElementInfo(
                "Metal",
                ChatColor.GRAY,
                "Warriors of steel and chains",
                List.of(
                        "Haste I",
                        "Arrow immunity (Upgrade II)"
                ),
                new ElementInfo.AbilityInfo("Chain Reel", "Pull an enemy toward you", 50, 1),
                new ElementInfo.AbilityInfo("Metal Dash", "Dash forward, damaging enemies", 75, 2)
        ));

        // Frost
        INFO.put(ElementType.FROST, new ElementInfo(
                "Frost",
                ChatColor.AQUA,
                "Controllers of ice and cold",
                List.of(
                        "Speed II on snow",
                        "Speed III on ice (Upgrade II)"
                ),
                new ElementInfo.AbilityInfo("Freezing Circle", "Slow enemies around you", 50, 1),
                new ElementInfo.AbilityInfo("Frozen Punch", "Freeze an enemy for 5s", 75, 2)
        ));
    }

    /**
     * Get element info
     */
    public static Optional<ElementInfo> get(ElementType type) {
        return Optional.ofNullable(INFO.get(type));
    }

    /**
     * Check if element has info registered
     */
    public static boolean hasInfo(ElementType type) {
        return INFO.containsKey(type);
    }

    private ElementInfoRegistry() {} // Prevent instantiation
}