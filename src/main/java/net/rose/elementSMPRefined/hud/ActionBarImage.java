package net.rose.elementSMPRefined.hud;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/**
Still working on this is gonna be for Ability Images that will display next to the Ability Name [Image] [Ability_Name] [Cooldown]
 */
public final class ActionBarImage {

    /** Font key defined in {@code assets/elementsmprefined/font/actionbar.json}. */
    public static final Key FONT = Key.key("elementsmprefined", "actionbar");

    /** Codepoint mapped to the bitmap provider for the main icon. */
    private static final String ICON_CHAR = "\uE000";

    // Negative/positive space glyphs, keyed by their pixel advance. Widths
    // must match the "advances" map in actionbar.json - add entries in both
    // places together if you need a size that isn't here.
    private static final String SPACE_MINUS_8 = "\uE010";
    private static final String SPACE_MINUS_4 = "\uE011";
    private static final String SPACE_MINUS_2 = "\uE012";
    private static final String SPACE_MINUS_1 = "\uE013";
    private static final String SPACE_PLUS_1 = "\uE014";
    private static final String SPACE_PLUS_2 = "\uE015";
    private static final String SPACE_PLUS_4 = "\uE016";
    private static final String SPACE_PLUS_8 = "\uE017";

    private ActionBarImage() {
    }

    /** The bare icon, in the custom font, with no extra spacing applied. */
    public static Component icon() {
        return Component.text(ICON_CHAR).font(FONT);
    }

    /**
     * A space glyph (from the resource pack's negative-space provider), in
     * the custom font, for nudging whatever follows it left/right by a fixed
     * number of pixels. {@code pixels} must be one of the widths defined in
     * actionbar.json (\u00b11, \u00b12, \u00b14, \u00b18) - chain several
     * calls to reach other offsets, e.g. -12px = shift(-8) + shift(-4).
     *
     * @throws IllegalArgumentException if there's no glyph for that width
     */
    public static Component shift(int pixels) {
        return switch (pixels) {
            case -8 -> Component.text(SPACE_MINUS_8).font(FONT);
            case -4 -> Component.text(SPACE_MINUS_4).font(FONT);
            case -2 -> Component.text(SPACE_MINUS_2).font(FONT);
            case -1 -> Component.text(SPACE_MINUS_1).font(FONT);
            case 1 -> Component.text(SPACE_PLUS_1).font(FONT);
            case 2 -> Component.text(SPACE_PLUS_2).font(FONT);
            case 4 -> Component.text(SPACE_PLUS_4).font(FONT);
            case 8 -> Component.text(SPACE_PLUS_8).font(FONT);
            default -> throw new IllegalArgumentException(
                    "No space glyph for " + pixels + "px - chain multiple shift() calls instead");
        };
    }

    /** Icon, a small gap, then normal (default-font) text - e.g. for prefixing a HUD line. */
    public static Component iconWithText(Component text) {
        return icon().append(Component.text(" ")).append(text);
    }
}