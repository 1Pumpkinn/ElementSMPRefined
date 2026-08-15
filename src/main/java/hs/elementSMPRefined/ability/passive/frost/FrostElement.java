package hs.elementSMPRefined.ability.passive.frost;

import hs.elementSMPRefined.API.element.BaseElement;
import hs.elementSMPRefined.API.element.ElementContext;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.API.ability.Ability;
import hs.elementSMPRefined.ability.main.frost.FrostCircleAbility;
import hs.elementSMPRefined.ability.main.frost.FrostPunchAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class FrostElement extends BaseElement {
    public static final String META_FROZEN_PUNCH_READY = "frost_frozen_punch_ready";

    private final Ability ability1;
    private final Ability ability2;

    public FrostElement(JavaPlugin plugin) {
        super(plugin);
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