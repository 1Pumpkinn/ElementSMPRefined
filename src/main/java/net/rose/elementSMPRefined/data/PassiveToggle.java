package net.rose.elementSMPRefined.data;

import net.rose.elementSMPRefined.core.API.element.ElementType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * A passive a player can switch on or off with {@code /toggle}. Every toggle is on by default and
 * only applies to the elements that actually have the passive.
 * <p>
 * To add another toggle: add a constant here, check {@link PlayerData#isToggleEnabled(PassiveToggle)}
 * inside that passive's listener, and it shows up in {@code /toggle} and its tab completion by itself.
 */
public enum PassiveToggle {

    /** Fire / Lava passive 1 - smelts raw ore drops. Stored under the old "autoSmelt" key so existing saves carry over. */
    FIRE_SMELT("firesmelt", "Fire Smelt", "autoSmelt", EnumSet.of(ElementType.FIRE, ElementType.LAVA)),

    /** Air / Storm passive 2 - the built-in Wind Burst smash attack. */
    WIND_BURST("windburst", "Wind Burst", "windBurst", EnumSet.of(ElementType.AIR, ElementType.STORM));

    private final String id;
    private final String displayName;
    private final String configKey;
    private final Set<ElementType> elements;

    PassiveToggle(String id, String displayName, String configKey, Set<ElementType> elements) {
        this.id = id;
        this.displayName = displayName;
        this.configKey = configKey;
        this.elements = elements;
    }

    /** The word typed after {@code /toggle}. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Key in players.yml. */
    public String configKey() {
        return configKey;
    }

    /** Whether a player with this element has the passive (null element = no). */
    public boolean appliesTo(ElementType element) {
        return element != null && elements.contains(element);
    }

    /** Human-readable list of the elements that have this passive, e.g. "Fire and Lava". */
    public String elementNames() {
        return String.join(" and ", elements.stream()
                .map(e -> e.name().charAt(0) + e.name().substring(1).toLowerCase(Locale.ROOT))
                .toList());
    }

    /** Parses what the player typed ("firesmelt", "fire_smelt", "WindBurst"...). */
    public static Optional<PassiveToggle> parse(String input) {
        if (input == null) return Optional.empty();
        String cleaned = input.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        for (PassiveToggle toggle : values()) {
            if (toggle.id.equals(cleaned)) return Optional.of(toggle);
        }
        return Optional.empty();
    }
}
