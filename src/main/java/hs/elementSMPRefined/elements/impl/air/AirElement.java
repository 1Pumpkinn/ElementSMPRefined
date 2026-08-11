package hs.elementSMPRefined.elements.impl.air;

import hs.elementSMPRefined.elements.BaseElement;
import hs.elementSMPRefined.elements.ElementContext;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.elements.abilities.Ability;
import hs.elementSMPRefined.elements.abilities.impl.air.SlicingWindAbility;
import hs.elementSMPRefined.elements.abilities.impl.air.AirDashAbility;
import hs.elementSMPRefined.ElementSMPRefined;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class AirElement extends BaseElement {
    private final ElementSMPRefined plugin;
    private final Ability ability1;
    private final Ability ability2;

    public AirElement(ElementSMPRefined plugin) {
        super(plugin);
        this.plugin = plugin;
        this.ability1 = new SlicingWindAbility(plugin);
        this.ability2 = new AirDashAbility(plugin);
    }

    @Override
    public ElementType getType() {
        return ElementType.AIR;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passive 1: No fall damage (handled in FallDamageListener)
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
        return "Master the swift and agile power of air to control movement and push enemies.";
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