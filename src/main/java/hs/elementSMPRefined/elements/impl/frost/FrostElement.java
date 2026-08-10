package hs.elementSMPRefined.elements.impl.frost;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.elements.BaseElement;
import hs.elementSMPRefined.elements.ElementContext;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.elements.abilities.Ability;
import hs.elementSMPRefined.elements.abilities.impl.frost.FrostCircleAbility;
import hs.elementSMPRefined.elements.abilities.impl.frost.FrostPunchAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class FrostElement extends BaseElement {
    public static final String META_FROZEN_PUNCH_READY = "frost_frozen_punch_ready";

    private final ElementSMPRefined plugin;
    private final Ability ability1;
    private final Ability ability2;

    public FrostElement(ElementSMPRefined plugin) {
        super(plugin);
        this.plugin = plugin;
        this.ability1 = new FrostCircleAbility(plugin);
        this.ability2 = new FrostPunchAbility(plugin);
    }

    @Override
    public ElementType getType() {
        return ElementType.FROST;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Upsides are handled by FrostPassiveListener
        // Upside 1: Speed 2 when wearing leather boots (always active)
        // Upside 2: Speed 3 on ice (requires upgrade level 2)
    }

    @Override
    protected boolean executeAbility1(ElementContext context) {
        return ability1.execute(context);
    }

    @Override
    protected boolean executeAbility2(ElementContext context) {
        return ability2.execute(context);
    }

    @Override
    public void clearEffects(Player player) {
        player.removeMetadata(META_FROZEN_PUNCH_READY, plugin);
        ability1.setActive(player, false);
        ability2.setActive(player, false);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.AQUA + "Frost";
    }

    @Override
    public String getDescription() {
        return "Frost Element.";
    }

    @Override
    public String getAbility1Name() {
        return ability1.getName();
    }

    @Override
    public String getAbility1Description() {
        return ability1.getDescription();
    }

    @Override
    public String getAbility2Name() {
        return ability2.getName();
    }

    @Override
    public String getAbility2Description() {
        return ability2.getDescription();
    }
}