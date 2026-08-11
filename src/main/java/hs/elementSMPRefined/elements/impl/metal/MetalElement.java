package hs.elementSMPRefined.elements.impl.metal;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.elements.BaseElement;
import hs.elementSMPRefined.elements.ElementContext;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.elements.abilities.Ability;
import hs.elementSMPRefined.elements.abilities.impl.metal.MetalChainAbility;
import hs.elementSMPRefined.elements.abilities.impl.metal.MetalDashAbility;
import hs.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class MetalElement extends BaseElement {
    private final ElementSMPRefined plugin;
    private final Ability ability1;
    private final MetalDashAbility ability2;

    public MetalElement(ElementSMPRefined plugin) {
        super(plugin);
        this.plugin = plugin;
        this.ability1 = new MetalChainAbility(plugin);
        this.ability2 = new MetalDashAbility(plugin);
    }

    public MetalDashAbility getMetalDashAbility() {
        return ability2;
    }

    @Override
    public ElementType getType() {
        return ElementType.METAL;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Upside 1: haste 1 permanently
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, Integer.MAX_VALUE, 0, true, false));

        // Upside 2: Arrow immunity (handled in listener)
        // No passive effect needed here
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
        EffectService.removeElementPotionEffect(player, PotionEffectType.HASTE);
        ability1.setActive(player, false);
        ability2.setActive(player, false);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.GRAY + "Metal";
    }

    @Override
    public String getDescription() {
        return "Masters of chains and iron. Metal users are swift and can dash through enemies.";
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