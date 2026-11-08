package net.rose.elementSMPRefined.hud;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/**
 * Puts a line of text directly above an action bar, at the same size, by using the pack's
 * {@code elementsmp:raised} font (the vanilla ASCII glyphs drawn 10px higher plus invisible
 * spacer glyphs that move the cursor sideways).
 * <p>
 * The raised line is followed by a negative spacer that returns the cursor to the start, so the
 * action bar's total width is unchanged and the original bar stays exactly where it was. The
 * raised line is centred over the bar using the vanilla glyph widths below.
 */
public final class RaisedText {
    public static final Key FONT = Key.key("elementsmp", "raised");

    private static final char POSITIVE_BASE = '\ue100';
    private static final char NEGATIVE_BASE = '\ue110';
    private static final int SPACER_BITS = 9;

    /** Advance of each printable ASCII glyph (32 to 126) in the vanilla font, in pixels. */
    private static final int[] ASCII_ADVANCE = {4, 2, 4, 6, 6, 6, 6, 2, 4, 4, 4, 6, 2, 6, 2, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 2, 2, 5, 6, 5, 6, 7, 6, 6, 6, 6, 6, 6, 6, 6, 4, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 4, 6, 4, 6, 6, 3, 6, 6, 6, 6, 6, 5, 6, 6, 2, 6, 5, 3, 6, 6, 6, 6, 6, 6, 6, 4, 6, 6, 6, 6, 6, 6, 4, 2, 4, 7};

    private static final int FALLBACK_ADVANCE = 6;

    private RaisedText() {}

    public static int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            width += c >= 32 && c <= 126 ? ASCII_ADVANCE[c - 32] : FALLBACK_ADVANCE;
        }
        return width;
    }

    /**
     * Draws {@code raised} one line above {@code bar}, centred over it.
     *
     * @param raised coloured text made of printable ASCII only
     * @param raisedWidth width of {@code raised} in pixels, from {@link #width(String)}
     * @param bar the action bar line to sit under it
     * @param barWidth width of {@code bar} in pixels
     */
    public static Component above(
            Component raised, int raisedWidth, Component bar, int barWidth) {
        int lead = Math.max(0, (barWidth - raisedWidth) / 2);
        return textOfChildren(
                shift(lead),
                raised.font(FONT),
                shift(-(lead + raisedWidth)),
                bar);
    }

    private static Component shift(int pixels) {
        if (pixels == 0) return Component.empty();
        char base = pixels > 0 ? POSITIVE_BASE : NEGATIVE_BASE;
        int remaining = Math.abs(pixels);
        StringBuilder spacers = new StringBuilder();
        for (int bit = SPACER_BITS - 1; bit >= 0; bit--) {
            int step = 1 << bit;
            while (remaining >= step) {
                spacers.append((char) (base + bit));
                remaining -= step;
            }
        }
        return text(spacers.toString()).font(FONT);
    }
}
