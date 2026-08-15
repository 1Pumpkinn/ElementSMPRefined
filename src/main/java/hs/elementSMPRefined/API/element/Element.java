package hs.elementSMPRefined.API.element;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Contract every element (Air, Fire, a future custom element, etc.) must satisfy.
 * In almost all cases you should extend {@link BaseElement} instead of implementing
 * this directly - it already handles mana spending, upgrade-level gating, and the
 * ability name/description pass-through for you.
 */
public interface Element {
    ElementType getType();

    void applyUpsides(Player player, int upgradeLevel);

    boolean ability1(ElementContext context);

    boolean ability2(ElementContext context);

    void clearEffects(Player player);

    String getDisplayName();

    String getDescription();

    String getAbility1Name();

    String getAbility1Description();

    String getAbility2Name();

    String getAbility2Description();

    /**
     * Short, player-facing bullet points describing this element's passive perks.
     * Used by /elements. Defaults to none - override if the element has passives
     * worth advertising.
     */
    default List<String> getPassiveBenefits() {
        return List.of();
    }
}
