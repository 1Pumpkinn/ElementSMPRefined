package hs.elementSMPRefined.API.element;

import org.bukkit.entity.Player;

public interface Element {
    ElementType getType();

    void applyUpsides(Player player, int upgradeLevel);

    boolean ability1(ElementContext context);

    boolean ability2(ElementContext context);

    void clearEffects(Player player);

    // New methods for improved API
    String getDisplayName();

    String getDescription();

    String getAbility1Name();

    String getAbility1Description();

    String getAbility2Name();

    String getAbility2Description();
}