package hs.elementSMPRefined.API.element;

import org.bukkit.ChatColor;

import java.util.List;

/**
 * Immutable information about an element using records.
 * Used for /elements command and documentation.
 */
public record ElementInfo(
        String displayName,
        ChatColor color,
        String description,
        List<String> passiveBenefits,
        AbilityInfo ability1,
        AbilityInfo ability2
) {
    /**
     * Information about a single ability
     */
    public record AbilityInfo(
            String name,
            String description,
            int manaCost,
            int requiredUpgradeLevel
    ) {
        public AbilityInfo {
            if (manaCost < 0) throw new IllegalArgumentException("Mana cost cannot be negative");
            if (requiredUpgradeLevel < 1 || requiredUpgradeLevel > 2) {
                throw new IllegalArgumentException("Upgrade level must be 1 or 2");
            }
        }
    }

    public ElementInfo {
        // Defensive copies of mutable lists
        passiveBenefits = List.copyOf(passiveBenefits);
    }

    /**
     * Get formatted display name with color
     */
    public String getColoredName() {
        return color + displayName;
    }
}