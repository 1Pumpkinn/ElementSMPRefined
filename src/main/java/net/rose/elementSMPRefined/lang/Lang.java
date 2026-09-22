package net.rose.elementSMPRefined.lang;

import org.bukkit.ChatColor;

/**
 * All player-facing message text lives here so wording/colors can be edited
 * in one place instead of hunting through every listener/ability/manager
 * that sends a message.
 * <p>
 * Fields are plain strings with ChatColor codes already baked in. Fields
 * with a {@code %s} (or more) are format strings - build the final message
 * with {@link #format(String, Object...)} before sending it.
 */
public final class Lang {

    private Lang() {}

    // --- Element item crafting: upgraders ---
    public static final String NO_ELEMENT_YET =
            ChatColor.RED + "You don't have an element yet.";

    public static final String UPGRADER_2_REQUIRES_UPGRADER_1 =
            ChatColor.RED + "You must craft and possess Upgrader I before crafting Upgrader II.";

    public static final String UPGRADE_ALREADY_OWNED =
            ChatColor.RED + "You already have this upgrade.";

    public static final String UNLOCKED_ABILITY_1 =
            ChatColor.GREEN + "Unlocked Ability 1 for %s";

    public static final String UNLOCKED_ABILITY_2 =
            ChatColor.GREEN + "Unlocked Ability 2 and Upside 2 for %s";

    // --- Element item crafting: basic element cores ---
    public static final String ITEM_ALREADY_CRAFTED =
            ChatColor.RED + "You can only craft this item once.";

    public static final String CRAFTED_ELEMENT_ITEM =
            ChatColor.GREEN + "Crafted element item for " + ChatColor.AQUA + "%s";

    public static final String UPGRADES_RESET =
            ChatColor.YELLOW + "All upgrades reset to None";

    /** Fills in a format string's {@code %s} placeholders. */
    public static String format(String template, Object... args) {
        return String.format(template, args);
    }
}