package hs.elementSMPRefined.util.visual;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;

/**
 * Bridges an element's legacy {@link ChatColor} - already embedded at the front
 * of every {@code Element#getDisplayName()} (e.g. {@code ChatColor.RED + "Fire"})
 * - to the Adventure {@link NamedTextColor} that titles/components need. Lets the
 * reroller roll animations and result titles show each element in its own real
 * color instead of one hardcoded color for every element.
 */
public final class ElementColours {
    private ElementColours() {}

    /**
     * Extracts the leading color code from legacy-formatted text such as an
     * element's display name (e.g. {@code "\u00A79Water"} -&gt; {@link NamedTextColor#AQUA})
     * and converts it to the equivalent Adventure color. Falls back to
     * {@link NamedTextColor#WHITE} if the text has no leading color code, or the
     * leading code is a formatting flag (bold, italic, etc.) rather than a color.
     */
    public static NamedTextColor fromLegacy(String legacyText) {
        if (legacyText != null && legacyText.length() >= 2 && legacyText.charAt(0) == ChatColor.COLOR_CHAR) {
            NamedTextColor named = toNamedTextColor(ChatColor.getByChar(legacyText.charAt(1)));
            if (named != null) {
                return named;
            }
        }
        return NamedTextColor.WHITE;
    }

    private static NamedTextColor toNamedTextColor(ChatColor color) {
        if (color == null) return null;
        return switch (color) {
            case BLACK -> NamedTextColor.BLACK;
            case DARK_BLUE -> NamedTextColor.DARK_BLUE;
            case DARK_GREEN -> NamedTextColor.DARK_GREEN;
            case DARK_AQUA -> NamedTextColor.DARK_AQUA;
            case DARK_RED -> NamedTextColor.DARK_RED;
            case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
            case GOLD -> NamedTextColor.GOLD;
            case GRAY -> NamedTextColor.GRAY;
            case DARK_GRAY -> NamedTextColor.DARK_GRAY;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case AQUA -> NamedTextColor.AQUA;
            case RED -> NamedTextColor.RED;
            case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE;
            case YELLOW -> NamedTextColor.YELLOW;
            case WHITE -> NamedTextColor.WHITE;
            default -> null; // formatting codes (BOLD, ITALIC, RESET, etc.) aren't colors
        };
    }
}