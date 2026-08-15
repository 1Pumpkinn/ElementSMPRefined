package hs.elementSMPRefined.ability.passive.air;

import hs.elementSMPRefined.API.element.BaseElement;
import hs.elementSMPRefined.API.element.ElementContext;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.API.ability.Ability;
import hs.elementSMPRefined.ability.main.air.SlicingWindAbility;
import hs.elementSMPRefined.ability.main.air.AirDashAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AirElement extends BaseElement {
    private final Ability ability1;
    private final Ability ability2;
    private hs.elementSMPRefined.ability.passive.air.listeners.AirFallImpactListener fallImpactListener;

    public AirElement(JavaPlugin plugin) {
        super(plugin);
        this.ability1 = new SlicingWindAbility(plugin);
        this.ability2 = new AirDashAbility(plugin);
    }

    public void setFallImpactListener(hs.elementSMPRefined.ability.passive.air.listeners.AirFallImpactListener listener) {
        this.fallImpactListener = listener;
    }

    public hs.elementSMPRefined.ability.passive.air.listeners.AirFallImpactListener getFallImpactListener() {
        return fallImpactListener;
    }

    @Override
    public ElementType getType() {
        return ElementType.AIR;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passive 1: No fall damage (handled in AirFallImpactListener)
        // Passive 2: The further you fall, the further nearby entities get
        // knocked back on landing (handled in AirFallImpactListener)
        // No potion effects needed
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
        ability1.setActive(player, false);
        ability2.setActive(player, false);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.WHITE + "Air";
    }

    @Override
    public String getDescription() {
        return "Master the swift and agile power of air. Take no fall damage and knock back enemies with the force of your landing.";
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