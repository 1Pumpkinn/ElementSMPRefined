package net.rose.elementSMPRefined.core.API.element;

import net.rose.elementSMPRefined.core.API.ability.Ability;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Contract every element (Air, Fire, a future custom element, etc.) must satisfy.
 * In almost all cases you should extend {@link BaseElement} instead of implementing
 * this directly - it already handles cooldown gating, upgrade-level gating, and the
 * ability name/description pass-through for you.
 */
public interface Element {
    ElementType getType();

    void applyUpsides(Player player, int upgradeLevel);

    boolean ability1(ElementContext context);

    boolean ability2(ElementContext context);

    void clearEffects(Player player);

    String getDisplayName();

    /**
     * The ability in {@code slot}.
     *
     * @param slot 1 or 2
     * @throws IllegalArgumentException for any other slot
     */
    Ability getAbility(int slot);

    /** Both abilities, slot 1 first. */
    default List<Ability> getAbilities() {
        return List.of(getAbility(1), getAbility(2));
    }

    String getAbility1Name();

    String getAbility1Description();

    String getAbility2Name();

    String getAbility2Description();

    String getAbility1Id();

    String getAbility2Id();

    int getAbility1CooldownSeconds();

    int getAbility2CooldownSeconds();

    default List<String> getPassiveBenefits() {
        return List.of();
    }
}
